package com.parento.managed.logging

import android.util.Log
import com.parento.managed.config.AppConfig

class AndroidManagedLogger(
    private val config: AppConfig,
) : ManagedLogger {
    override fun log(level: LogLevel, message: String) {
        if (!config.logging.enabled || level.ordinal < config.logging.minimumLevel.ordinal) {
            return
        }

        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }

    private companion object {
        const val TAG = "ParentoManaged"
    }
}
