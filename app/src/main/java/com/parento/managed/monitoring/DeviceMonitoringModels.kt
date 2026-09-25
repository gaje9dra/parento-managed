package com.parento.managed.monitoring

import com.parento.managed.device.ManagementMode

enum class ChargingState { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }
enum class BatteryStatus { NORMAL, LOW, CRITICAL, FULL, UNKNOWN }
enum class NetworkState { UNKNOWN, OFFLINE, WIFI, CELLULAR, OTHER }

data class BatteryInfo(
    val percentage: Int?,
    val chargingState: ChargingState,
    val status: BatteryStatus,
)

data class NetworkInfo(val state: NetworkState)
data class StorageInfo(val totalBytes: Long?, val availableBytes: Long?, val usedBytes: Long?)
data class MemoryInfo(val totalBytes: Long?, val availableBytes: Long?, val lowMemory: Boolean?)

data class DeviceInfo(
    val managedDeviceId: String?,
    val installationId: String?,
    val managementMode: ManagementMode,
    val androidVersion: String,
    val apiLevel: Int,
    val appVersion: String,
    val appVersionCode: Long,
)

data class MonitoringSnapshot(
    val collectedAtEpochMillis: Long,
    val deviceInfo: DeviceInfo,
    val battery: BatteryInfo,
    val network: NetworkInfo,
    val storage: StorageInfo,
    val memory: MemoryInfo,
    val managementMode: ManagementMode,
    val lastSuccessfulInitializationEpochMillis: Long?,
    val lastSuccessfulCommunicationEpochMillis: Long?,
    val lastMonitoringUpdateEpochMillis: Long,
)
