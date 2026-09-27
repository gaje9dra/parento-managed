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
import com.parento.managed.network.NetworkPolicySyncCommandHandler
import com.parento.managed.network.NetworkPolicyStatusCommandHandler
import com.parento.managed.network.NetworkPolicySynchronizer

class ManagedCommandRuntime(
    private val dao: ManagedCommandDao,
    private val sessionManager: DeviceCommunicationSessionManager,
    screenShareManager: ScreenShareManager,
    applicationInventorySync: com.parento.managed.application.ApplicationInventorySync,
    localStateRepository: com.parento.managed.data.LocalStateRepository,
    networkPolicySynchronizer: NetworkPolicySynchronizer,
) {
    private val handlers = mapOf(
        "START_SCREEN_SHARE" to ScreenShareStartCommandHandler(screenShareManager),
        "STOP_SCREEN_SHARE" to ScreenShareStopCommandHandler(screenShareManager),
        "REQUEST_APPLICATION_INVENTORY" to ApplicationInventoryCommandHandler(applicationInventorySync),
        "SYNC_APPLICATION_POLICY" to ApplicationPolicyCommandHandler(localStateRepository),
        "SYNC_NETWORK_POLICY" to NetworkPolicySyncCommandHandler(networkPolicySynchronizer),
        "REQUEST_NETWORK_POLICY_STATUS" to NetworkPolicyStatusCommandHandler(networkPolicySynchronizer),
    )

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
