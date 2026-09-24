package com.parento.managed

import com.parento.managed.command.DefaultCommandValidator
import com.parento.managed.command.DenyByDefaultCommandAuthorization
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSafetyTest {
    private val command = ManagedCommand(
        commandId = "command-1",
        commandType = "FUTURE_OPERATION",
        version = 1,
        expiresAtEpochMillis = 2_000L,
    )

    @Test
    fun expiredCommandIsRejected() {
        val result = DefaultCommandValidator { 2_001L }.validate(command)
        assertEquals(
            ManagedError.AUTHORIZATION_FAILURE,
            (result as OperationResult.Failure).error,
        )
    }

    @Test
    fun authorizationFailsClosedUntilLaterPhaseProvidesAuthorization() {
        val result = DenyByDefaultCommandAuthorization().authorize(command)
        assertEquals(
            ManagedError.AUTHORIZATION_FAILURE,
            (result as OperationResult.Failure).error,
        )
    }
}
