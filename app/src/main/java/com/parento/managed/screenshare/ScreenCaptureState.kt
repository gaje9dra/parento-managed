package com.parento.managed.screenshare

enum class ScreenCaptureState {
    UNAVAILABLE,
    REQUESTED,
    AUTHORIZATION_REQUIRED,
    AUTHORIZED,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    FAILED,
    REVOKED,
    EXPIRED,
}

data class ScreenCaptureSnapshot(
    val state: ScreenCaptureState,
    val sessionId: String?,
    val updatedAtEpochMillis: Long,
    val errorCategory: String? = null,
)
