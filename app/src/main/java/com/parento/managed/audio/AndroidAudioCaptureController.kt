package com.parento.managed.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AndroidAudioCaptureController(
    context: Context,
    private val onFailure: (String) -> Unit = {},
) : AudioCaptureController {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun start(sessionId: String, onFrame: (ByteArray, Int, Long) -> Result<Unit>): Result<Unit> =
        synchronized(lock) {
            if (running.get()) return Result.failure(IllegalStateException("Audio capture is already active."))
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return Result.failure(SecurityException("RECORD_AUDIO permission is not granted."))
            }

            val selected = createRecorder()
                ?: return Result.failure(IllegalStateException("No supported microphone configuration is available."))

            return runCatching {
                recorder = selected
                running.set(true)
                selected.startRecording()
                if (selected.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    error("AudioRecord did not enter recording state.")
                }
                worker = Thread({ captureLoop(selected, onFrame) }, "parento-audio-capture").also {
                    it.isDaemon = true
                    it.start()
                }
            }.onFailure {
                running.set(false)
                releaseLocked()
                onFailure("CAPTURE_INITIALIZATION_FAILED")
            }.map { Unit }
        }

    override fun stop() {
        synchronized(lock) {
            running.set(false)
            releaseLocked()
        }
    }

    private fun captureLoop(
        localRecorder: AudioRecord,
        onFrame: (ByteArray, Int, Long) -> Result<Unit>,
    ) {
        val frameBytes = max(1, localRecorder.sampleRate / 50 * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        val buffer = ByteArray(frameBytes)
        try {
            while (running.get() && recorder === localRecorder) {
                val read = localRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    onFailure("CAPTURE_READ_FAILED")
                    break
                }
                if (read == 0) continue
                if (onFrame(buffer, read, System.currentTimeMillis()).isFailure) {
                    onFailure("AUDIO_TRANSPORT_FAILURE")
                    break
                }
            }
        } catch (_: Throwable) {
            onFailure("CAPTURE_RUNTIME_FAILED")
        } finally {
            synchronized(lock) {
                if (recorder === localRecorder) {
                    running.set(false)
                    releaseLocked()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord? {
        for (sampleRate in listOf(48000, 16000)) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue
            val bufferSize = max(minBuffer, sampleRate / 10 * CHANNEL_COUNT * BYTES_PER_SAMPLE)
            val candidate = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) return candidate
            candidate.release()
        }
        return null
    }

    private fun releaseLocked() {
        worker = null
        recorder?.let {
            runCatching { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
            it.release()
        }
        recorder = null
    }

    private companion object {
        const val CHANNEL_COUNT = 1
        const val BYTES_PER_SAMPLE = 2
    }
}
