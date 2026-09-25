package com.parento.managed.communication

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.CapabilityState
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommunicationTelemetryTest {
    @Test
    fun submitMonitoringUsesAuthenticatedSessionAndPersistsSyncTimestamp() = runBlocking {
        val repository = FakeStateRepository()
        val credentialStore = FakeCredentialStore()
        val sessionStore = FakeSessionStore()
        val transport = FakeTransport()
        val manager = DeviceCommunicationSessionManager(repository, credentialStore, sessionStore, transport)

        assertEquals(OperationResult.Success(ConnectionState.CONNECTED), manager.connect())
        val result = manager.submitMonitoring(
            managedDeviceId = DEVICE_ID,
            payload = """{"schemaVersion":1}""",
            collectedAtEpochMillis = 123L,
        )

        assertTrue(result is OperationResult.Success)
        assertEquals(1, transport.monitoringCalls)
        assertEquals(123L, transport.lastCollectedAtEpochMillis)
        assertTrue(repository.lastSynchronizationTimestamp > 0L)
    }

    @Test
    fun submitMonitoringRejectsMismatchedManagedDevice() = runBlocking {
        val repository = FakeStateRepository()
        val sessionStore = FakeSessionStore()
        val manager = DeviceCommunicationSessionManager(
            repository,
            FakeCredentialStore(),
            sessionStore,
            FakeTransport(),
        )

        manager.connect()
        val result = manager.submitMonitoring(
            managedDeviceId = "550e8400-e29b-41d4-a716-446655440099",
            payload = """{}""",
            collectedAtEpochMillis = 123L,
        )

        assertEquals(OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE), result)
    }
}

private const val DEVICE_ID = "550e8400-e29b-41d4-a716-446655440000"

private class FakeStateRepository : LocalStateRepository {
    var lastSynchronizationTimestamp: Long = 0L
    override suspend fun getOrCreateIdentity() = OperationResult.Success(LocalDeviceIdentity("550e8400-e29b-41d4-a716-446655440001", 1L))
    override suspend fun getEnrollmentState() = OperationResult.Success(EnrollmentState.ENROLLED)
    override suspend fun updateEnrollmentState(state: EnrollmentState) = OperationResult.Success(Unit)
    override suspend fun getConnectionState() = OperationResult.Success(ConnectionState.CONNECTED)
    override suspend fun updateConnectionState(state: ConnectionState) = OperationResult.Success(Unit)
    override suspend fun updateLastSynchronizationTimestamp(epochMillis: Long): OperationResult<Unit> {
        lastSynchronizationTimestamp = epochMillis
        return OperationResult.Success(Unit)
    }
    override suspend fun getManagedDeviceId() = OperationResult.Success<String?>(DEVICE_ID)
    override suspend fun completeEnrollment(managedDeviceId: String) = OperationResult.Success(Unit)
    override suspend fun read() = OperationResult.Success<LocalApplicationState?>(null)
    override suspend fun write(state: LocalApplicationState) = OperationResult.Success(Unit)
    override suspend fun clear() = OperationResult.Success(Unit)
    override fun observe(): Flow<OperationResult<LocalApplicationState?>> = emptyFlow()
    override suspend fun initializeLocalState() = OperationResult.Success(LocalApplicationState())
}

private class FakeCredentialStore : DeviceCredentialStore {
    override fun read() = OperationResult.Success<String?>(CREDENTIAL)
    override fun save(value: String) = OperationResult.Success(Unit)
    override fun clear() = OperationResult.Success(Unit)
}

private class FakeSessionStore : DeviceSessionStore {
    private var value: DeviceSession? = null
    override fun read() = OperationResult.Success(value)
    override fun save(session: DeviceSession): OperationResult<Unit> {
        value = session
        return OperationResult.Success(Unit)
    }
    override fun clear(): OperationResult<Unit> {
        value = null
        return OperationResult.Success(Unit)
    }
}

private class FakeTransport : DeviceTransport {
    var monitoringCalls = 0
    var lastCollectedAtEpochMillis = 0L
    override suspend fun connect(deviceCredential: String) =
        OperationResult.Success(
            TransportSession(
                "550e8400-e29b-41d4-a716-446655440010",
                DEVICE_ID,
                SESSION_TOKEN,
                System.currentTimeMillis() + 60_000L,
            ),
        )
    override suspend fun heartbeat(sessionToken: String) = OperationResult.Success(System.currentTimeMillis() + 60_000L)
    override suspend fun disconnect(sessionToken: String) = OperationResult.Success(Unit)
    override suspend fun acknowledge(sessionToken: String, commandId: String) = OperationResult.Success(Unit)
    override suspend fun start(sessionToken: String, commandId: String) = OperationResult.Success(Unit)
    override suspend fun result(sessionToken: String, commandId: String, status: String, resultCode: String?, errorCategory: String?, resultMetadata: String?) = OperationResult.Success(Unit)
    override suspend fun receiveNextCommand(sessionToken: String) = OperationResult.Success<TransportCommand?>(null)
    override suspend fun submitMonitoring(sessionToken: String, payload: String): OperationResult<Unit> {
        monitoringCalls++
        lastCollectedAtEpochMillis = 123L
        return OperationResult.Success(Unit)
    }
}

private const val CREDENTIAL = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
private const val SESSION_TOKEN = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
