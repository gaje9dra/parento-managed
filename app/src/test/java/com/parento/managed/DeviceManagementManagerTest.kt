package com.parento.managed

import com.parento.managed.device.AndroidDeviceManagementManager
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.DeviceManagementPlatform
import com.parento.managed.device.ManagementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceManagementManagerTest {
    @Test
    fun unmanagedDeviceReportsAuthorizationBoundaries() {
        val manager = AndroidDeviceManagementManager(
            object : DeviceManagementPlatform {
                override fun isDeviceOwner() = false
                override fun isProfileOwner() = false
            },
        )

        assertEquals(ManagementMode.NOT_MANAGED, manager.detectManagementMode())
        val capabilities = manager.evaluateCapabilities().associateBy { it.capability }
        assertEquals(CapabilityStatus.UNAVAILABLE, capabilities[DeviceManagementCapability.DEVICE_OWNER]?.status)
        assertEquals(CapabilityStatus.REQUIRES_AUTHORIZATION, capabilities[DeviceManagementCapability.POLICY_SUPPORT]?.status)
        assertEquals(CapabilityStatus.REQUIRES_PERMISSION, capabilities[DeviceManagementCapability.CAMERA_CAPABILITY]?.status)
        assertTrue(capabilities.keys.contains(DeviceManagementCapability.SCREEN_CAPTURE_CAPABILITY))
    }

    @Test
    fun deviceOwnerIsDetectedWithoutActivatingAnyPolicy() {
        val manager = AndroidDeviceManagementManager(
            object : DeviceManagementPlatform {
                override fun isDeviceOwner() = true
                override fun isProfileOwner() = false
            },
        )

        assertEquals(ManagementMode.DEVICE_OWNER, manager.detectManagementMode())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            manager.evaluateCapabilities()
                .first { it.capability == DeviceManagementCapability.POLICY_SUPPORT }
                .status,
        )
    }
}
