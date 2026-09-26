package com.parento.managed.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.parento.managed.R

class ScreenCaptureForegroundService : Service() {
    private var controller: AndroidScreenCaptureController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        controller = AndroidScreenCaptureController(this) { state, error ->
            val current = ScreenCaptureRuntime.snapshot()
            val securityTerminal = current.state == ScreenCaptureState.REVOKED ||
                current.state == ScreenCaptureState.EXPIRED
            val cleanupCallback = state == ScreenCaptureState.STOPPING ||
                state == ScreenCaptureState.STOPPED

            if (!(securityTerminal && cleanupCallback)) {
                ScreenCaptureRuntime.publish(
                    ScreenCaptureSnapshot(
                        state = state,
                        sessionId = current.sessionId,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                        errorCategory = error,
                    ),
                )
            }

            if (
                state == ScreenCaptureState.STOPPED ||
                state == ScreenCaptureState.FAILED ||
                state == ScreenCaptureState.REVOKED ||
                state == ScreenCaptureState.EXPIRED
            ) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller?.stop()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = intent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_RESULT_DATA)
            }
        }
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val current = ScreenCaptureRuntime.snapshot()

        if (
            resultCode == Int.MIN_VALUE ||
            resultData == null ||
            !isUuid(sessionId) ||
            current.sessionId != sessionId ||
            current.state != ScreenCaptureState.STARTING
        ) {
            ScreenCaptureRuntime.publish(
                ScreenCaptureSnapshot(
                    ScreenCaptureState.FAILED,
                    null,
                    System.currentTimeMillis(),
                    "INVALID_AUTHORIZATION_RESULT",
                ),
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val validatedSessionId = sessionId
            ?: return START_NOT_STICKY

        controller?.start(resultCode, resultData, validatedSessionId)
            ?.onFailure {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.screen_share_notification_title))
            .setContentText(getString(R.string.screen_share_notification_active))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.screen_share_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun isUuid(value: String?): Boolean =
        !value.isNullOrBlank() && runCatching { java.util.UUID.fromString(value) }.isSuccess

    companion object {
        const val ACTION_START = "com.parento.managed.action.START_SCREEN_SHARE"
        const val ACTION_STOP = "com.parento.managed.action.STOP_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "parento_screen_share"
        private const val NOTIFICATION_ID = 9201
    }
}
