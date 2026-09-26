package com.parento.managed.screenshare

import android.content.Context

class ScreenCaptureStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): ScreenCaptureSnapshot {
        val state = runCatching {
            ScreenCaptureState.valueOf(preferences.getString(KEY_STATE, ScreenCaptureState.STOPPED.name).orEmpty())
        }.getOrDefault(ScreenCaptureState.FAILED)
        return ScreenCaptureSnapshot(
            state = state,
            sessionId = preferences.getString(KEY_SESSION_ID, null),
            updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
            errorCategory = preferences.getString(KEY_ERROR, null),
        )
    }

    fun write(snapshot: ScreenCaptureSnapshot) {
        preferences.edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putString(KEY_SESSION_ID, snapshot.sessionId)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtEpochMillis)
            .putString(KEY_ERROR, snapshot.errorCategory)
            .apply()
    }

    fun clearActiveSession() {
        write(
            ScreenCaptureSnapshot(
                state = ScreenCaptureState.STOPPED,
                sessionId = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val PREFERENCES = "parento_screen_capture_state"
        const val KEY_STATE = "state"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_ERROR = "error"
    }
}
