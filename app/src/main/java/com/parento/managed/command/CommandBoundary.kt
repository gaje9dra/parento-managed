package com.parento.managed.command

import com.parento.managed.domain.OperationResult

enum class CommandExecutionState {
    RECEIVED, VALIDATED, AUTHORIZED, EXECUTING, SUCCEEDED, FAILED, EXPIRED, REJECTED,
}

data class ManagedCommand(
    val commandId: String,
    val commandType: String,
    val version: Int,
    val expiresAtEpochMillis: Long,
)

interface CommandValidator { fun validate(command: ManagedCommand): OperationResult<Unit> }
interface CommandAuthorization { fun authorize(command: ManagedCommand): OperationResult<Unit> }
interface CommandExecutor { fun execute(command: ManagedCommand): OperationResult<CommandExecutionState> }
