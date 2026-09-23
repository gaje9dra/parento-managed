package com.parento.managed

import android.app.Application
import com.parento.managed.config.ManagedApplicationConfig
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.LocalStateService
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.domain.OperationResult
import com.parento.managed.logging.AndroidManagedLogger
import com.parento.managed.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ParentoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val localStateRepository: LocalStateRepository by lazy {
        RoomLocalStateRepository(LocalDatabaseProvider.get().localApplicationStateDao())
    }

    val localStateService: LocalStateService by lazy {
        LocalStateService(localStateRepository)
    }

    override fun onCreate() {
        super.onCreate()

        ManagedApplicationConfig.initialize()
        LocalDatabaseProvider.initialize(this)

        val logger = AndroidManagedLogger(ManagedApplicationConfig.get())

        applicationScope.launch {
            when (localStateService.initialize()) {
                is OperationResult.Success ->
                    logger.log(LogLevel.INFO, "Parento Managed local state initialized.")
                is OperationResult.Failure ->
                    logger.log(LogLevel.ERROR, "Parento Managed local state initialization failed.")
            }
        }

        logger.log(LogLevel.INFO, "Parento Managed initialized.")
    }

    override fun onTerminate() {
        applicationScope.cancel()
        LocalDatabaseProvider.get().close()
        super.onTerminate()
    }
