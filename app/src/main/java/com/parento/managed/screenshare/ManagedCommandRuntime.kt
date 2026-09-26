package com.parento.managed.screenshare

import com.parento.managed.command.AllowlistedCommandAuthorization
import com.parento.managed.command.CommandProcessor
import com.parento.managed.command.CommandResult
import com.parento.managed.command.DefaultCommandValidator
import com.parento.managed.command.ManagedCommand
import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.communication.TransportCommand
import com.parento.managed.data.local.ManagedCommandDao
import com.parento.managed.domain.OperationResult

class ManagedCommandRuntime(
    private val dao: ManagedCommandDao,
    private val sessionManager: DeviceCommunicationSessionManager,
    screenShareManager: ScreenShareManager,
) {
    private val handlers = mapOf(
        "START_SCREEN_SHARE" to ScreenShareStartCommandHandler(screenShareManager),
        "STOP_SCREEN_SHARE" to ScreenShareStopCommandHandler(screenShareManager),
    )

    suspend fun processNextCommand(): OperationResult<CommandResult?> =
        when (val received = sessionManager.receiveNextCommand()) {
            is OperationResult.Failure -> received
            is OperationResult.Success -> received.value?.let { process(it) } ?: OperationResult.Success(null)
        }

    suspend fun process(command: TransportCommand): OperationResult<CommandResult> {
        val processor = CommandProcessor(
            dao = dao,
            transport = sessionManager.transportBoundary(),
            sessionTokenProvider = { sessionManager.currentSessionToken() },
            validator = DefaultCommandValidator { sessionManager.currentManagedDeviceId() },
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
