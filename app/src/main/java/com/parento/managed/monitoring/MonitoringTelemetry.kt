package com.parento.managed.monitoring

import org.json.JSONObject

object MonitoringTelemetrySerializer {
    const val SCHEMA_VERSION = 1

    fun serialize(snapshot: MonitoringSnapshot): JSONObject {
        val device = snapshot.deviceInfo
        return JSONObject()
            .put("managedDeviceId", device.managedDeviceId)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("deviceCollectedAtEpochMillis", snapshot.collectedAtEpochMillis)
            .put("androidVersion", device.androidVersion)
            .put("apiLevel", device.apiLevel)
            .put("appVersion", device.appVersion)
            .put("appVersionCode", device.appVersionCode)
            .put("managementMode", snapshot.managementMode.name)
            .put("batteryPercentage", snapshot.battery.percentage)
            .put("chargingState", snapshot.battery.chargingState.name)
            .put("batteryStatus", snapshot.battery.status.name)
            .put("networkState", snapshot.network.state.name)
            .put("storageTotalBytes", snapshot.storage.totalBytes)
            .put("storageAvailableBytes", snapshot.storage.availableBytes)
            .put("storageUsedBytes", snapshot.storage.usedBytes)
            .put("memoryTotalBytes", snapshot.memory.totalBytes)
            .put("memoryAvailableBytes", snapshot.memory.availableBytes)
            .put("memoryLow", snapshot.memory.lowMemory)
            .put("lastSuccessfulInitializationEpochMillis", snapshot.lastSuccessfulInitializationEpochMillis)
            .put("lastSuccessfulCommunicationEpochMillis", snapshot.lastSuccessfulCommunicationEpochMillis)
            .put("lastMonitoringUpdateEpochMillis", snapshot.lastMonitoringUpdateEpochMillis)
    }
}
