package com.parento.managed.monitoring

import com.parento.managed.device.ManagementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringTelemetrySerializerTest {
    @Test
    fun serializerUsesEstablishedSchemaAndPreservesNullableMetrics() {
        val snapshot = MonitoringSnapshot(
            collectedAtEpochMillis = 123456L,
            deviceInfo = DeviceInfo(
                managedDeviceId = "550e8400-e29b-41d4-a716-446655440000",
                installationId = "550e8400-e29b-41d4-a716-446655440001",
                managementMode = ManagementMode.DEVICE_OWNER,
                androidVersion = "15",
                apiLevel = 35,
                appVersion = "0.1.0",
                appVersionCode = 1L,
            ),
            battery = BatteryInfo(null, ChargingState.UNKNOWN, BatteryStatus.UNKNOWN),
            network = NetworkInfo(NetworkState.WIFI),
            storage = StorageInfo(1000L, 400L, 600L),
            memory = MemoryInfo(null, null, null),
            managementMode = ManagementMode.DEVICE_OWNER,
            lastSuccessfulInitializationEpochMillis = 100L,
            lastSuccessfulCommunicationEpochMillis = null,
            lastMonitoringUpdateEpochMillis = 123456L,
        )

        val payload = MonitoringTelemetrySerializer.serialize(snapshot)

        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals("550e8400-e29b-41d4-a716-446655440000", payload.getString("managedDeviceId"))
        assertEquals(123456L, payload.getLong("deviceCollectedAtEpochMillis"))
        assertTrue(payload.isNull("batteryPercentage"))
        assertTrue(payload.isNull("memoryTotalBytes"))
        assertTrue(payload.isNull("memoryLow"))
    }
}
