package com.parento.managed

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.LocalStateService
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStateServiceTest {
    @Test
    fun initialize_isIdempotentAndUsesUnknownRuntimeConnection() = runBlocking {
        val repository = FakeLocalStateRepository()
        val service = LocalStateService(repository)
        val first = service.initialize()
        val second = service.initialize()
        assertTrue(first is OperationResult.Success)
        assertTrue(second is OperationResult.Success)
        val firstState = (first as OperationResult.Success).value
        val secondState = (second as OperationResult.Success).value
        assertEquals(firstState.installationId, secondState.installationId)
        assertEquals(ConnectionState.UNKNOWN, secondState.connectionState)
    }

    @Test
    fun missingIdentity_isRecoveredWithoutAuthentication() = runBlocking {
        val service = LocalStateService(FakeLocalStateRepository(state = LocalApplicationState(initialized = true)))
        val result = service.initialize()
        assertTrue(result is OperationResult.Success)
        assertNotNull((result as OperationResult.Success).value.installationId)
        assertEquals(EnrollmentState.UNENROLLED, result.value.enrollmentState)
    }

    @Test
    fun stalePersistedConnectedState_doesNotBecomeLiveConnection() = runBlocking {
        val repository = FakeLocalStateRepository(state = LocalApplicationState(
            initialized = true,
            installationId = "550e8400-e29b-41d4-a716-446655440000",
            identityCreatedAtEpochMillis = 1L,
            connectionState = ConnectionState.CONNECTED,
        ))
        val service = LocalStateService(repository)
        val result = service.initialize()
        assertEquals(ConnectionState.UNKNOWN, (result as OperationResult.Success).value.connectionState)
        assertEquals(ConnectionState.UNKNOWN, (service.getConnectionState() as OperationResult.Success).value)
    }

    @Test
    fun runtimeConnectionTransitions_areValidatedWithoutPersistence() = runBlocking {
        val repository = FakeLocalStateRepository(
            state = LocalApplicationState(
                initialized = true,
                installationId = "stable",
                identityCreatedAtEpochMillis = 1L,
            ),
        )
        val service = LocalStateService(repository)
        assertEquals(
            OperationResult.Failure(ManagedError.INVALID_STATE),
            service.setConnectionState(ConnectionState.CONNECTED),
        )
        assertEquals(
            OperationResult.Success(Unit),
            service.setConnectionState(ConnectionState.DISCONNECTED),
        )
        assertEquals(
            OperationResult.Success(Unit),
            service.setConnectionState(ConnectionState.CONNECTING),
        )
        assertEquals(
            OperationResult.Success(Unit),
            service.setConnectionState(ConnectionState.AUTHENTICATING),
        )
        assertEquals(
            OperationResult.Success(Unit),
            service.setConnectionState(ConnectionState.CONNECTED),
        )
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun malformedPersistedIdentity_isRejectedAsStorageFailure() = runBlocking {
        val service = LocalStateService(
            FakeLocalStateRepository(
                state = LocalApplicationState(
                    initialized = true,
                    installationId = "not-a-uuid",
                    identityCreatedAtEpochMillis = 1L,
                ),
            ),
        )

        assertEquals(
            OperationResult.Failure(ManagedError.STORAGE_FAILURE),
            service.initialize(),
        )
    }

    @Test
    fun invalidPersistedIdentityTimestamp_isRejectedAsStorageFailure() = runBlocking {
        val service = LocalStateService(
            FakeLocalStateRepository(
                state = LocalApplicationState(
                    initialized = true,
                    installationId = "550e8400-e29b-41d4-a716-446655440000",
                    identityCreatedAtEpochMillis = 0L,
                ),
            ),
        )

        assertEquals(
            OperationResult.Failure(ManagedError.STORAGE_FAILURE),
            service.initialize(),
        )
    }

    @Test
    fun repositoryFailure_isReturnedWithoutInventingEnrollment() = runBlocking {
        val service = LocalStateService(FakeLocalStateRepository(readResult = OperationResult.Failure(ManagedError.STORAGE_FAILURE)))
        assertEquals(OperationResult.Failure(ManagedError.STORAGE_FAILURE), service.initialize())
    }

    private class FakeLocalStateRepository(
        var state: LocalApplicationState? = null,
        private val readResult: OperationResult<LocalApplicationState?>? = null,
    ) : LocalStateRepository {
        var writeCount: Int = 0
        override suspend fun read() = readResult ?: OperationResult.Success(state)
        override suspend fun write(state: LocalApplicationState): OperationResult<Unit> {
            writeCount += 1
            this.state = state
            return OperationResult.Success(Unit)
        }
        override suspend fun clear() = OperationResult.Success(Unit)
        override fun observe(): Flow<OperationResult<LocalApplicationState?>> = flowOf(OperationResult.Success(state))
        override suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity> = OperationResult.Success(
            LocalDeviceIdentity(
                state?.installationId ?: "550e8400-e29b-41d4-a716-446655440000",
                state?.identityCreatedAtEpochMillis ?: 1L,
            ),
        )
        override suspend fun getEnrollmentState() = OperationResult.Success(state?.enrollmentState ?: EnrollmentState.UNENROLLED)
        override suspend fun getConnectionState() = OperationResult.Success(state?.connectionState ?: ConnectionState.UNKNOWN)
        override suspend fun getManagedDeviceId() = OperationResult.Success(state?.managedDeviceId)
        override suspend fun completeEnrollment(managedDeviceId: String): OperationResult<Unit> {
            state = (state ?: LocalApplicationState()).copy(
                managedDeviceId = managedDeviceId,
                enrollmentState = EnrollmentState.ENROLLED,
            )
            return OperationResult.Success(Unit)
        }
        override suspend fun initializeLocalState(): OperationResult<LocalApplicationState> {
            val current = state ?: LocalApplicationState()
            val updated = current.copy(initialized = true, installationId = current.installationId ?: "550e8400-e29b-41d4-a716-446655440000", identityCreatedAtEpochMillis = current.identityCreatedAtEpochMillis ?: 1L)
            state = updated
            return OperationResult.Success(updated)
        }
        override suspend fun updateEnrollmentState(state: EnrollmentState) = OperationResult.Success(Unit)
        override suspend fun updateConnectionState(state: ConnectionState) = OperationResult.Success(Unit)
    }
}
