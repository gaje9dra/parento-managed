package com.parento.managed.audio

/**
 * Media transport boundary for live audio. Phase 10.2 does not invent a media
 * protocol because the backend contract currently exposes session control only.
 */
interface AudioTransport {
    fun isAvailable(): Boolean
    fun start(sessionId: String): Result<Unit>
    fun send(frame: ByteArray, length: Int, timestampEpochMillis: Long): Result<Unit>
    fun stop()
}

class UnavailableAudioTransport : AudioTransport {
    override fun isAvailable(): Boolean = false
    override fun start(sessionId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio media transport is not available in Phase 10.2."))
    override fun send(frame: ByteArray, length: Int, timestampEpochMillis: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio media transport is not available in Phase 10.2."))
    override fun stop() = Unit
}
