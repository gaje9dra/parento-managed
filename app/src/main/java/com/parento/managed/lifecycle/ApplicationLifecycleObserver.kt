package com.parento.managed.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.parento.managed.logging.LogLevel
import com.parento.managed.logging.ManagedLogger

enum class ApplicationLifecycleState {
    FOREGROUND,
    BACKGROUND,
}

class ApplicationLifecycleObserver(
    private val onForeground: () -> Unit,
    private val logger: ManagedLogger,
) : DefaultLifecycleObserver {
    var state: ApplicationLifecycleState = ApplicationLifecycleState.BACKGROUND
        private set

    override fun onStart(owner: LifecycleOwner) {
        state = ApplicationLifecycleState.FOREGROUND
        logger.log(LogLevel.INFO, "Application entered foreground.")
        onForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        state = ApplicationLifecycleState.BACKGROUND
        logger.log(LogLevel.INFO, "Application entered background.")
    }
}
