package com.parento.managed.device

interface DeviceManagementManager {
    fun detectManagementMode(): ManagementMode
    fun detectManagementState(): ManagementDetectionResult
    fun evaluateCapabilities(
        detection: ManagementDetectionResult = detectManagementState(),
    ): List<CapabilityState>
}

interface DeviceManagementPlatform {
    fun isDeviceOwner(): Boolean
    fun isProfileOwner(): Boolean
    fun isDevicePolicySupported(): Boolean
}

class AndroidDeviceManagementManager(
    private val platform: DeviceManagementPlatform,
) : DeviceManagementManager, ManagementModeDetector {

    override fun detect(): ManagementDetectionResult = detectManagementState()

    override fun detectManagementState(): ManagementDetectionResult =
        runCatching {
            if (!platform.isDevicePolicySupported()) {
                return ManagementDetectionResult.unknown(
                    ManagementDetectionError.UNSUPPORTED_API,
                )
            }

            val deviceOwner = platform.isDeviceOwner()
            val profileOwner = platform.isProfileOwner()

            when {
                deviceOwner && profileOwner ->
                    ManagementDetectionResult.unknown(
                        ManagementDetectionError.INVALID_MANAGEMENT_STATE,
                    )

                deviceOwner -> ManagementDetectionResult(ManagementMode.DEVICE_OWNER)

                profileOwner -> ManagementDetectionResult(ManagementMode.PROFILE_OWNER)

                else -> ManagementDetectionResult.unmanaged()
            }
        }.getOrElse { error ->
            when (error) {
                is SecurityException ->
                    ManagementDetectionResult.unknown(
                        ManagementDetectionError.SECURITY_EXCEPTION,
                    )

                else ->
                    ManagementDetectionResult.unknown(
                        ManagementDetectionError.PLATFORM_API_ERROR,
                    )
            }
        }

    override fun detectManagementMode(): ManagementMode =
        detectManagementState().mode

    override fun evaluateCapabilities(
        detection: ManagementDetectionResult,
    ): List<CapabilityState> {
        val managed = detection.mode == ManagementMode.DEVICE_OWNER ||
            detection.mode == ManagementMode.PROFILE_OWNER

        val platformCapabilities = when {
            detection.error != null -> listOf(
                CapabilityState(DeviceManagementCapability.DEVICE_OWNER, CapabilityStatus.ERROR),
                CapabilityState(DeviceManagementCapability.PROFILE_OWNER, CapabilityStatus.ERROR),
                CapabilityState(
                    DeviceManagementCapability.DEVICE_POLICY_SUPPORTED,
                    CapabilityStatus.ERROR,
                ),
                CapabilityState(
                    DeviceManagementCapability.MANAGED_CONFIGURATION,
                    CapabilityStatus.ERROR,
                ),
            )

            else -> listOf(
                CapabilityState(
                    DeviceManagementCapability.DEVICE_OWNER,
                    if (detection.mode == ManagementMode.DEVICE_OWNER) {
                        CapabilityStatus.AVAILABLE
                    } else {
                        CapabilityStatus.UNAVAILABLE
                    },
                ),
                CapabilityState(
                    DeviceManagementCapability.PROFILE_OWNER,
                    if (detection.mode == ManagementMode.PROFILE_OWNER) {
                        CapabilityStatus.AVAILABLE
                    } else {
                        CapabilityStatus.UNAVAILABLE
                    },
                ),
                CapabilityState(
                    DeviceManagementCapability.DEVICE_POLICY_SUPPORTED,
                    CapabilityStatus.AVAILABLE,
                ),
                CapabilityState(
                    DeviceManagementCapability.MANAGED_CONFIGURATION,
                    if (managed) {
                        CapabilityStatus.AVAILABLE
                    } else {
                        CapabilityStatus.REQUIRES_AUTHORIZATION
                    },
                ),
            )
        }

        return platformCapabilities + listOf(
            // These operations are intentionally not implemented in Phase 4.2.
            CapabilityState(
                DeviceManagementCapability.POLICY_SUPPORT,
                when {
                    detection.error != null -> CapabilityStatus.ERROR
                    managed -> CapabilityStatus.NOT_SUPPORTED
                    else -> CapabilityStatus.REQUIRES_AUTHORIZATION
                },
            ),
            CapabilityState(
                DeviceManagementCapability.LOCK_CAPABILITY,
                when {
                    detection.error != null -> CapabilityStatus.ERROR
                    managed -> CapabilityStatus.NOT_SUPPORTED
                    else -> CapabilityStatus.REQUIRES_AUTHORIZATION
                },
            ),
            CapabilityState(
                DeviceManagementCapability.APP_MANAGEMENT_CAPABILITY,
                when {
                    detection.error != null -> CapabilityStatus.ERROR
                    managed -> CapabilityStatus.NOT_SUPPORTED
                    else -> CapabilityStatus.REQUIRES_AUTHORIZATION
                },
            ),
            CapabilityState(
                DeviceManagementCapability.NETWORK_RESTRICTION_CAPABILITY,
                when {
                    detection.error != null -> CapabilityStatus.ERROR
                    detection.mode == ManagementMode.DEVICE_OWNER -> CapabilityStatus.NOT_SUPPORTED
                    else -> CapabilityStatus.REQUIRES_AUTHORIZATION
                },
            ),
            CapabilityState(
                DeviceManagementCapability.SCREEN_CAPTURE_CAPABILITY,
                CapabilityStatus.NOT_SUPPORTED,
            ),
            CapabilityState(
                DeviceManagementCapability.CAMERA_CAPABILITY,
                CapabilityStatus.NOT_SUPPORTED,
            ),
            CapabilityState(
                DeviceManagementCapability.MICROPHONE_CAPABILITY,
                CapabilityStatus.NOT_SUPPORTED,
            ),
            CapabilityState(
                DeviceManagementCapability.LOCATION_CAPABILITY,
                CapabilityStatus.NOT_SUPPORTED,
            ),
        )
    }
}
