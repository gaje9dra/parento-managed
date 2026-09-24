package com.parento.managed.device

interface DeviceManagementManager {
    fun detectManagementMode(): ManagementMode
    fun evaluateCapabilities(): List<CapabilityState>
}

interface DeviceManagementPlatform {
    fun isDeviceOwner(): Boolean
    fun isProfileOwner(): Boolean
}

class AndroidDeviceManagementManager(
    private val platform: DeviceManagementPlatform,
) : DeviceManagementManager {
    override fun detectManagementMode(): ManagementMode =
        runCatching {
            when {
                platform.isDeviceOwner() -> ManagementMode.DEVICE_OWNER
                platform.isProfileOwner() -> ManagementMode.PROFILE_OWNER
                else -> ManagementMode.NOT_MANAGED
            }
        }.getOrElse { ManagementMode.UNKNOWN }

    override fun evaluateCapabilities(): List<CapabilityState> {
        val mode = detectManagementMode()
        val managed = mode == ManagementMode.DEVICE_OWNER || mode == ManagementMode.PROFILE_OWNER
        return listOf(
            capability(DeviceManagementCapability.DEVICE_OWNER, mode == ManagementMode.DEVICE_OWNER),
            capability(DeviceManagementCapability.PROFILE_OWNER, mode == ManagementMode.PROFILE_OWNER),
            capability(
                DeviceManagementCapability.MANAGED_CONFIGURATION,
                managed,
            ),
            CapabilityState(
                DeviceManagementCapability.POLICY_SUPPORT,
                if (managed) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_AUTHORIZATION,
            ),
            CapabilityState(
                DeviceManagementCapability.LOCK_CAPABILITY,
                if (managed) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_AUTHORIZATION,
            ),
            CapabilityState(
                DeviceManagementCapability.APP_MANAGEMENT_CAPABILITY,
                if (managed) CapabilityStatus.AVAILABLE else CapabilityStatus.REQUIRES_AUTHORIZATION,
            ),
            CapabilityState(
                DeviceManagementCapability.NETWORK_RESTRICTION_CAPABILITY,
                if (mode == ManagementMode.DEVICE_OWNER) CapabilityStatus.AVAILABLE
                else CapabilityStatus.REQUIRES_AUTHORIZATION,
            ),
            CapabilityState(
                DeviceManagementCapability.SCREEN_CAPTURE_CAPABILITY,
                CapabilityStatus.REQUIRES_PERMISSION,
            ),
            CapabilityState(
                DeviceManagementCapability.CAMERA_CAPABILITY,
                CapabilityStatus.REQUIRES_PERMISSION,
            ),
            CapabilityState(
                DeviceManagementCapability.MICROPHONE_CAPABILITY,
                CapabilityStatus.REQUIRES_PERMISSION,
            ),
            CapabilityState(
                DeviceManagementCapability.LOCATION_CAPABILITY,
                CapabilityStatus.REQUIRES_PERMISSION,
            ),
        )
    }

    private fun capability(
        capability: DeviceManagementCapability,
        available: Boolean,
    ) = CapabilityState(
        capability,
        if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
    )
}
