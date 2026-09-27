package com.parento.managed.network

import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.CommandHandler
import com.parento.managed.command.CommandResult
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class NetworkPolicySyncCommandHandler(
    private val synchronizer: NetworkPolicySynchronizer,
) : CommandHandler {
    override val commandType: String = "SYNC_NETWORK_POLICY"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> = runBlocking {
        val payload = runCatching { JSONObject(command.payloadJson) }.getOrNull()
            ?: return@runBlocking invalid("INVALID_PAYLOAD")
        if (payload.keys().asSequence().toSet() != setOf("policyId", "policyVersion")) {
            return@runBlocking invalid("INVALID_PAYLOAD")
        }

        val policyId = if (payload.isNull("policyId")) null else payload.optString("policyId", "").takeIf { it.isNotBlank() }
        val policyVersion = if (payload.isNull("policyVersion")) null else payload.optLong("policyVersion", -1L).takeIf { it > 0L }
        if ((policyId == null) != (policyVersion == null)) return@runBlocking invalid("INVALID_POLICY_VERSION")

        when (val result = synchronizer.synchronize(policyId, policyVersion)) {
            is OperationResult.Failure ->
                OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "NETWORK_POLICY_SYNC_FAILED", result.error.name))
            is OperationResult.Success ->
                OperationResult.Success(
                    CommandResult(
                        if (result.value.status == NetworkEnforcementStatus.FAILED) CommandExecutionState.FAILED else CommandExecutionState.SUCCEEDED,
                        result.value.status.name,
                        result.value.errorCode,
                    ),
                )
        }
    }

    private fun invalid(code: String) =
        OperationResult.Success(CommandResult(CommandExecutionState.FAILED, code, "INVALID_COMMAND_PAYLOAD"))
}

class NetworkPolicyStatusCommandHandler(
    private val synchronizer: NetworkPolicySynchronizer,
) : CommandHandler {
    override val commandType: String = "REQUEST_NETWORK_POLICY_STATUS"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> = runBlocking {
        val payload = runCatching { JSONObject(command.payloadJson) }.getOrNull()
            ?: return@runBlocking invalid()
        if (payload.keys().asSequence().toSet() != setOf("schemaVersion") || payload.optInt("schemaVersion", -1) != 1) {
            return@runBlocking invalid()
        }
        when (val result = synchronizer.synchronize(null, null)) {
            is OperationResult.Failure ->
                OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "NETWORK_POLICY_STATUS_FAILED", result.error.name))
            is OperationResult.Success ->
                OperationResult.Success(CommandResult(CommandExecutionState.SUCCEEDED, result.value.status.name, result.value.errorCode))
        }
    }

    private fun invalid() =
        OperationResult.Success(CommandResult(CommandExecutionState.FAILED, "INVALID_PAYLOAD", "INVALID_COMMAND_PAYLOAD"))
}
