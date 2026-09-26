package com.parento.managed.application

import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.CommandHandler
import com.parento.managed.command.CommandResult
import com.parento.managed.command.ManagedCommand
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.domain.OperationResult
import java.util.UUID
import org.json.JSONObject

class ApplicationInventoryCommandHandler(
    private val sync: ApplicationInventorySync,
) : CommandHandler {
    override val commandType: String = "REQUEST_APPLICATION_INVENTORY"
    override val supportedVersion: Int = 1

    override suspend fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        if (command.payloadJson.trim() != "{}") {
            return OperationResult.Success(
                CommandResult(
                    CommandExecutionState.FAILED,
                    "INVALID_PAYLOAD",
                    "INVALID_COMMAND_PAYLOAD",
                ),
            )
        }

        return when (val result = sync.syncNow()) {
            is OperationResult.Success ->
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.SUCCEEDED,
                        "INVENTORY_SYNCHRONIZED",
                        null,
                        JSONObject()
                            .put("applicationCount", result.value.applicationCount)
                            .put("synchronizationId", result.value.synchronizationId)
                            .toString(),
                    ),
                )
            is OperationResult.Failure ->
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.FAILED,
                        "INVENTORY_SYNC_FAILED",
                        result.error.name,
                    ),
                )
        }
    }
}

class ApplicationPolicyCommandHandler(
    private val localStateRepository: LocalStateRepository,
) : CommandHandler {
    override val commandType: String = "SYNC_APPLICATION_POLICY"
    override val supportedVersion: Int = 1

    override suspend fun handle(command: ManagedCommand): OperationResult<CommandResult> {
        val payload = runCatching { JSONObject(command.payloadJson) }.getOrNull()
            ?: return invalidPayload()

        val keys = payload.keys().asSequence().toSet()
        if (keys != setOf("policyId", "policyVersion")) return invalidPayload()

        val policyId = payload.optString("policyId", "").trim()
        val policyVersion = payload.optInt("policyVersion", -1)
        if (!isUuid(policyId) || policyVersion < 1) return invalidPayload()

        val current = when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> {
                return OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.FAILED,
                        "LOCAL_STATE_UNAVAILABLE",
                        result.error.name,
                    ),
                )
            }
            is OperationResult.Success -> result.value
        } ?: return OperationResult.Success(
            CommandResult(
                CommandExecutionState.FAILED,
                "LOCAL_STATE_UNAVAILABLE",
                "STORAGE_FAILURE",
            ),
        )

        val acceptedVersion = current.acceptedApplicationPolicyVersion ?: 0
        val currentPolicyId = current.desiredApplicationPolicyId
        when {
            policyVersion < acceptedVersion -> {
                localStateRepository.updateApplicationPolicySyncStatus(
                    ApplicationPolicySyncStatus.STALE,
                )
                localStateRepository.updateApplicationEnforcementStatus(
                    ApplicationEnforcementStatus.STALE,
                )
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.SUCCEEDED,
                        "STALE_POLICY",
                        null,
                    ),
                )
            }
            policyVersion == acceptedVersion && currentPolicyId != null && currentPolicyId != policyId ->
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.FAILED,
                        "POLICY_VERSION_CONFLICT",
                        "INVALID_POLICY_VERSION",
                    ),
                )
            policyVersion == acceptedVersion && currentPolicyId == policyId ->
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.SUCCEEDED,
                        "POLICY_ALREADY_ACCEPTED",
                        null,
                        JSONObject()
                            .put("policyVersion", policyVersion)
                            .put("enforcementState", ApplicationEnforcementStatus.PENDING.name)
                            .put("policyRulesAvailable", false)
                            .toString(),
                    ),
                )
            else -> {
                val stored = localStateRepository.updateApplicationPolicyReference(
                    policyId,
                    policyVersion,
                    ApplicationPolicySyncStatus.PENDING,
                )
                if (stored is OperationResult.Failure) {
                    OperationResult.Success(
                        CommandResult(
                            CommandExecutionState.FAILED,
                            "POLICY_STATE_UPDATE_FAILED",
                            stored.error.name,
                        ),
                    )
                } else {
                    OperationResult.Success(
                        CommandResult(
                            CommandExecutionState.SUCCEEDED,
                            "POLICY_REFERENCE_ACCEPTED",
                            null,
                            JSONObject()
                                .put("policyVersion", policyVersion)
                                .put("enforcementState", ApplicationEnforcementStatus.PENDING.name)
                                .put("policyRulesAvailable", false)
                                .toString(),
                        ),
                    )
                }
            }
        }
    }

    private fun invalidPayload(): OperationResult<CommandResult> =
        OperationResult.Success(
            CommandResult(
                CommandExecutionState.FAILED,
                "INVALID_PAYLOAD",
                "INVALID_COMMAND_PAYLOAD",
            ),
        )

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess
}
