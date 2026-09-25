package com.parento.managed.monitoring

import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.LocalApplicationState
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import com.parento.managed.device.CapabilityState
import com.parento.managed.device.ManagementMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MonitoringCollectionTest {
    @Test
    fun partialCollectorFailurePreservesOtherMetrics() = runBlocking {
        val repository = FakeMonitoringRepository()
        val result = CollectDeviceMonitoringSnapshot(
            StaticDeviceProvider(),
            BatteryInfoProvider { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) },
            NetworkInfoProvider { OperationResult.Success(NetworkInfo(NetworkState.WIFI)) },
            StorageInfoProvider { OperationResult.Success(StorageInfo(1000L, 400L, 600L)) },
            MemoryInfoProvider { OperationResult.Success(MemoryInfo(2000L, 800L, false)) },
            ManagementInfoProvider { OperationResult.Success(ManagementMode.DEVICE_OWNER) },
            FakeLocalStateRepository(),
            repository,
            nowEpochMillis = { 1234L },
        ).execute()

        val snapshot = (result as OperationResult.Success).value
        assertEquals(1234L, snapshot.collectedAtEpochMillis)
        assertNull(snapshot.battery.percentage)
        assertEquals(NetworkState.WIFI, snapshot.network.state)
        assertEquals(400L, snapshot.storage.availableBytes)
        assertEquals(800L, snapshot.memory.availableBytes)
        assertEquals(ManagementMode.DEVICE_OWNER, snapshot.managementMode)
        assertEquals(snapshot, repository.saved)
    }
}

private class StaticDeviceProvider : DeviceInfoProvider {
    override fun get() = OperationResult.Success(
        DeviceInfo("managed-1", "550e8400-e29b-41d4-a716-446655440000", ManagementMode.PROFILE_OWNER, "15", 35, "0.1.0", 1L),
    )
}

private class FakeMonitoringRepository : MonitoringRepository {
    var saved: MonitoringSnapshot? = null
    override suspend fun read() = OperationResult.Success(saved)
    override suspend fun save(snapshot: MonitoringSnapshot) = OperationResult.Success(Unit).also { saved = snapshot }
    override fun observe(): Flow<OperationResult<MonitoringSnapshot?>> = emptyFlow()
}

private class FakeLocalStateRepository : LocalStateRepository {
    override suspend fun getOrCreateIdentity() = OperationResult.Success(LocalDeviceIdentity("550e8400-e29b-41d4-a716-446655440000", 1L))
    override suspend fun getEnrollmentState() = OperationResult.Success(EnrollmentState.ENROLLED)
    override suspend fun updateEnrollmentState(state: EnrollmentState) = OperationResult.Success(Unit)
    override suspend fun getConnectionState() = OperationResult.Success(ConnectionState.CONNECTED)
    override suspend fun updateConnectionState(state: ConnectionState) = OperationResult.Success(Unit)
    override suspend fun updateLastSynchronizationTimestamp(epochMillis: Long) = OperationResult.Success(Unit)
    override suspend fun getManagedDeviceId() = OperationResult.Success<String?>("managed-1")
    override suspend fun completeEnrollment(managedDeviceId: String) = OperationResult.Success(Unit)
    override suspend fun read() = OperationResult.Success<LocalApplicationState?>(null)
    override suspend fun write(state: LocalApplicationState) = OperationResult.Success(Unit)
    override suspend fun clear() = OperationResult.Success(Unit)
    override fun observe() = emptyFlow<OperationResult<LocalApplicationState?>>()
    override suspend fun initializeLocalState() = OperationResult.Success(LocalApplicationState())
}
