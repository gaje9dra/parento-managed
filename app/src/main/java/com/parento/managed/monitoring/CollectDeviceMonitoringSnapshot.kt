package com.parento.managed.monitoring

import com.parento.managed.data.LocalStateRepository
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class CollectDeviceMonitoringSnapshot(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val batteryInfoProvider: BatteryInfoProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val storageInfoProvider: StorageInfoProvider,
    private val memoryInfoProvider: MemoryInfoProvider,
    private val managementInfoProvider: ManagementInfoProvider,
    private val localStateRepository: LocalStateRepository,
    private val monitoringRepository: MonitoringRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun execute(): OperationResult<MonitoringSnapshot> {
        val collectedAt = nowEpochMillis()
        val deviceInfo = (deviceInfoProvider.get() as? OperationResult.Success)?.value
            ?: return OperationResult.Failure(ManagedError.PLATFORM_FAILURE)
        val battery = (batteryInfoProvider.get() as? OperationResult.Success)?.value
            ?: BatteryInfo(null, ChargingState.UNKNOWN, BatteryStatus.UNKNOWN)
        val network = (networkInfoProvider.get() as? OperationResult.Success)?.value
            ?: NetworkInfo(NetworkState.UNKNOWN)
        val storage = (storageInfoProvider.get() as? OperationResult.Success)?.value
            ?: StorageInfo(null, null, null)
        val memory = (memoryInfoProvider.get() as? OperationResult.Success)?.value
            ?: MemoryInfo(null, null, null)
        val managementMode = (managementInfoProvider.get() as? OperationResult.Success)?.value
            ?: deviceInfo.managementMode

        val localState = (localStateRepository.read() as? OperationResult.Success)?.value
        val snapshot = MonitoringSnapshot(
            collectedAtEpochMillis = collectedAt,
            deviceInfo = deviceInfo.copy(managementMode = managementMode),
            battery = battery,
            network = network,
            storage = storage,
            memory = memory,
            managementMode = managementMode,
            lastSuccessfulInitializationEpochMillis = localState?.managementStateUpdatedAtEpochMillis,
            lastSuccessfulCommunicationEpochMillis = localState?.lastSynchronizationTimestamp,
            lastMonitoringUpdateEpochMillis = collectedAt,
        )
        return when (val saved = monitoringRepository.save(snapshot)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(snapshot)
        }
    }
}
