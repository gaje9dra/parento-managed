package com.parento.managed

import com.parento.managed.device.AndroidDeviceManagementManager
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.DeviceManagementPlatform
import com.parento.managed.device.ManagementDetectionError
import com.parento.managed.device.ManagementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceManagementManagerTest {
    private fun platform(
        deviceOwner: Boolean = false,
        profileOwner: Boolean = false,
        supported: Boolean = true,
    ) = object : DeviceManagementPlatform {
        override fun isDeviceOwner() = deviceOwner
        override fun isProfileOwner() = profileOwner
        override fun isDevicePolicySupported() = supported
    }

    @Test
    fun unmanagedDeviceIsDistinctFromUnavailableManagementState() {
        val manager = AndroidDeviceManagementManager(platform())

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.NOT_MANAGED, detection.mode)
        assertEquals(null, detection.error)
        val capabilities = manager.evaluateCapabilities(detection).associateBy { it.capability }
        assertEquals(
            CapabilityStatus.UNAVAILABLE,
            capabilities[DeviceManagementCapability.DEVICE_OWNER]?.status,
        )
        assertEquals(
            CapabilityStatus.REQUIRES_AUTHORIZATION,
            capabilities[DeviceManagementCapability.MANAGED_CONFIGURATION]?.status,
        )
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            capabilities[DeviceManagementCapability.CAMERA_CAPABILITY]?.status,
        )
    }

    @Test
    fun deviceOwnerIsDetectedFromPlatformState() {
        val manager = AndroidDeviceManagementManager(platform(deviceOwner = true))

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.DEVICE_OWNER, detection.mode)
        assertEquals(null, detection.error)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            manager.evaluateCapabilities(detection)
                .first { it.capability == DeviceManagementCapability.DEVICE_OWNER }
                .status,
        )
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            manager.evaluateCapabilities(detection)
                .first { it.capability == DeviceManagementCapability.POLICY_SUPPORT }
                .status,
        )
    }

    @Test
    fun profileOwnerIsNotMisclassifiedAsDeviceOwner() {
        val manager = AndroidDeviceManagementManager(platform(profileOwner = true))

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.PROFILE_OWNER, detection.mode)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            manager.evaluateCapabilities(detection)
                .first { it.capability == DeviceManagementCapability.PROFILE_OWNER }
                .status,
        )
        assertEquals(
            CapabilityStatus.UNAVAILABLE,
            manager.evaluateCapabilities(detection)
                .first { it.capability == DeviceManagementCapability.DEVICE_OWNER }
                .status,
        )
    }

    @Test
    fun unsupportedPlatformFailsClosed() {
        val manager = AndroidDeviceManagementManager(platform(supported = false))

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.UNKNOWN, detection.mode)
        assertEquals(ManagementDetectionError.UNSUPPORTED_API, detection.error)
        assertTrue(
            manager.evaluateCapabilities(detection)
                .all {
                    it.status == CapabilityStatus.ERROR ||
                        it.status == CapabilityStatus.NOT_SUPPORTED
                },
        )
    }

    @Test
    fun unexpectedPlatformExceptionBecomesSafeDomainState() {
        val manager = AndroidDeviceManagementManager(
            object : DeviceManagementPlatform {
                override fun isDeviceOwner(): Boolean = throw IllegalStateException("unexpected")
                override fun isProfileOwner() = false
                override fun isDevicePolicySupported() = true
            },
        )

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.UNKNOWN, detection.mode)
        assertEquals(ManagementDetectionError.PLATFORM_API_ERROR, detection.error)
    }

    @Test
    fun securityExceptionBecomesSafeDomainState() {
        val manager = AndroidDeviceManagementManager(
            object : DeviceManagementPlatform {
                override fun isDeviceOwner(): Boolean = throw SecurityException("blocked")
                override fun isProfileOwner() = false
                override fun isDevicePolicySupported() = true
            },
        )

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.UNKNOWN, detection.mode)
        assertEquals(ManagementDetectionError.SECURITY_EXCEPTION, detection.error)
    }

    @Test
    fun conflictingOwnerSignalsAreRejected() {
        val manager = AndroidDeviceManagementManager(
            platform(deviceOwner = true, profileOwner = true),
        )

        val detection = manager.detectManagementState()

        assertEquals(ManagementMode.UNKNOWN, detection.mode)
        assertEquals(ManagementDetectionError.INVALID_MANAGEMENT_STATE, detection.error)
    }
}
