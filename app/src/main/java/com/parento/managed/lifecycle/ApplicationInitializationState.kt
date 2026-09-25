package com.parento.managed.lifecycle

import com.parento.managed.domain.ManagedError

enum class InitializationStatus {
    NOT_STARTED,
    INITIALIZING,
    READY,
    FAILED,
}

data class ApplicationInitializationState(
    val status: InitializationStatus = InitializationStatus.NOT_STARTED,
    val failedSubsystem: String? = null,
    val error: ManagedError? = null,
)
