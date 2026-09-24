package com.parento.managed.device

enum class CapabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
    REQUIRES_AUTHORIZATION,
    REQUIRES_PERMISSION,
    NOT_SUPPORTED,
}

enum class DeviceManagementCapability {
    DEVICE_OWNER,
    PROFILE_OWNER,
    MANAGED_CONFIGURATION,
    POLICY_SUPPORT,
    LOCK_CAPABILITY,
    APP_MANAGEMENT_CAPABILITY,
    NETWORK_RESTRICTION_CAPABILITY,
    SCREEN_CAPTURE_CAPABILITY,
    CAMERA_CAPABILITY,
    MICROPHONE_CAPABILITY,
    LOCATION_CAPABILITY,
}

data class CapabilityState(
    val capability: DeviceManagementCapability,
    val status: CapabilityStatus,
)
