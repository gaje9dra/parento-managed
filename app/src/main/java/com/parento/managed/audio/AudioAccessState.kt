package com.parento.managed.audio

enum class AudioAccessState {
    IDLE,
    PERMISSION_REQUIRED,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    EXPIRED,
    FAILED,
}

data class AudioAccessSnapshot(
    val state: AudioAccessState,
    val sessionId: String?,
    val updatedAtEpochMillis: Long,
    val errorCategory: String? = null,
)
