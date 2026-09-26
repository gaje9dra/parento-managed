package com.parento.managed.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.parento.managed.ParentoApplication
import com.parento.managed.R
import com.parento.managed.domain.OperationResult
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioAccessForegroundService : Service() {
    private var capture: AndroidAudioCaptureController? = null
    private var transport: AudioTransport? = null
    private var sessionId: String? = null
    private var reportedStarted = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        capture = AndroidAudioCaptureController(this) { error ->
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.FAILED, sessionId, System.currentTimeMillis(), error))
            stopSelf()
        }
        transport = UnavailableAudioTransport()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }

        val requestedSession = intent?.getStringExtra(EXTRA_SESSION_ID)
        val validatedSessionId = requestedSession ?: run {
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.FAILED, null, System.currentTimeMillis(), "INVALID_AUTHORIZATION_RESULT"))
            stopSelf()
            return START_NOT_STICKY
        }
        val current = AudioAccessRuntime.snapshot()
        if (!isUuid(validatedSessionId) || current.sessionId != validatedSessionId || current.state != AudioAccessState.STARTING) {
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.FAILED, null, System.currentTimeMillis(), "INVALID_AUTHORIZATION_RESULT"))
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.PERMISSION_REQUIRED, validatedSessionId, System.currentTimeMillis(), "MICROPHONE_PERMISSION_REQUIRED"))
            stopSelf()
            return START_NOT_STICKY
        }
        val mediaTransport = transport ?: UnavailableAudioTransport()
        if (!mediaTransport.isAvailable()) {
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.FAILED, validatedSessionId, System.currentTimeMillis(), "AUDIO_TRANSPORT_UNAVAILABLE"))
            stopSelf()
            return START_NOT_STICKY
        }

        return runCatching {
            startForegroundWithMicrophone()
            sessionId = validatedSessionId
            mediaTransport.start(validatedSessionId).getOrThrow()
            capture?.start(validatedSessionId) { buffer, length, timestamp ->
                mediaTransport.send(buffer, length, timestamp)
            }?.getOrThrow()
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.ACTIVE, validatedSessionId, System.currentTimeMillis()))
            reportedStarted = true
            serviceScope.launch {
                (application as ParentoApplication).deviceCommunicationSessionManager.reportAudioStarted(
                    validatedSessionId,
                    "{\"state\":\"ACTIVE\",\"transport\":\"audio-media\"}",
                )
            }
        }.onFailure {
            mediaTransport.stop()
            capture?.stop()
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.FAILED, validatedSessionId, System.currentTimeMillis(), "AUDIO_START_FAILED"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.let { START_NOT_STICKY }
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopCapture() {
        capture?.stop()
        transport?.stop()
        val current = AudioAccessRuntime.snapshot()
        val stoppedSession = sessionId
        if (reportedStarted && !stoppedSession.isNullOrBlank()) {
            serviceScope.launch {
                (application as ParentoApplication).deviceCommunicationSessionManager.reportAudioStopped(stoppedSession)
            }
        }
        reportedStarted = false
        if (current.state != AudioAccessState.FAILED) {
            AudioAccessRuntime.publish(AudioAccessSnapshot(AudioAccessState.STOPPED, current.sessionId, System.currentTimeMillis()))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionId = null
    }

    private fun startForegroundWithMicrophone() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.audio_access_notification_title))
            .setContentText(getString(R.string.audio_access_notification_active))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.audio_access_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun isUuid(value: String?): Boolean = !value.isNullOrBlank() && runCatching { UUID.fromString(value) }.isSuccess

    companion object {
        const val ACTION_START = "com.parento.managed.action.START_AUDIO_ACCESS"
        const val ACTION_STOP = "com.parento.managed.action.STOP_AUDIO_ACCESS"
        const val EXTRA_SESSION_ID = "audio_session_id"
        private const val CHANNEL_ID = "parento_audio_access"
        private const val NOTIFICATION_ID = 9301
    }
}
