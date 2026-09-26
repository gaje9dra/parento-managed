package com.parento.managed.command

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import org.json.JSONObject

class DefaultCommandValidator(
    private val expectedManagedDeviceId: () -> String?,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val maxPayloadChars: Int = 16 * 1024,
) : CommandValidator {
    override fun validate(command: ManagedCommand): OperationResult<Unit> {
        if (!isUuid(command.commandId) || command.commandId.length > 128) return reject()
        if (!isUuid(command.managedDeviceId) || expectedManagedDeviceId() != command.managedDeviceId) return reject()
        if (command.commandType.isBlank() || command.commandType.length > 128) return reject()
        if (command.version < 1 || command.payloadJson.length > maxPayloadChars) return reject()
        if (command.createdAtEpochMillis <= 0L || command.expiresAtEpochMillis <= command.createdAtEpochMillis) return reject()
        if (nowEpochMillis() > command.expiresAtEpochMillis) return reject()
        if (command.correlationId?.length?.let { it > 128 } == true) return reject()
        if (command.idempotencyKey?.length?.let { it > 128 } == true) return reject()
        runCatching { JSONObject(command.payloadJson) }.getOrElse { return reject() }
        return OperationResult.Success(Unit)
    }

    private fun reject(): OperationResult.Failure = OperationResult.Failure(ManagedError.INVALID_STATE)
}

class AllowlistedCommandAuthorization(
    private val handlers: Map<String, CommandHandler>,
) : CommandAuthorization {
    override fun authorize(command: ManagedCommand): OperationResult<Unit> {
        val handler = handlers[command.commandType]
        return if (handler != null && handler.supportedVersion == command.version) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
        }
    }
}

class FutureCommandPlaceholderHandler : CommandHandler {
    override val commandType: String = "FUTURE_COMMAND"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val payload = command.payloadJson.trim()
        if (payload != "{}") {
            return OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "UNSUPPORTED_PAYLOAD", "UNSUPPORTED_COMMAND"))
        }
        return OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "NOT_IMPLEMENTED", "UNSUPPORTED_COMMAND"))
    }
}

class DenyByDefaultCommandAuthorization : CommandAuthorization {
    override fun authorize(command: ManagedCommand): OperationResult<Unit> =
        OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
}

private fun isUuid(value: String): Boolean =
    runCatching { java.util.UUID.fromString(value) }.isSuccess
