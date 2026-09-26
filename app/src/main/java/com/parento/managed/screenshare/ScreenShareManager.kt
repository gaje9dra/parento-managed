package com.parento.managed.screenshare

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.parento.managed.R
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

class ScreenShareManager(
    context: Context,
    private val isDeviceAuthorized: suspend () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val stateStore = ScreenCaptureStateStore(appContext)

    init {
        ScreenCaptureRuntime.initialize(normalizeAfterProcessStart(stateStore.read()))
    }

    val state: StateFlow<ScreenCaptureSnapshot>
        get() = ScreenCaptureRuntime.flow()

    suspend fun requestAuthorization(sessionId: String): Result<Intent> {
        if (!isDeviceAuthorized()) return Result.failure(SecurityException("Device is not authorized."))
        return requestAuthorizationInternal(sessionId)
    }

    suspend fun enforceAuthorization() {
        val current = state.value
        if (
            current.state in ACTIVE_STATES &&
            current.sessionId != null &&
            !isDeviceAuthorized()
        ) {
            terminateWithoutReauthorization(
                state = ScreenCaptureState.REVOKED,
                reason = "AUTHORIZATION_LOST",
            )
        }
    }

    fun handleBackendSessionExpired(sessionId: String): Result<Unit> {
        val current = state.value
        if (current.sessionId != null && current.sessionId != sessionId) {
            return Result.failure(IllegalArgumentException("Screen-sharing session mismatch."))
        }
        if (current.state !in ACTIVE_STATES) return Result.success(Unit)
        terminateWithoutReauthorization(
            state = ScreenCaptureState.EXPIRED,
            reason = "BACKEND_SESSION_EXPIRED",
        )
        return Result.success(Unit)
    }

    private fun requestAuthorizationInternal(sessionId: String): Result<Intent> {
        if (!isUuid(sessionId)) {
            return Result.failure(IllegalArgumentException("Invalid screen-sharing session."))
        }

        val current = state.value
        if (current.state in ACTIVE_STATES) {
            if (current.sessionId == sessionId && current.state == ScreenCaptureState.AUTHORIZATION_REQUIRED) {
                return createAuthorizationIntent()
            }
            return Result.failure(IllegalStateException("Another screen-sharing session is active or pending."))
        }

        return createAuthorizationIntent().map {
            publish(ScreenCaptureState.AUTHORIZATION_REQUIRED, sessionId)
            it
        }
    }

    fun requestAuthorizationFromCommand(sessionId: String): Result<Unit> {
        return requestAuthorizationInternal(sessionId).map {
            postAuthorizationNotification(sessionId)
            Unit
        }
    }

    fun startAfterConsent(
        activity: Activity,
        resultCode: Int,
        resultData: Intent,
        sessionId: String,
    ): Result<Unit> {
        if (!isUuid(sessionId)) {
            return Result.failure(IllegalArgumentException("Invalid screen-sharing session."))
        }
        if (resultCode != Activity.RESULT_OK) {
            publish(ScreenCaptureState.FAILED, sessionId, "AUTHORIZATION_DENIED")
            return Result.failure(SecurityException("MediaProjection authorization was not granted."))
        }

        val current = state.value
        if (current.sessionId != sessionId || current.state != ScreenCaptureState.AUTHORIZATION_REQUIRED) {
            return Result.failure(IllegalStateException("No matching screen-sharing authorization request exists."))
        }

        publish(ScreenCaptureState.AUTHORIZED, sessionId)
        publish(ScreenCaptureState.STARTING, sessionId)
        val serviceIntent = Intent(activity, ScreenCaptureForegroundService::class.java).apply {
            action = ScreenCaptureForegroundService.ACTION_START
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(activity, serviceIntent)
            } else {
                activity.startService(serviceIntent)
            }
        }.onFailure {
            publish(ScreenCaptureState.FAILED, sessionId, "FOREGROUND_SERVICE_START_FAILED")
        }.map { Unit }
    }

    fun stop(sessionId: String? = state.value.sessionId): Result<Unit> {
        val current = state.value
        if (sessionId != null && current.sessionId != null && sessionId != current.sessionId) {
            return Result.failure(IllegalArgumentException("Screen-sharing session mismatch."))
        }
        if (current.state == ScreenCaptureState.REVOKED || current.state == ScreenCaptureState.EXPIRED) {
            appContext.stopService(Intent(appContext, ScreenCaptureForegroundService::class.java))
            return Result.success(Unit)
        }
        if (current.state == ScreenCaptureState.STOPPED || current.state == ScreenCaptureState.UNAVAILABLE) {
            stateStore.clearActiveSession()
            ScreenCaptureRuntime.publish(stateStore.read())
            return Result.success(Unit)
        }

        publish(ScreenCaptureState.STOPPING, current.sessionId)
        appContext.stopService(Intent(appContext, ScreenCaptureForegroundService::class.java))
        return Result.success(Unit)
    }

    fun publish(state: ScreenCaptureState, sessionId: String?, error: String? = null) {
        val current = ScreenCaptureRuntime.snapshot()

        if (
            current.state in SECURITY_TERMINAL_STATES &&
            state in setOf(
                ScreenCaptureState.STOPPING,
                ScreenCaptureState.STOPPED,
            )
        ) {
            return
        }

        if (!ScreenCaptureStateMachine.canTransition(current.state, state)) {
            val invalid = ScreenCaptureSnapshot(
                state = ScreenCaptureState.FAILED,
                sessionId = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
                errorCategory = "INVALID_STATE_TRANSITION",
            )
            stateStore.write(invalid)
            ScreenCaptureRuntime.publish(invalid)
            return
        }

        val snapshot = ScreenCaptureSnapshot(
            state = state,
            sessionId = sessionId,
            updatedAtEpochMillis = System.currentTimeMillis(),
            errorCategory = error,
        )
        stateStore.write(snapshot)
        ScreenCaptureRuntime.publish(snapshot)
    }

    private fun terminateWithoutReauthorization(
        state: ScreenCaptureState,
        reason: String,
    ) {
        val current = ScreenCaptureRuntime.snapshot()
        if (current.state !in ACTIVE_STATES) return

        publish(
            state = state,
            sessionId = null,
            error = reason,
        )
        appContext.stopService(Intent(appContext, ScreenCaptureForegroundService::class.java))
    }

    private fun createAuthorizationIntent(): Result<Intent> {
        val manager = appContext.getSystemService(MediaProjectionManager::class.java)
            ?: return Result.failure(IllegalStateException("MediaProjection is unavailable."))
        return Result.success(manager.createScreenCaptureIntent())
    }

    private fun postAuthorizationNotification(sessionId: String) {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    AUTHORIZATION_CHANNEL_ID,
                    appContext.getString(R.string.screen_share_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val intent = Intent(appContext, ScreenShareActivity::class.java).apply {
            action = ScreenShareActivity.ACTION_AUTHORIZE
            putExtra(ScreenShareActivity.EXTRA_SESSION_ID, sessionId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            AUTHORIZATION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            manager.notify(
                AUTHORIZATION_NOTIFICATION_ID,
                android.app.Notification.Builder(appContext, AUTHORIZATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle(appContext.getString(R.string.screen_share_authorization_title))
                    .setContentText(appContext.getString(R.string.screen_share_authorization_message))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun normalizeAfterProcessStart(snapshot: ScreenCaptureSnapshot): ScreenCaptureSnapshot {
        return when (snapshot.state) {
            ScreenCaptureState.STARTING,
            ScreenCaptureState.ACTIVE,
            ScreenCaptureState.STOPPING,
            ScreenCaptureState.AUTHORIZED,
            -> ScreenCaptureSnapshot(
                ScreenCaptureState.FAILED,
                null,
                System.currentTimeMillis(),
                "PROCESS_RESTARTED",
            )

            else -> snapshot
        }
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess

    private companion object {
        val ACTIVE_STATES = setOf(
            ScreenCaptureState.REQUESTED,
            ScreenCaptureState.AUTHORIZATION_REQUIRED,
            ScreenCaptureState.AUTHORIZED,
            ScreenCaptureState.STARTING,
            ScreenCaptureState.ACTIVE,
            ScreenCaptureState.STOPPING,
        )
        val SECURITY_TERMINAL_STATES = setOf(
            ScreenCaptureState.REVOKED,
            ScreenCaptureState.EXPIRED,
        )
        const val AUTHORIZATION_CHANNEL_ID = "parento_screen_share_authorization"
        const val AUTHORIZATION_NOTIFICATION_ID = 9200
    }
}
