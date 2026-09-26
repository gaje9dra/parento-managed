package com.parento.managed.command

import com.parento.managed.communication.DeviceTransport
import com.parento.managed.data.local.ManagedCommandDao
import com.parento.managed.data.local.ManagedCommandEntity
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CommandProcessor(
    private val dao: ManagedCommandDao,
    private val transport: DeviceTransport,
    private val sessionTokenProvider: suspend () -> String?,
    private val validator: CommandValidator,
    private val authorization: CommandAuthorization,
    private val handlers: Map<String, CommandHandler>,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    suspend fun process(command: ManagedCommand): OperationResult<CommandResult> = mutex.withLock {
        val existing = dao.find(command.commandId)
        if (existing != null) {
            return@withLock OperationResult.Success(
                CommandResult(
                    runCatching { CommandExecutionState.valueOf(existing.state) }.getOrDefault(CommandExecutionState.REJECTED),
                    existing.resultCode ?: "DUPLICATE",
                    existing.errorCategory,
                    existing.resultMetadataJson,
                ),
            )
        }

        val inserted = dao.insert(
            ManagedCommandEntity(
                commandId = command.commandId,
                managedDeviceId = command.managedDeviceId,
                type = command.commandType,
                version = command.version,
                payloadJson = command.payloadJson,
                correlationId = command.correlationId,
                idempotencyKey = command.idempotencyKey,
                createdAtEpochMillis = command.createdAtEpochMillis,
                expiresAtEpochMillis = command.expiresAtEpochMillis,
                state = CommandExecutionState.RECEIVED.name,
                resultCode = null,
                errorCategory = null,
                resultMetadataJson = null,
                updatedAtEpochMillis = nowEpochMillis(),
            ),
        )
        if (inserted == -1L) return@withLock OperationResult.Success(CommandResult(CommandExecutionState.REJECTED, "DUPLICATE", "DUPLICATE_COMMAND"))

        if (validator.validate(command) is OperationResult.Failure) return@withLock reject(command, "INVALID_COMMAND", "VALIDATION_FAILED")
        if (authorization.authorize(command) is OperationResult.Failure) return@withLock reject(command, "UNSUPPORTED_COMMAND", "AUTHORIZATION_DENIED")
        if (nowEpochMillis() > command.expiresAtEpochMillis) return@withLock reject(command, "EXPIRED", "COMMAND_EXPIRED", CommandExecutionState.EXPIRED)

        val token = sessionTokenProvider() ?: return@withLock reject(command, "SESSION_UNAVAILABLE", "SESSION_INVALID")
        dao.updateState(command.commandId, CommandExecutionState.ACKNOWLEDGED.name, null, null, null, nowEpochMillis())
        when (transport.acknowledge(token, command.commandId)) {
            is OperationResult.Failure -> return@withLock reject(command, "ACK_FAILED", "TRANSPORT_FAILURE", CommandExecutionState.FAILED)
            is OperationResult.Success -> Unit
        }

        dao.updateState(command.commandId, CommandExecutionState.RUNNING.name, null, null, null, nowEpochMillis())
        when (transport.start(token, command.commandId)) {
            is OperationResult.Failure -> return@withLock reject(command, "START_FAILED", "TRANSPORT_FAILURE", CommandExecutionState.FAILED)
            is OperationResult.Success -> Unit
        }

        val handler = handlers[command.commandType] ?: return@withLock reject(command, "UNSUPPORTED_COMMAND", "UNSUPPORTED_COMMAND", CommandExecutionState.FAILED)
        val result = when (val handled = handler.handle(command)) {
            is OperationResult.Failure -> CommandResult(CommandExecutionState.FAILED, "HANDLER_FAILED", "HANDLER_FAILURE")
            is OperationResult.Success -> handled.value
        }

        dao.updateState(command.commandId, result.state.name, result.resultCode, result.errorCategory, result.safeMetadataJson, nowEpochMillis())
        if (result.state == CommandExecutionState.RUNNING ||
            result.state == CommandExecutionState.SUCCEEDED ||
            result.state == CommandExecutionState.FAILED
        ) {
            transport.result(
                token,
                command.commandId,
                when (result.state) {
                    CommandExecutionState.SUCCEEDED -> "SUCCEEDED"
                    CommandExecutionState.RUNNING -> "RUNNING"
                    else -> "FAILED"
                },
                result.resultCode,
                result.errorCategory,
                result.safeMetadataJson,
            )
        }
        OperationResult.Success(result)
    }

    private suspend fun reject(command: ManagedCommand,resultCode:String,errorCategory:String,state:CommandExecutionState=CommandExecutionState.REJECTED):OperationResult<CommandResult>{
        val result=CommandResult(state,resultCode,errorCategory)
        dao.updateState(command.commandId,state.name,resultCode,errorCategory,null,nowEpochMillis())
        return OperationResult.Success(result)
    }
}
