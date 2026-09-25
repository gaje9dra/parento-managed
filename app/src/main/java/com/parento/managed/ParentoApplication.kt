package com.parento.managed

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.parento.managed.background.WorkManagerBackgroundWorkScheduler
import com.parento.managed.communication.AndroidDeviceCredentialStore
import com.parento.managed.communication.AndroidDeviceSessionStore
import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.communication.DeviceCredentialStore
import com.parento.managed.communication.DeviceTransport
import com.parento.managed.communication.HttpsDeviceTransport
import com.parento.managed.config.ManagedApplicationConfig
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.data.LocalStateService
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.device.AndroidDeviceManagementManager
import com.parento.managed.device.AndroidDeviceManagementPlatform
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.enrollment.AndroidSecureEnrollmentStore
import com.parento.managed.enrollment.EnrollmentRepository
import com.parento.managed.enrollment.HttpEnrollmentApiClient
import com.parento.managed.lifecycle.AndroidConnectivityObserver
import com.parento.managed.lifecycle.ApplicationInitializationState
import com.parento.managed.lifecycle.ApplicationLifecycleObserver
import com.parento.managed.lifecycle.ConnectivityObserver
import com.parento.managed.lifecycle.InitializationStatus
import com.parento.managed.lifecycle.LocalManagementStateRepository
import com.parento.managed.lifecycle.ManagedDeviceInitializer
import com.parento.managed.lifecycle.ManagedStartupOrchestrator
import com.parento.managed.lifecycle.ManagementState
import com.parento.managed.logging.AndroidManagedLogger
import com.parento.managed.logging.LogLevel
import com.parento.managed.logging.ManagedLogger
import com.parento.managed.policy.DefaultPolicyEngine
import com.parento.managed.monitoring.*
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

    private val _managementInitialization = MutableStateFlow<OperationResult<ManagementState>?>(null)
    val managementInitialization: StateFlow<OperationResult<ManagementState>?> = _managementInitialization.asStateFlow()

    private val _initializationState = MutableStateFlow(ApplicationInitializationState())
    val initializationState: StateFlow<ApplicationInitializationState> = _initializationState.asStateFlow()

    private lateinit var logger: ManagedLogger
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var startupOrchestrator: ManagedStartupOrchestrator
    private lateinit var monitoringScheduler: MonitoringScheduler

    val localStateRepository: LocalStateRepository by lazy {
        RoomLocalStateRepository(LocalDatabaseProvider.get().localApplicationStateDao())
    }

    private val deviceCredentialStore: DeviceCredentialStore by lazy {
        AndroidDeviceCredentialStore(this)
    }

    val deviceCommunicationSessionManager: DeviceCommunicationSessionManager by lazy {
        val config = ManagedApplicationConfig.get()
        DeviceCommunicationSessionManager(
            localStateRepository = localStateRepository,
            credentialStore = deviceCredentialStore,
            sessionStore = AndroidDeviceSessionStore(this),
            transport = HttpsDeviceTransport(
                baseUrl = config.backendBaseUrl,
                requireHttps = config.security.requireHttps,
            ),
        )
    }

    val connectionState: StateFlow<com.parento.managed.domain.ConnectionState>
        get() = deviceCommunicationSessionManager.state

    val enrollmentRepository: EnrollmentRepository by lazy {
        val config = ManagedApplicationConfig.get()
        EnrollmentRepository(
            apiClient = HttpEnrollmentApiClient(
                baseUrl = config.backendBaseUrl,
                requireHttps = config.security.requireHttps,
            ),
            localStateRepository = localStateRepository,
            secureStore = AndroidSecureEnrollmentStore(this),
            deviceCredentialStore = deviceCredentialStore,
        )
    }

    val localStateService: LocalStateService by lazy {
        LocalStateService(localStateRepository)
    }

    override fun onCreate() {
        super.onCreate()

        val configResult = runCatching { ManagedApplicationConfig.initialize() }
        if (configResult.isFailure) {
            publishInitializationFailure("configuration", ManagedError.UNKNOWN)
            android.util.Log.e("ParentoManaged", "Application configuration initialization failed.")
            return
        }
        logger = AndroidManagedLogger(ManagedApplicationConfig.get())

        val databaseResult = runCatching { LocalDatabaseProvider.initialize(this) }
        if (databaseResult.isFailure) {
            publishInitializationFailure("database", ManagedError.STORAGE_FAILURE)
            logger.log(LogLevel.ERROR, "Local database initialization failed.")
            return
        }

        val managementStateRepository = LocalManagementStateRepository(localStateRepository)
        val initializer = ManagedDeviceInitializer(
            localStateRepository = localStateRepository,
            managementStateRepository = managementStateRepository,
            deviceManagementManager = AndroidDeviceManagementManager(AndroidDeviceManagementPlatform(this)),
            policyEngine = DefaultPolicyEngine(),
        )
        startupOrchestrator = ManagedStartupOrchestrator(initializer)

        WorkManagerBackgroundWorkScheduler(this)
        monitoringScheduler = MonitoringScheduler(this)
        monitoringScheduler.schedule()

        connectivityObserver = AndroidConnectivityObserver(
            context = this,
            onChanged = { availability ->
                logger.log(LogLevel.DEBUG, "Network availability observed: $availability.")
            },
            logger = logger,
        )
        connectivityObserver.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            ApplicationLifecycleObserver(
                onForeground = {
                    if (_initializationState.value.status == InitializationStatus.READY) {
                        applicationScope.launch {
                            refreshManagementState()
                            deviceCommunicationSessionManager.heartbeat()
                        }
                    }
                },
                logger = logger,
            ),
        )

        initialize()
        logger.log(LogLevel.INFO, "Parento Managed startup orchestration started.")
    }

    private fun initialize() {
        _initializationState.value = ApplicationInitializationState(status = InitializationStatus.INITIALIZING)
        applicationScope.launch {
            val result = runCatching {
                when (val local = localStateService.initialize()) {
                    is OperationResult.Failure -> local
                    is OperationResult.Success -> startupOrchestrator.initialize()
                }
            }.getOrElse { OperationResult.Failure(ManagedError.UNKNOWN) }

            _managementInitialization.value = result
            _initializationState.value = when (result) {
                is OperationResult.Success -> ApplicationInitializationState(status = InitializationStatus.READY)
                is OperationResult.Failure -> ApplicationInitializationState(
                    status = InitializationStatus.FAILED,
                    failedSubsystem = "managed-device-initialization",
                    error = result.error,
                )
            }

            if (result is OperationResult.Success) {
                deviceCommunicationSessionManager.recover()
                if (result.value.enrollmentState == com.parento.managed.domain.EnrollmentState.ENROLLED) {
                    deviceCommunicationSessionManager.connect()
                }
                logger.log(LogLevel.INFO, "Managed-device initialization completed.")
            } else {
                logger.log(LogLevel.ERROR, "Managed-device initialization failed.")
            }
        }
    }

    fun retryInitialization() {
        if (_initializationState.value.status == InitializationStatus.INITIALIZING) return
        initialize()
    }

    private suspend fun refreshManagementState() {
        val result = startupOrchestrator.refresh()
        _managementInitialization.value = result
    }

    private fun publishInitializationFailure(subsystem: String, error: ManagedError) {
        _managementInitialization.value = OperationResult.Failure(error)
        _initializationState.value = ApplicationInitializationState(
            status = InitializationStatus.FAILED,
            failedSubsystem = subsystem,
            error = error,
        )
    }

    override fun onTerminate() {
        if (::connectivityObserver.isInitialized) connectivityObserver.stop()
        applicationScope.cancel()
        if (databaseIsInitialized()) LocalDatabaseProvider.get().close()
        super.onTerminate()
    }

    private fun databaseIsInitialized(): Boolean = runCatching { LocalDatabaseProvider.get() }.isSuccess
}
