package com.parento.managed.network

import com.parento.managed.communication.DeviceTransport
import com.parento.managed.communication.TransportCommand
import com.parento.managed.communication.TransportSession
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicySynchronizerTest {
    private val policyId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun staleRemotePolicyCannotOverwriteNewerDesiredVersion() = kotlinx.coroutines.test.runTest {
        val repository = FakeStateRepository(
            LocalNetworkPolicyState(
                desiredPolicyId = policyId,
                desiredPolicyVersion = 8L,
                enforcementStatus = NetworkEnforcementStatus.APPLIED,
            ),
        )
        val enforcer = FakeEnforcer()
        val transport = FakeTransport(
            NetworkPolicy(
                policyId = policyId,
                version = 7L,
                status = NetworkPolicyStatus.ACTIVE,
                rules = listOf(NetworkPolicyRule("rule", "example.com", NetworkRuleAction.BLOCK, true)),
                receivedAtEpochMillis = 1L,
            ),
        )
        val result = NetworkPolicySynchronizer(
            transport,
            repository,
            enforcer,
            { "token" },
        ).synchronize(null, null)

        assertTrue(result is OperationResult.Success)
        assertEquals(NetworkEnforcementStatus.STALE, (result as OperationResult.Success).value.status)
        assertEquals(0, enforcer.applyCalls)
        assertEquals(8L, repository.state.desiredPolicyVersion)
    }

    @Test
    fun disabledPolicyRemovesOnlyThePreviouslyAppliedPolicy() = kotlinx.coroutines.test.runTest {
        val repository = FakeStateRepository(
            LocalNetworkPolicyState(
                desiredPolicyId = policyId,
                desiredPolicyVersion = 4L,
                appliedPolicyId = policyId,
                appliedPolicyVersion = 4L,
                enforcementStatus = NetworkEnforcementStatus.APPLIED,
            ),
        )
        val enforcer = FakeEnforcer(
            removeResult = NetworkPolicyEnforcementResult(NetworkEnforcementStatus.APPLIED, true),
        )
        val disabled = NetworkPolicy(
            policyId = policyId,
            version = 5L,
            status = NetworkPolicyStatus.DISABLED,
            rules = emptyList(),
            receivedAtEpochMillis = 1L,
        )
        val transport = FakeTransport(disabled)
        val result = NetworkPolicySynchronizer(
            transport,
            repository,
            enforcer,
            { "token" },
        ).synchronize(null, null)

        assertEquals(NetworkEnforcementStatus.APPLIED, (result as OperationResult.Success).value.status)
        assertEquals(policyId, enforcer.removedPolicyId)
        assertNull(repository.state.appliedPolicyId)
        assertEquals(5L, repository.state.desiredPolicyVersion)
    }

    @Test
    fun revokedStateIsClearedBeforeReenrollmentSync() = kotlinx.coroutines.test.runTest {
        val repository = FakeStateRepository(
            LocalNetworkPolicyState(
                desiredPolicyId = policyId,
                desiredPolicyVersion = 99L,
                appliedPolicyId = policyId,
                appliedPolicyVersion = 99L,
                enforcementStatus = NetworkEnforcementStatus.REVOKED,
            ),
        )
        val enforcer = FakeEnforcer()
        val transport = FakeTransport(
            NetworkPolicy(
                policyId = policyId,
                version = 1L,
                status = NetworkPolicyStatus.ACTIVE,
                rules = listOf(NetworkPolicyRule("rule", "example.com", NetworkRuleAction.BLOCK, true)),
                receivedAtEpochMillis = 1L,
            ),
        )
        val result = NetworkPolicySynchronizer(
            transport,
            repository,
            enforcer,
            { "token" },
        ).synchronize(null, null)

        assertTrue(result is OperationResult.Success)
        assertEquals(NetworkEnforcementStatus.UNSUPPORTED, (result as OperationResult.Success).value.status)
        assertEquals(1L, repository.state.desiredPolicyVersion)
        assertEquals(1, enforcer.applyCalls)
    }

    private class FakeStateRepository(initial: LocalNetworkPolicyState) : NetworkPolicyStateRepository {
        var state = initial
        override suspend fun read(): OperationResult<LocalNetworkPolicyState> = OperationResult.Success(state)
        override suspend fun write(state: LocalNetworkPolicyState): OperationResult<Unit> {
            this.state = state
            return OperationResult.Success(Unit)
        }
        override suspend fun markRevoked(): OperationResult<Unit> {
            state = state.copy(
                desiredPolicyId = null,
                desiredPolicyVersion = null,
                desiredPolicyJson = null,
                appliedPolicyId = null,
                appliedPolicyVersion = null,
                enforcementStatus = NetworkEnforcementStatus.REVOKED,
            )
            return OperationResult.Success(Unit)
        }
    }

    private class FakeEnforcer(
        private val removeResult: NetworkPolicyEnforcementResult =
            NetworkPolicyEnforcementResult(NetworkEnforcementStatus.UNSUPPORTED, false, "UNSUPPORTED"),
    ) : NetworkPolicyEnforcer {
        var applyCalls = 0
        var removedPolicyId: String? = null
        override fun capability() = NetworkPolicyCapability(false, NetworkCapabilityMode.UNSUPPORTED, 1, 1L)
        override fun apply(policy: NetworkPolicy): NetworkPolicyEnforcementResult {
            applyCalls++
            return NetworkPolicyEnforcementResult(NetworkEnforcementStatus.UNSUPPORTED, false, "UNSUPPORTED")
        }
        override fun remove(policyId: String?): NetworkPolicyEnforcementResult {
            removedPolicyId = policyId
            return removeResult
        }
    }

    private class FakeTransport(
        private val policy: NetworkPolicy?,
    ) : DeviceTransport {
        override suspend fun connect(deviceCredential: String) = OperationResult.Failure(ManagedError.NETWORK_FAILURE)
        override suspend fun heartbeat(sessionToken: String) = OperationResult.Success(1L)
        override suspend fun disconnect(sessionToken: String) = OperationResult.Success(Unit)
        override suspend fun acknowledge(sessionToken: String, commandId: String) = OperationResult.Success(Unit)
        override suspend fun start(sessionToken: String, commandId: String) = OperationResult.Success(Unit)
        override suspend fun result(sessionToken: String, commandId: String, status: String, resultCode: String?, errorCategory: String?, resultMetadata: String?) = OperationResult.Success(Unit)
        override suspend fun reportLocation(sessionToken: String, reportId: String, availability: String, latitude: Double?, longitude: Double?, accuracyMeters: Double?, observedAt: String) = OperationResult.Success(Unit)
        override suspend fun receiveNextCommand(sessionToken: String): OperationResult<TransportCommand?> = OperationResult.Success(null)
        override suspend fun uploadApplicationInventory(sessionToken: String, payloadJson: String) = OperationResult.Success(Unit)
        override suspend fun reportApplicationEnforcement(sessionToken: String, payloadJson: String) = OperationResult.Success(Unit)
        override suspend fun fetchNetworkPolicy(sessionToken: String): OperationResult<NetworkPolicy?> = OperationResult.Success(policy)
        override suspend fun reportNetworkPolicyStatus(sessionToken: String, policyId: String?, policyVersion: Long?, status: NetworkEnforcementStatus, reportedAtEpochMillis: Long, errorCode: String?) = OperationResult.Success(Unit)
        override suspend fun reportNetworkPolicyCapability(sessionToken: String, capability: NetworkPolicyCapability) = OperationResult.Success(Unit)
    }
}
