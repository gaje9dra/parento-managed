package com.parento.managed

import com.parento.managed.command.AllowlistedCommandAuthorization
import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.DefaultCommandValidator
import com.parento.managed.command.FutureCommandPlaceholderHandler
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSafetyTest {
    private val command = ManagedCommand(
        commandId = "550e8400-e29b-41d4-a716-446655440000",
        managedDeviceId = "550e8400-e29b-41d4-a716-446655440001",
        commandType = "FUTURE_COMMAND",
        version = 1,
        payloadJson = "{}",
        createdAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 2_000L,
        correlationId = null,
        idempotencyKey = null,
    )

    @Test fun expiredCommandIsRejected() {
        val result = DefaultCommandValidator(
            expectedManagedDeviceId = { command.managedDeviceId },
            nowEpochMillis = { 2_001L },
        ).validate(command)
        assertEquals(ManagedError.INVALID_STATE, (result as OperationResult.Failure).error)
    }

    @Test fun invalidPayloadJsonIsRejected() {
        val result = DefaultCommandValidator({ command.managedDeviceId }).validate(command.copy(payloadJson = "{"))
        assertEquals(ManagedError.INVALID_STATE, (result as OperationResult.Failure).error)
    }

    @Test fun allowlistRequiresKnownCommandAndVersion() {
        val handler = FutureCommandPlaceholderHandler()
        val authorization = AllowlistedCommandAuthorization(mapOf(handler.commandType to handler))
        assertTrue(authorization.authorize(command) is OperationResult.Success)
        assertTrue(authorization.authorize(command.copy(version = 2)) is OperationResult.Failure)
    }

    @Test fun placeholderNeverPerformsDeviceAction() {
        val result = FutureCommandPlaceholderHandler().handle(command)
        assertTrue(result is OperationResult.Success)
        assertEquals(CommandExecutionState.FAILED, (result as OperationResult.Success).value.state)
    }

    @Test fun malformedCommandTypeIsRejected() {
        val result = DefaultCommandValidator({ command.managedDeviceId }).validate(command.copy(commandType = ""))
        assertEquals(ManagedError.INVALID_STATE, (result as OperationResult.Failure).error)
    }
}
