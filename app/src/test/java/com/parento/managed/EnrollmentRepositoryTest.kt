package com.parento.managed

import com.parento.managed.communication.DeviceCredentialStore
import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.CapabilityState
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.enrollment.EnrollmentApiClient
import com.parento.managed.enrollment.EnrollmentAuthorization
import com.parento.managed.enrollment.EnrollmentRepository
import com.parento.managed.enrollment.EnrollmentResult
import com.parento.managed.enrollment.EnrollmentStore
import com.parento.managed.enrollment.PendingEnrollment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentRepositoryTest {
    @Test
    fun beginPersistsPendingAuthorizationAndMovesToEnrolling() = runBlocking {
        val state = FakeStateRepository()
        val store = FakeStore()
        val repository = EnrollmentRepository(FakeApi(), state, store, FakeCredentialStore())

        val result = repository.begin("00000000-0000-0000-0000-000000000001", "a".repeat(43), System.currentTimeMillis() + 60_000)

        assertTrue(result is OperationResult.Success)
        assertEquals(EnrollmentState.ENROLLING, state.enrollmentState)
        assertNotNull(store.pending)
    }

    @Test
    fun successfulConsumptionPersistsManagedDeviceAndClearsSecret() = runBlocking {
        val state = FakeStateRepository()
        val store = FakeStore(
            PendingEnrollment("00000000-0000-0000-0000-000000000001", "a".repeat(43), System.currentTimeMillis() + 60_000),
        )
        state.enrollmentState = EnrollmentState.ENROLLING
        val repository = EnrollmentRepository(FakeApi(), state, store, FakeCredentialStore())

        val result = repository.enroll("Child Device")

        assertTrue(result is OperationResult.Success)
        assertEquals("managed-1", state.managedDeviceId)
        assertEquals(EnrollmentState.ENROLLED, state.enrollmentState)
        assertNull(store.pending)
    }

    @Test
    fun duplicateCallsAreSerializedAndDoNotRunTwoOperations() = runBlocking {
        val state = FakeStateRepository()
        val store = FakeStore(
            PendingEnrollment("00000000-0000-0000-0000-000000000001", "a".repeat(43), System.currentTimeMillis() + 60_000),
        )
        state.enrollmentState = EnrollmentState.ENROLLING
        val api = CountingApi()
        val repository = EnrollmentRepository(api, state, store, FakeCredentialStore())

        val results = listOf(
            async { repository.enroll("Child Device") },
            async { repository.enroll("Child Device") },
        ).awaitAll()

        assertEquals(1, api.calls)
        assertEquals(1, results.count { it is OperationResult.Success })
        assertEquals(1, results.count { it is OperationResult.Failure })
    }

    private class FakeApi : EnrollmentApiClient {
        override suspend fun consume(
            authorization: EnrollmentAuthorization,
            localInstallationIdentity: String,
            name: String,
        ): OperationResult<EnrollmentResult> =
            OperationResult.Success(EnrollmentResult("managed-1", authorization.enrollmentId, System.currentTimeMillis() + 60_000, "b".repeat(43)))
    }

    private class CountingApi : EnrollmentApiClient {
        var calls = 0
        override suspend fun consume(
            authorization: EnrollmentAuthorization,
            localInstallationIdentity: String,
            name: String,
        ): OperationResult<EnrollmentResult> {
            calls++
            return if (calls == 1) {
                OperationResult.Success(EnrollmentResult("managed-1", authorization.enrollmentId, System.currentTimeMillis() + 60_000, "b".repeat(43)))
            } else {
                OperationResult.Failure(ManagedError.INVALID_STATE)
            }
        }
    }

    private class FakeStore(initial: PendingEnrollment? = null) : EnrollmentStore {
        var pending: PendingEnrollment? = initial
        override fun read() = OperationResult.Success(pending)
        override fun save(enrollment: PendingEnrollment): OperationResult<Unit> {
            pending = enrollment
            return OperationResult.Success(Unit)
        }
        override fun clear(): OperationResult<Unit> {
            pending = null
            return OperationResult.Success(Unit)
        }
    }

    private class FakeStateRepository : LocalStateRepository {
        var enrollmentState = EnrollmentState.UNENROLLED
        var managedDeviceId: String? = null
        private var identity = LocalDeviceIdentity("550e8400-e29b-41d4-a716-446655440000", 1L)

        override suspend fun read() = OperationResult.Success(
            LocalApplicationState(
                installationId = identity.installationId,
                identityCreatedAtEpochMillis = identity.createdAtEpochMillis,
                managedDeviceId = managedDeviceId,
                enrollmentState = enrollmentState,
            ),
        )
        override suspend fun write(state: LocalApplicationState) = OperationResult.Success(Unit)
        override suspend fun clear() = OperationResult.Success(Unit)
        override fun observe(): Flow<OperationResult<LocalApplicationState?>> = emptyFlow()
        override suspend fun initializeLocalState() = read() as OperationResult.Success<LocalApplicationState>
        override suspend fun getOrCreateIdentity() = OperationResult.Success(identity)
        override suspend fun getEnrollmentState() = OperationResult.Success(enrollmentState)
        override suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit> {
            enrollmentState = state
            return OperationResult.Success(Unit)
        }
        override suspend fun getConnectionState() = OperationResult.Success(ConnectionState.UNKNOWN)
        override suspend fun updateConnectionState(state: ConnectionState) = OperationResult.Success(Unit)
        override suspend fun getManagedDeviceId() = OperationResult.Success(managedDeviceId)
        override suspend fun completeEnrollment(id: String): OperationResult<Unit> {
            managedDeviceId = id
            enrollmentState = EnrollmentState.ENROLLED
            return OperationResult.Success(Unit)
        }
    }
}


private class FakeCredentialStore : DeviceCredentialStore {
    override fun read() = OperationResult.Success<String?>(null)
    override fun save(credential: String): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun clear(): OperationResult<Unit> = OperationResult.Success(Unit)
}
