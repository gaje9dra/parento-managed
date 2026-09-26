package com.parento.managed.audio

import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.CommandHandler
import com.parento.managed.command.CommandResult
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.OperationResult
import org.json.JSONObject
import java.util.UUID

internal fun parseAudioSessionId(payloadJson: String): String? {
    val match = AUDIO_SESSION_PAYLOAD_PATTERN.matchEntire(payloadJson.trim()) ?: return null
    return runCatching { UUID.fromString(match.groupValues[1]); match.groupValues[1] }.getOrNull()
}

private val AUDIO_SESSION_PAYLOAD_PATTERN = Regex(
    """{"audioSessionId":"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})"}""",
)

class AudioStartCommandHandler(private val manager: AudioAccessManager) : CommandHandler {
    override val commandType = "START_AUDIO_ACCESS"
    override val supportedVersion = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val sessionId = parseAudioSessionId(command.payloadJson)
            ?: return OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "INVALID_SESSION", "INVALID_SESSION"))
        val current = manager.state.value
        if (current.state == AudioAccessState.ACTIVE && current.sessionId == sessionId) {
            return OperationResult.Success(CommandResult(CommandExecutionState.SUCCEEDED, "ALREADY_ACTIVE", null))
        }
        if (!manager.microphonePermissionGranted()) {
            manager.requestPermissionFromCommand(sessionId)
            return OperationResult.Success(
                CommandResult(
                    CommandExecutionState.FAILED,
                    "MICROPHONE_PERMISSION_REQUIRED",
                    "PERMISSION_REQUIRED",
                    metadata(sessionId, AudioAccessState.PERMISSION_REQUIRED),
                ),
            )
        }
        if (!manager.mediaTransportAvailable()) {
            return OperationResult.Success(
                CommandResult(
                    CommandExecutionState.FAILED,
                    "AUDIO_TRANSPORT_UNAVAILABLE",
                    "TRANSPORT_UNAVAILABLE",
                    metadata(sessionId, AudioAccessState.FAILED),
                ),
            )
        }
        return manager.startFromAuthorizedCommand(sessionId).fold(
            onSuccess = {
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.RUNNING,
                        "START_REQUESTED",
                        null,
                        metadata(sessionId, AudioAccessState.STARTING),
                    ),
                )
            },
            onFailure = {
                OperationResult.Success(
                    CommandResult(CommandExecutionState.FAILED, "START_FAILED", "CAPTURE_START_FAILED", metadata(sessionId, AudioAccessState.FAILED)),
                )
            },
        )
    }

    private fun metadata(sessionId: String, state: AudioAccessState) =
        JSONObject().put("audioSessionId", sessionId).put("state", state.name).toString()
}

class AudioStopCommandHandler(private val manager: AudioAccessManager) : CommandHandler {
    override val commandType = "STOP_AUDIO_ACCESS"
    override val supportedVersion = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val sessionId = parseAudioSessionId(command.payloadJson)
            ?: return OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "INVALID_SESSION", "INVALID_SESSION"))

        return manager.stop(sessionId).fold(
            onSuccess = {
                OperationResult.Success(CommandResult(CommandExecutionState.SUCCEEDED, "STOP_REQUESTED", null))
            },
            onFailure = {
                OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "STOP_FAILED", "CAPTURE_STOP_FAILED"))
            },
        )
    }
}
