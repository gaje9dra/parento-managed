package com.parento.managed

import android.app.Application
import com.parento.managed.config.ManagedApplicationConfig
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.LocalStateService
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.device.AndroidDeviceManagementManager
import com.parento.managed.device.AndroidDeviceManagementPlatform
import com.parento.managed.lifecycle.LocalManagementStateRepository
import com.parento.managed.lifecycle.ManagedDeviceInitializer
import com.parento.managed.lifecycle.ManagementState
import com.parento.managed.domain.OperationResult
import com.parento.managed.logging.AndroidManagedLogger
import com.parento.managed.logging.LogLevel
import com.parento.managed.policy.DefaultPolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ParentoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _managementInitialization =
        MutableStateFlow<OperationResult<ManagementState>?>(null)

    val managementInitialization: StateFlow<OperationResult<ManagementState>?> =
        _managementInitialization.asStateFlow()

    val localStateRepository: LocalStateRepository by lazy {
        RoomLocalStateRepository(LocalDatabaseProvider.get().localApplicationStateDao())
    }

    val localStateService: LocalStateService by lazy {
        LocalStateService(localStateRepository)
    }

    private val managementStateRepository: LocalManagementStateRepository by lazy {
        LocalManagementStateRepository(localStateRepository)
    }

    private val deviceManagementInitializer: ManagedDeviceInitializer by lazy {
        ManagedDeviceInitializer(
            localStateRepository = localStateRepository,
            managementStateRepository = managementStateRepository,
            deviceManagementManager = AndroidDeviceManagementManager(
                AndroidDeviceManagementPlatform(this),
            ),
            policyEngine = DefaultPolicyEngine(),
        )
    }

    override fun onCreate() {
        super.onCreate()

        ManagedApplicationConfig.initialize()
        LocalDatabaseProvider.initialize(this)

        val logger = AndroidManagedLogger(ManagedApplicationConfig.get())

        applicationScope.launch {
            val result = when (val local = localStateService.initialize()) {
                is OperationResult.Failure -> local
                is OperationResult.Success -> deviceManagementInitializer.initialize()
            }

            _managementInitialization.value = result

            when (result) {
                is OperationResult.Failure ->
                    logger.log(
                        LogLevel.ERROR,
                        "Managed-device platform initialization failed.",
                    )

                is OperationResult.Success ->
                    logger.log(
                        LogLevel.INFO,
                        "Managed-device platform state initialized.",
                    )
            }
        }

        logger.log(LogLevel.INFO, "Parento Managed initialized.")
    }

    override fun onTerminate() {
        applicationScope.cancel()
        LocalDatabaseProvider.get().close()
        super.onTerminate()
    }
}
