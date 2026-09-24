package com.parento.managed.device

enum class ManagementDetectionError {
    MANAGEMENT_STATE_UNAVAILABLE,
    PLATFORM_API_ERROR,
    UNSUPPORTED_API,
    SECURITY_EXCEPTION,
    INVALID_MANAGEMENT_STATE,
}

data class ManagementDetectionResult(
    val mode: ManagementMode,
    val error: ManagementDetectionError? = null,
) {
    companion object {
        fun unmanaged(): ManagementDetectionResult =
            ManagementDetectionResult(ManagementMode.NOT_MANAGED)

        fun unknown(error: ManagementDetectionError): ManagementDetectionResult =
            ManagementDetectionResult(ManagementMode.UNKNOWN, error)
    }
}
