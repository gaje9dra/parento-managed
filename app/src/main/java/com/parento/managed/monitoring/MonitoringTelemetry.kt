package com.parento.managed.monitoring

import org.json.JSONObject

object MonitoringTelemetrySerializer {
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

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
            .putNullable("batteryPercentage", snapshot.battery.percentage)
            .put("chargingState", snapshot.battery.chargingState.name)
            .put("batteryStatus", snapshot.battery.status.name)
            .put("networkState", snapshot.network.state.name)
            .putNullable("storageTotalBytes", snapshot.storage.totalBytes)
            .putNullable("storageAvailableBytes", snapshot.storage.availableBytes)
            .putNullable("storageUsedBytes", snapshot.storage.usedBytes)
            .putNullable("memoryTotalBytes", snapshot.memory.totalBytes)
            .putNullable("memoryAvailableBytes", snapshot.memory.availableBytes)
            .putNullable("memoryLow", snapshot.memory.lowMemory)
            .putNullable("lastSuccessfulInitializationEpochMillis", snapshot.lastSuccessfulInitializationEpochMillis)
            .putNullable("lastSuccessfulCommunicationEpochMillis", snapshot.lastSuccessfulCommunicationEpochMillis)
            .put("lastMonitoringUpdateEpochMillis", snapshot.lastMonitoringUpdateEpochMillis)
    }
}
