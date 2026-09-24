package com.parento.managed.command

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class DefaultCommandValidator(
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : CommandValidator {
    override fun validate(command: ManagedCommand): OperationResult<Unit> {
        if (command.commandId.isBlank() || command.commandId.length > 128) {
            return OperationResult.Failure(ManagedError.INVALID_STATE)
        }
        if (command.commandType.isBlank() || command.commandType.length > 128) {
            return OperationResult.Failure(ManagedError.INVALID_STATE)
        }
        if (command.version < 1 || command.expiresAtEpochMillis <= nowEpochMillis()) {
            return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
        }
        return OperationResult.Success(Unit)
    }
}

/**
 * Phase 4.1 deliberately has no remote authorization source.
 * Commands therefore fail closed until a later phase supplies an authorized command context.
 */
class DenyByDefaultCommandAuthorization : CommandAuthorization {
    override fun authorize(command: ManagedCommand): OperationResult<Unit> =
        OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
}
