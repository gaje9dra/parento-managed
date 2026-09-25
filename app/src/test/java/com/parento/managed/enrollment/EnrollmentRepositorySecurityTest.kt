package com.parento.managed.enrollment

import com.parento.managed.communication.DeviceCredentialStore
import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.domain.canTransitionTo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentRepositorySecurityTest {
    private val enrollmentId = UUID.randomUUID().toString()
    private val secret = "A".repeat(43)

    @Test
    fun networkFailureClearsOneTimeAuthorizationAndPreventsReplay() = runBlocking {
        val local = FakeLocalStateRepository()
        val store = FakeEnrollmentStore()
        val api = FakeEnrollmentApi(OperationResult.Failure(ManagedError.NETWORK_FAILURE))
        val repository = EnrollmentRepository(api, local, store)

        assertTrue(repository.begin(enrollmentId, secret, System.currentTimeMillis() + 60_000) is OperationResult.Success)
        val result = repository.enroll("Child phone")

        assertEquals(OperationResult.Failure(ManagedError.NETWORK_FAILURE), result)
        assertNull(store.pending)
        assertEquals(EnrollmentState.ERROR, local.state)
    }

    @Test
    fun restoredPendingAuthorizationNeverReplaysAfterErrorState() = runBlocking {
        val local = FakeLocalStateRepository(state = EnrollmentState.ERROR)
        val store = FakeEnrollmentStore(PendingEnrollment(enrollmentId, secret, System.currentTimeMillis() + 60_000))
        val repository = EnrollmentRepository(FakeEnrollmentApi(), local, store, FakeCredentialStore())

        val result = repository.restorePending()

        assertEquals(OperationResult.Success(null), result)
        assertNull(store.pending)
        assertEquals(EnrollmentState.UNENROLLED, local.state)
    }

    @Test
    fun mismatchedSuccessfulResponseFailsClosedAndClearsAuthorization() = runBlocking {
        val local = FakeLocalStateRepository()
        val store = FakeEnrollmentStore()
        val api = FakeEnrollmentApi(OperationResult.Success(EnrollmentResult(UUID.randomUUID().toString(), UUID.randomUUID().toString(), System.currentTimeMillis() + 60_000, "B".repeat(43))))
        val repository = EnrollmentRepository(api, local, store)

        repository.begin(enrollmentId, secret, System.currentTimeMillis() + 60_000)
        val result = repository.enroll("Child phone")

        assertEquals(OperationResult.Failure(ManagedError.UNKNOWN), result)
        assertNull(store.pending)
        assertEquals(EnrollmentState.ERROR, local.state)
    }
}

private class FakeEnrollmentApi(private val result: OperationResult<EnrollmentResult> = OperationResult.Failure(ManagedError.NETWORK_FAILURE)) : EnrollmentApiClient {
    override suspend fun consume(authorization: EnrollmentAuthorization, localInstallationIdentity: String, name: String): OperationResult<EnrollmentResult> = result
}

private class FakeEnrollmentStore(var pending: PendingEnrollment? = null) : EnrollmentStore {
    override fun read(): OperationResult<PendingEnrollment?> = OperationResult.Success(pending)
    override fun save(enrollment: PendingEnrollment): OperationResult<Unit> { pending = enrollment; return OperationResult.Success(Unit) }
    override fun clear(): OperationResult<Unit> { pending = null; return OperationResult.Success(Unit) }
}

private class FakeLocalStateRepository(var state: EnrollmentState = EnrollmentState.UNENROLLED) : LocalStateRepository {
    private val identity = LocalDeviceIdentity(UUID.randomUUID().toString(), System.currentTimeMillis())
    override suspend fun read(): OperationResult<LocalApplicationState?> = OperationResult.Success(LocalApplicationState(installationId = identity.installationId, identityCreatedAtEpochMillis = identity.createdAtEpochMillis, enrollmentState = state))
    override suspend fun write(state: LocalApplicationState): OperationResult<Unit> { this.state = state.enrollmentState; return OperationResult.Success(Unit) }
    override suspend fun clear(): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun observe(): Flow<OperationResult<LocalApplicationState?>> = flowOf(OperationResult.Success<LocalApplicationState?>(null))
    override suspend fun initializeLocalState(): OperationResult<LocalApplicationState> = OperationResult.Success(LocalApplicationState())
    override suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity> = OperationResult.Success(identity)
    override suspend fun getEnrollmentState(): OperationResult<EnrollmentState> = OperationResult.Success(state)
    override suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit> {
        if (!this.state.canTransitionTo(state)) return OperationResult.Failure(ManagedError.INVALID_STATE)
        this.state = state
        return OperationResult.Success(Unit)
    }
    override suspend fun getConnectionState(): OperationResult<ConnectionState> = OperationResult.Success(ConnectionState.UNKNOWN)
    override suspend fun updateConnectionState(state: ConnectionState): OperationResult<Unit> = OperationResult.Success(Unit)
    override suspend fun getManagedDeviceId(): OperationResult<String?> = OperationResult.Success(null)
    override suspend fun completeEnrollment(managedDeviceId: String): OperationResult<Unit> {
        if (state != EnrollmentState.ENROLLING) return OperationResult.Failure(ManagedError.INVALID_STATE)
        state = EnrollmentState.ENROLLED
        return OperationResult.Success(Unit)
    }
}


private class FakeCredentialStore : DeviceCredentialStore {
    override fun read() = OperationResult.Success<String?>(null)
    override fun save(credential: String): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun clear(): OperationResult<Unit> = OperationResult.Success(Unit)
}
