package com.parento.managed.screenshare

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureRuntime {
    private val state = MutableStateFlow(
        ScreenCaptureSnapshot(
            state = ScreenCaptureState.UNAVAILABLE,
            sessionId = null,
            updatedAtEpochMillis = 0L,
        ),
    )

    fun initialize(snapshot: ScreenCaptureSnapshot) {
        state.value = snapshot
    }

    fun snapshot(): ScreenCaptureSnapshot = state.value

    fun flow(): StateFlow<ScreenCaptureSnapshot> = state.asStateFlow()

    fun publish(snapshot: ScreenCaptureSnapshot) {
        state.value = snapshot
    }
}
