package com.parento.managed.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.parento.managed.R
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

class AudioAccessManager(context: Context) {
    private val appContext = context.applicationContext

    val state: StateFlow<AudioAccessSnapshot>
        get() = AudioAccessRuntime.flow()

    fun mediaTransportAvailable(): Boolean = false

    fun microphonePermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestPermissionFromCommand(sessionId: String): Result<Unit> {
        if (!isUuid(sessionId)) return Result.failure(IllegalArgumentException("Invalid audio session."))
        AudioAccessRuntime.publish(snapshot(AudioAccessState.PERMISSION_REQUIRED, sessionId, "MICROPHONE_PERMISSION_REQUIRED"))
        postPermissionNotification()
        return Result.success(Unit)
    }

    fun startFromAuthorizedCommand(sessionId: String): Result<Unit> {
        if (!isUuid(sessionId)) return Result.failure(IllegalArgumentException("Invalid audio session."))
        if (!microphonePermissionGranted()) {
            requestPermissionFromCommand(sessionId)
            return Result.failure(SecurityException("Microphone permission is required."))
        }
        val current = state.value
        if (current.state == AudioAccessState.ACTIVE && current.sessionId == sessionId) return Result.success(Unit)
        if (current.state in setOf(AudioAccessState.STARTING, AudioAccessState.ACTIVE, AudioAccessState.STOPPING) && current.sessionId != sessionId) {
            return Result.failure(IllegalStateException("Another audio session is active."))
        }
        AudioAccessRuntime.publish(snapshot(AudioAccessState.STARTING, sessionId))
        return runCatching {
            val intent = Intent(appContext, AudioAccessForegroundService::class.java).apply {
                action = AudioAccessForegroundService.ACTION_START
                putExtra(AudioAccessForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure {
            AudioAccessRuntime.publish(snapshot(AudioAccessState.FAILED, sessionId, "FOREGROUND_SERVICE_START_FAILED"))
        }.map { Unit }
    }

    fun stop(sessionId: String? = state.value.sessionId): Result<Unit> {
        val current = state.value
        if (sessionId != null && current.sessionId != null && sessionId != current.sessionId) {
            return Result.failure(IllegalArgumentException("Audio session mismatch."))
        }
        if (current.state == AudioAccessState.IDLE || current.state == AudioAccessState.STOPPED) return Result.success(Unit)
        AudioAccessRuntime.publish(snapshot(AudioAccessState.STOPPING, current.sessionId))
        appContext.stopService(Intent(appContext, AudioAccessForegroundService::class.java))
        return Result.success(Unit)
    }

    fun handleBackendSessionExpired(sessionId: String): Result<Unit> {
        val current = state.value
        if (current.sessionId != null && current.sessionId != sessionId) return Result.failure(IllegalArgumentException("Audio session mismatch."))
        stop(sessionId)
        AudioAccessRuntime.publish(snapshot(AudioAccessState.FAILED, null, "BACKEND_SESSION_EXPIRED"))
        return Result.success(Unit)
    }

    fun enforcePermissionBoundary() {
        val current = state.value
        if (current.state in setOf(AudioAccessState.STARTING, AudioAccessState.ACTIVE, AudioAccessState.STOPPING) && !microphonePermissionGranted()) {
            stop(current.sessionId)
            AudioAccessRuntime.publish(snapshot(AudioAccessState.FAILED, null, "MICROPHONE_PERMISSION_REVOKED"))
        }
    }

    private fun postPermissionNotification() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "parento_audio_permission"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, appContext.getString(R.string.audio_permission_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(appContext, com.parento.managed.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = android.app.PendingIntent.getActivity(appContext, 9300, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            manager.notify(
                9300,
                android.app.Notification.Builder(appContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(appContext.getString(R.string.audio_permission_title))
                    .setContentText(appContext.getString(R.string.audio_permission_message))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun snapshot(state: AudioAccessState, sessionId: String?, error: String? = null) =
        AudioAccessSnapshot(state, sessionId, System.currentTimeMillis(), error)

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
