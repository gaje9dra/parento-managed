package com.parento.managed.network

import com.parento.managed.communication.DeviceTransport
import com.parento.managed.domain.OperationResult
import java.util.UUID

class NetworkPolicySynchronizer(
    private val transport: DeviceTransport,
    private val stateRepository: NetworkPolicyStateRepository,
    private val enforcer: NetworkPolicyEnforcer,
    private val sessionTokenProvider: () -> String,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun synchronize(expectedPolicyId: String?, expectedPolicyVersion: Long?): OperationResult<NetworkPolicyEnforcementResult> {
        val current = when (val result = stateRepository.read()) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        }

        if (expectedPolicyId != null && !isUuid(expectedPolicyId)) {
            return OperationResult.Failure(com.parento.managed.domain.ManagedError.POLICY_FAILURE)
        }
        if (expectedPolicyVersion != null && expectedPolicyVersion < 1L) {
            return OperationResult.Failure(com.parento.managed.domain.ManagedError.POLICY_FAILURE)
        }

        val capability = enforcer.capability()
        stateRepository.write(
            current.copy(
                capabilityMode = capability.mode,
                capabilitySupported = capability.supported,
                capabilityVersion = capability.capabilityVersion,
            ),
        )
        val capabilityReport = transport.reportNetworkPolicyCapability(
            sessionToken = sessionTokenProvider(),
            capability = capability,
        )
        if (capabilityReport is OperationResult.Failure) {
            // Capability reporting is retried by the existing authenticated command/session path.
        }

        val remote = transport.fetchNetworkPolicy(currentSessionTokenPlaceholder)
        val policy = when (remote) {
            is OperationResult.Failure -> return remote
            is OperationResult.Success -> remote.value
        }

        if (policy != null && (!NetworkPolicyDomain.validateRules(policy.rules) || policy.version < 1L)) {
            return finish(
                current.copy(
                    enforcementStatus = NetworkEnforcementStatus.FAILED,
                    pendingSynchronization = false,
                    lastSynchronizedAtEpochMillis = nowEpochMillis(),
                    lastErrorCode = "INVALID_POLICY",
                ),
                NetworkPolicyEnforcementResult(NetworkEnforcementStatus.FAILED, false, "INVALID_POLICY"),
            )
        }

        if (expectedPolicyId != null &&
            (policy == null || policy.policyId != expectedPolicyId || policy.version != expectedPolicyVersion)
        ) {
            return finish(
                current.copy(
                    enforcementStatus = NetworkEnforcementStatus.STALE,
                    pendingSynchronization = false,
                    lastSynchronizedAtEpochMillis = nowEpochMillis(),
                    lastErrorCode = "STALE_POLICY_COMMAND",
                ),
                NetworkPolicyEnforcementResult(NetworkEnforcementStatus.STALE, false, "STALE_POLICY_COMMAND"),
            )
        }

        if (policy == null) {
            val result = enforcer.remove(current.appliedPolicyId)
            return finish(
                current.copy(
                    desiredPolicyId = null,
                    desiredPolicyVersion = null,
                    desiredPolicyJson = null,
                    appliedPolicyId = if (result.applied) null else current.appliedPolicyId,
                    appliedPolicyVersion = if (result.applied) null else current.appliedPolicyVersion,
                    enforcementStatus = result.status,
                    pendingSynchronization = false,
                    lastSynchronizedAtEpochMillis = nowEpochMillis(),
                    lastErrorCode = result.errorCode,
                ),
                result,
            )
        }

        if (current.desiredPolicyVersion != null && policy.version < current.desiredPolicyVersion) {
            return finish(
                current.copy(
                    enforcementStatus = NetworkEnforcementStatus.STALE,
                    pendingSynchronization = false,
                    lastSynchronizedAtEpochMillis = nowEpochMillis(),
                    lastErrorCode = "STALE_POLICY",
                ),
                NetworkPolicyEnforcementResult(NetworkEnforcementStatus.STALE, false, "STALE_POLICY"),
            )
        }

        val encoded = NetworkPolicyJson.encode(policy)
        val desired = current.copy(
            desiredPolicyId = policy.policyId,
            desiredPolicyVersion = policy.version,
            desiredPolicyJson = encoded,
            pendingSynchronization = true,
            lastErrorCode = null,
        )
        stateRepository.write(desired)

        if (current.appliedPolicyId == policy.policyId &&
            current.appliedPolicyVersion == policy.version &&
            current.enforcementStatus == NetworkEnforcementStatus.APPLIED
        ) {
            return finish(
                desired.copy(
                    enforcementStatus = NetworkEnforcementStatus.APPLIED,
                    pendingSynchronization = false,
                    lastSynchronizedAtEpochMillis = nowEpochMillis(),
                ),
                NetworkPolicyEnforcementResult(NetworkEnforcementStatus.APPLIED, true),
            )
        }

        val result = enforcer.apply(policy)
        return finish(
            desired.copy(
                appliedPolicyId = if (result.applied) policy.policyId else current.appliedPolicyId,
                appliedPolicyVersion = if (result.applied) policy.version else current.appliedPolicyVersion,
                enforcementStatus = result.status,
                pendingSynchronization = false,
                lastSynchronizedAtEpochMillis = nowEpochMillis(),
                lastErrorCode = result.errorCode,
            ),
            result,
        )
    }

    private suspend fun finish(
        state: LocalNetworkPolicyState,
        result: NetworkPolicyEnforcementResult,
    ): OperationResult<NetworkPolicyEnforcementResult> {
        val written = stateRepository.write(state)
        if (written is OperationResult.Failure) return written
        transport.reportNetworkPolicyStatus(
            sessionToken = sessionTokenProvider(),
            policyId = state.desiredPolicyId,
            policyVersion = state.desiredPolicyVersion,
            status = result.status,
            reportedAtEpochMillis = nowEpochMillis(),
            errorCode = result.errorCode,
        )
        return OperationResult.Success(result)
    }


    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess
}
