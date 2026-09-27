package com.parento.managed.screenshare

import com.parento.managed.application.ApplicationInventoryCommandHandler
import com.parento.managed.application.ApplicationPolicyCommandHandler
import com.parento.managed.command.AllowlistedCommandAuthorization
import com.parento.managed.command.CommandProcessor
import com.parento.managed.command.CommandResult
import com.parento.managed.command.DefaultCommandValidator
import com.parento.managed.command.ManagedCommand
import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.communication.TransportCommand
import com.parento.managed.data.local.ManagedCommandDao
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ManagedCommandRuntime(
    private val dao: ManagedCommandDao,
    private val sessionManager: DeviceCommunicationSessionManager,
    screenShareManager: ScreenShareManager,
    applicationInventorySync: com.parento.managed.application.ApplicationInventorySync,
    localStateRepository: com.parento.managed.data.LocalStateRepository,
    applicationPolicySynchronizer: com.parento.managed.application.ApplicationPolicySynchronizer,
) {

    private val handlers = mapOf(
        "START_SCREEN_SHARE" to ScreenShareStartCommandHandler(screenShareManager),
        "STOP_SCREEN_SHARE" to ScreenShareStopCommandHandler(screenShareManager),
        "REQUEST_APPLICATION_INVENTORY" to ApplicationInventoryCommandHandler(applicationInventorySync),
        "SYNC_APPLICATION_POLICY" to ApplicationPolicyCommandHandler(applicationPolicySynchronizer),
    )

    private var streamJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val token = sessionManager.currentSessionToken()
                if (token == null) {
                    delay(5_000)
                    continue
                }
                when (val result = sessionManager.transportBoundary().streamCommands(token) { command ->
                    process(command)
                }) {
                    is OperationResult.Success -> delay(1_000)
                    is OperationResult.Failure -> delay(5_000)
                }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    suspend fun processNextCommand(): OperationResult<CommandResult?> =
        when (val received = sessionManager.receiveNextCommand()) {
            is OperationResult.Failure -> received
            is OperationResult.Success -> received.value?.let { process(it) } ?: OperationResult.Success(null)
        }

    suspend fun process(command: TransportCommand): OperationResult<CommandResult> {
        val expectedManagedDeviceId = sessionManager.currentManagedDeviceId()
        val processor = CommandProcessor(
            dao = dao,
            transport = sessionManager.transportBoundary(),
            sessionTokenProvider = { sessionManager.currentSessionToken() },
            validator = DefaultCommandValidator(expectedManagedDeviceId = { expectedManagedDeviceId }),
            authorization = AllowlistedCommandAuthorization(handlers),
            handlers = handlers,
        )
        return processor.process(
            ManagedCommand(
                commandId = command.commandId,
                managedDeviceId = command.managedDeviceId,
                commandType = command.type,
                version = command.version,
                payloadJson = command.payload,
                createdAtEpochMillis = command.createdAtEpochMillis,
                expiresAtEpochMillis = command.expiresAtEpochMillis,
                correlationId = command.correlationId,
                idempotencyKey = command.idempotencyKey,
            ),
        )
    }
}
