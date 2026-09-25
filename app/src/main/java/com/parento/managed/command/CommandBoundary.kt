package com.parento.managed.command

import com.parento.managed.domain.OperationResult

enum class CommandExecutionState {
    RECEIVED,
    VALIDATING,
    ACKNOWLEDGED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    REJECTED,
}

data class ManagedCommand(
    val commandId: String,
    val managedDeviceId: String,
    val commandType: String,
    val version: Int,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val correlationId: String?,
    val idempotencyKey: String?,
)

interface CommandValidator {
    fun validate(command: ManagedCommand): OperationResult<Unit>
}

interface CommandAuthorization {
    fun authorize(command: ManagedCommand): OperationResult<Unit>
}

interface CommandHandler {
    val commandType: String
    val supportedVersion: Int
    fun handle(command: ManagedCommand): OperationResult<CommandResult>
}

data class CommandResult(
    val state: CommandExecutionState,
    val resultCode: String?,
    val errorCategory: String?,
    val safeMetadataJson: String? = null,
)
