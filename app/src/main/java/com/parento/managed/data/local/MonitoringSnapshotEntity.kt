package com.parento.managed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.parento.managed.monitoring.BatteryStatus
import com.parento.managed.monitoring.ChargingState
import com.parento.managed.monitoring.NetworkState
import com.parento.managed.monitoring.MonitoringSnapshot

@Entity(tableName = "monitoring_snapshot")
data class MonitoringSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val collectedAtEpochMillis: Long,
    val managedDeviceId: String?,
    val installationId: String?,
    val managementMode: String,
    val androidVersion: String,
    val apiLevel: Int,
    val appVersion: String,
    val appVersionCode: Long,
    val batteryPercentage: Int?,
    val chargingState: String,
    val batteryStatus: String,
    val networkState: String,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
    val storageUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val lowMemory: Boolean?,
    val lastSuccessfulInitializationEpochMillis: Long?,
    val lastSuccessfulCommunicationEpochMillis: Long?,
    val lastMonitoringUpdateEpochMillis: Long,
)
fun MonitoringSnapshot.toEntity() = MonitoringSnapshotEntity(
    collectedAtEpochMillis = collectedAtEpochMillis,
    managedDeviceId = deviceInfo.managedDeviceId,
    installationId = deviceInfo.installationId,
    managementMode = managementMode.name,
    androidVersion = deviceInfo.androidVersion,
    apiLevel = deviceInfo.apiLevel,
    appVersion = deviceInfo.appVersion,
    appVersionCode = deviceInfo.appVersionCode,
    batteryPercentage = battery.percentage,
    chargingState = battery.chargingState.name,
    batteryStatus = battery.status.name,
    networkState = network.state.name,
    storageTotalBytes = storage.totalBytes,
    storageAvailableBytes = storage.availableBytes,
    storageUsedBytes = storage.usedBytes,
    memoryTotalBytes = memory.totalBytes,
    memoryAvailableBytes = memory.availableBytes,
    lowMemory = memory.lowMemory,
    lastSuccessfulInitializationEpochMillis = lastSuccessfulInitializationEpochMillis,
    lastSuccessfulCommunicationEpochMillis = lastSuccessfulCommunicationEpochMillis,
    lastMonitoringUpdateEpochMillis = lastMonitoringUpdateEpochMillis,
)
fun MonitoringSnapshotEntity.toDomain() = MonitoringSnapshot(
    collectedAtEpochMillis = collectedAtEpochMillis,
    deviceInfo = com.parento.managed.monitoring.DeviceInfo(
        managedDeviceId, installationId,
        runCatching { com.parento.managed.device.ManagementMode.valueOf(managementMode) }.getOrDefault(com.parento.managed.device.ManagementMode.UNKNOWN),
        androidVersion, apiLevel, appVersion, appVersionCode,
    ),
    battery = com.parento.managed.monitoring.BatteryInfo(
        batteryPercentage,
        runCatching { ChargingState.valueOf(chargingState) }.getOrDefault(ChargingState.UNKNOWN),
        runCatching { BatteryStatus.valueOf(batteryStatus) }.getOrDefault(BatteryStatus.UNKNOWN),
    ),
    network = com.parento.managed.monitoring.NetworkInfo(
        runCatching { NetworkState.valueOf(networkState) }.getOrDefault(NetworkState.UNKNOWN),
    ),
    storage = com.parento.managed.monitoring.StorageInfo(storageTotalBytes, storageAvailableBytes, storageUsedBytes),
    memory = com.parento.managed.monitoring.MemoryInfo(memoryTotalBytes, memoryAvailableBytes, lowMemory),
    managementMode = runCatching { com.parento.managed.device.ManagementMode.valueOf(managementMode) }.getOrDefault(com.parento.managed.device.ManagementMode.UNKNOWN),
    lastSuccessfulInitializationEpochMillis = lastSuccessfulInitializationEpochMillis,
    lastSuccessfulCommunicationEpochMillis = lastSuccessfulCommunicationEpochMillis,
    lastMonitoringUpdateEpochMillis = lastMonitoringUpdateEpochMillis,
)
