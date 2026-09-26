package com.parento.managed.audio

interface AudioCaptureController {
    fun start(sessionId: String, onFrame: (ByteArray, Int, Long) -> Result<Unit>): Result<Unit>
    fun stop()
}
