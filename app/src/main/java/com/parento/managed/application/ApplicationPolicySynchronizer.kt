package com.parento.managed.application

import com.parento.managed.communication.ApplicationPolicyPayload
import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class ApplicationPolicySynchronizer(
    private val localStateRepository: LocalStateRepository,
    private val sessionManager: DeviceCommunicationSessionManager,
    private val enforcementEngine: ApplicationEnforcementEngine,
    private val managementModeProvider: () -> ManagementMode,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun synchronize(
        commandPolicyId: String?,
        commandPolicyVersion: Int?,
    ): OperationResult<ApplicationEnforcementOutcome> {
        val state = when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        } ?: return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)

        if (state.enrollmentState != EnrollmentState.ENROLLED ||
            state.managedDeviceId.isNullOrBlank()
        ) {
            return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
        }

        if (managementModeProvider() != ManagementMode.DEVICE_OWNER &&
            managementModeProvider() != ManagementMode.PROFILE_OWNER
        ) {
            val outcome = ApplicationEnforcementOutcome(
                commandPolicyId,
                commandPolicyVersion,
                0,
                0,
                0,
                1,
                "UNSUPPORTED_MANAGEMENT_MODE",
            )
            localStateRepository.updateApplicationPolicySyncStatus(ApplicationPolicySyncStatus.FAILED)
            localStateRepository.updateApplicationEnforcementStatus(ApplicationEnforcementStatus.FAILED)
            report(outcome)
            return OperationResult.Success(outcome)
        }

        if (sessionManager.state.value != ConnectionState.CONNECTED) {
            return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        }

        val token = sessionManager.currentSessionToken()
            ?: return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)

        val response = when (val result = sessionManager.transportBoundary().getApplicationPolicy(token)) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        }

        if (response.managedDeviceId != state.managedDeviceId) {
            return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
        }

        val policy = response.policy
        if (commandPolicyId != null || commandPolicyVersion != null) {
            if (commandPolicyId == null || commandPolicyVersion == null) {
                return OperationResult.Failure(ManagedError.INVALID_STATE)
            }
            if (policy == null ||
                policy.policyId != commandPolicyId ||
                policy.policyVersion != commandPolicyVersion
            ) {
                localStateRepository.updateApplicationPolicySyncStatus(ApplicationPolicySyncStatus.STALE)
                localStateRepository.updateApplicationEnforcementStatus(ApplicationEnforcementStatus.STALE)
                return OperationResult.Success(
                    ApplicationEnforcementOutcome(
                        commandPolicyId,
                        commandPolicyVersion,
                        0,
                        0,
                        0,
                        0,
                        "STALE_POLICY",
                    ),
                )
            }
        }

        val rules = policy?.rules.orEmpty().map {
            ApplicationPolicyRule(
                packageName = it.packageName,
                action = when (it.action) {
                    "ALLOW" -> ApplicationDesiredAction.ALLOW
                    "BLOCK" -> ApplicationDesiredAction.BLOCK
                    else -> return OperationResult.Failure(ManagedError.INVALID_STATE)
                },
            )
        }

        if (policy == null) {
            localStateRepository.clearApplicationPolicy()
        } else {
            val rulesJson = rulesJson(rules)
            val stored = localStateRepository.updateApplicationPolicyReference(
                policy.policyId,
                policy.policyVersion,
                rulesJson,
                ApplicationPolicySyncStatus.PENDING,
            )
            if (stored is OperationResult.Failure) return stored
        }

        val current = when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        } ?: return OperationResult.Failure(ManagedError.STORAGE_FAILURE)

        val previouslyManaged = parsePackageSet(current.enforcedBlockedPackagesJson)
        val outcome = enforcementEngine.enforce(
            policyId = policy?.policyId,
            policyVersion = policy?.policyVersion,
            rules = rules,
            previouslyManagedBlockedPackages = previouslyManaged,
        )

        val syncStatus = when (outcome.status) {
            ApplicationEnforcementStatus.APPLIED -> ApplicationPolicySyncStatus.APPLIED
            ApplicationEnforcementStatus.PARTIALLY_APPLIED -> ApplicationPolicySyncStatus.FAILED
            ApplicationEnforcementStatus.FAILED -> ApplicationPolicySyncStatus.FAILED
            else -> ApplicationPolicySyncStatus.FAILED
        }

        val stored = localStateRepository.recordApplicationPolicyEnforcement(
            policyId = if (outcome.status == ApplicationEnforcementStatus.APPLIED) policy?.policyId else current.appliedApplicationPolicyId,
            policyVersion = if (outcome.status == ApplicationEnforcementStatus.APPLIED) policy?.policyVersion else current.appliedApplicationPolicyVersion,
            syncStatus = syncStatus,
            enforcementStatus = outcome.status,
            enforcedBlockedPackagesJson = JSONArray(outcome.resultingManagedBlockedPackages.sorted()).toString(),
        )
        if (stored is OperationResult.Failure) return stored

        report(outcome)
        return OperationResult.Success(outcome)
    }

    suspend fun reconcileStoredPolicy(): OperationResult<ApplicationEnforcementOutcome> {
        val state = when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        } ?: return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)

        val policyId = state.desiredApplicationPolicyId
        val policyVersion = state.desiredApplicationPolicyVersion
        val rules = parseRules(state.desiredApplicationPolicyRulesJson)
        val outcome = enforcementEngine.enforce(
            policyId = policyId,
            policyVersion = policyVersion,
            rules = rules,
            previouslyManagedBlockedPackages = parsePackageSet(state.enforcedBlockedPackagesJson),
        )
        if (outcome.status == ApplicationEnforcementStatus.APPLIED) {
            localStateRepository.recordApplicationPolicyEnforcement(
                policyId = policyId,
                policyVersion = policyVersion,
                syncStatus = ApplicationPolicySyncStatus.APPLIED,
                enforcementStatus = outcome.status,
                enforcedBlockedPackagesJson = JSONArray(outcome.resultingManagedBlockedPackages.sorted()).toString(),
            )
        } else {
            localStateRepository.updateApplicationEnforcementStatus(outcome.status)
        }
        if (sessionManager.state.value == ConnectionState.CONNECTED) report(outcome)
        return OperationResult.Success(outcome)
    }

    private suspend fun report(outcome: ApplicationEnforcementOutcome) {
        val token = sessionManager.currentSessionToken() ?: return
        val payload = JSONObject()
            .put("policyId", outcome.policyId)
            .put("policyVersion", outcome.policyVersion)
            .put("status", outcome.status.name)
            .put("reportedAt", Instant.ofEpochMilli(nowEpochMillis()).toString())
            .put("errorCode", outcome.errorCode)
        sessionManager.transportBoundary().reportApplicationEnforcement(token, payload.toString())
    }

    private fun rulesJson(rules: List<ApplicationPolicyRule>): String =
        JSONArray().apply {
            rules.sortedBy { it.packageName }.forEach {
                put(JSONObject().put("packageName", it.packageName).put("action", it.action.name))
            }
        }.toString()

    private fun parseRules(json: String?): List<ApplicationPolicyRule> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ApplicationPolicyRule(
                        packageName = item.getString("packageName"),
                        action = ApplicationDesiredAction.valueOf(item.getString("action")),
                    ),
                )
            }
        }
    }

    private fun parsePackageSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val array = JSONArray(json)
        return buildSet {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }
}
