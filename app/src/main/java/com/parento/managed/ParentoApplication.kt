package com.parento.managed

import android.app.Application
import com.parento.managed.config.ManagedApplicationConfig
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.logging.AndroidManagedLogger
import com.parento.managed.logging.LogLevel

class ParentoApplication : Application() {
    val localStateRepository: LocalStateRepository by lazy {
        RoomLocalStateRepository(LocalDatabaseProvider.get().localApplicationStateDao())
    }

    override fun onCreate() {
        super.onCreate()

        ManagedApplicationConfig.initialize()
        LocalDatabaseProvider.initialize(this)

        val config = ManagedApplicationConfig.get()
        AndroidManagedLogger(config).log(
            LogLevel.INFO,
            "Parento Managed initialized.",
        )
    }
}
