package com.parento.managed.screenshare

import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.CommandHandler
import com.parento.managed.command.CommandResult
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.OperationResult
import org.json.JSONObject
import java.util.UUID

class ScreenShareStartCommandHandler(
    private val manager: ScreenShareManager,
) : CommandHandler {
    override val commandType: String = "START_SCREEN_SHARE"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val sessionId = screenSessionId(command) ?: return OperationResult.Success(
            CommandResult(CommandExecutionState.FAILED, "INVALID_SESSION", "INVALID_SESSION"),
        )
        val current = manager.state.value
        if (current.state == ScreenCaptureState.ACTIVE && current.sessionId == sessionId) {
            return OperationResult.Success(
                CommandResult(CommandExecutionState.SUCCEEDED, "ALREADY_ACTIVE", null),
            )
        }
        return if (manager.requestAuthorizationFromCommand(sessionId).isSuccess) {
            OperationResult.Success(
                CommandResult(
                    CommandExecutionState.RUNNING,
                    "AUTHORIZATION_REQUIRED",
                    null,
                    JSONObject()
                        .put("screenSessionId", sessionId)
                        .put("state", ScreenCaptureState.AUTHORIZATION_REQUIRED.name)
                        .toString(),
                ),
            )
        } else {
            OperationResult.Success(
                CommandResult(
                    CommandExecutionState.FAILED,
                    "AUTHORIZATION_REQUIRED",
                    "AUTHORIZATION_FAILURE",
                ),
            )
        }
    }

    private fun screenSessionId(command: ManagedCommand): String? =
        runCatching {
            val payload = JSONObject(command.payloadJson)
            if (payload.length() != 1 || !payload.has("screenSessionId")) return null
            val id = payload.getString("screenSessionId").trim()
            UUID.fromString(id)
            id
        }.getOrNull()
}

class ScreenShareStopCommandHandler(
    private val manager: ScreenShareManager,
) : CommandHandler {
    override val commandType: String = "STOP_SCREEN_SHARE"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val sessionId = runCatching {
            val payload = JSONObject(command.payloadJson)
            if (payload.length() != 1 || !payload.has("screenSessionId")) return@runCatching null
            val id = payload.getString("screenSessionId").trim()
            UUID.fromString(id)
            id
        }.getOrNull()
            ?: return OperationResult.Success(
                CommandResult(CommandExecutionState.FAILED, "INVALID_SESSION", "INVALID_SESSION"),
            )

        return manager.stop(sessionId).fold(
            onSuccess = {
                OperationResult.Success(
                    CommandResult(CommandExecutionState.SUCCEEDED, "STOP_REQUESTED", null),
                )
            },
            onFailure = {
                OperationResult.Success(
                    CommandResult(CommandExecutionState.FAILED, "STOP_FAILED", "CAPTURE_STOP_FAILED"),
                )
            },
        )
    }
}
