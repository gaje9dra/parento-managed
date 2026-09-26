package com.parento.managed.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioAccessRuntime {
    private val state = MutableStateFlow(
        AudioAccessSnapshot(AudioAccessState.IDLE, null, 0L),
    )

    fun snapshot(): AudioAccessSnapshot = state.value
    fun flow(): StateFlow<AudioAccessSnapshot> = state.asStateFlow()
    fun publish(snapshot: AudioAccessSnapshot) { state.value = snapshot }
    fun reset() { publish(AudioAccessSnapshot(AudioAccessState.IDLE, null, System.currentTimeMillis())) }
}
