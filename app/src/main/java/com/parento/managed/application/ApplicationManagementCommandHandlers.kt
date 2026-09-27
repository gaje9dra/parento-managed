package com.parento.managed.application

import com.parento.managed.command.CommandExecutionState
import com.parento.managed.command.CommandHandler
import com.parento.managed.command.CommandResult
import com.parento.managed.command.ManagedCommand
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class ApplicationInventoryCommandHandler(
    private val sync: ApplicationInventorySync,
) : CommandHandler {
    override val commandType: String = "REQUEST_APPLICATION_INVENTORY"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> = runBlocking {
        val payload = runCatching { JSONObject(command.payloadJson) }.getOrNull()
        if (payload == null ||
            payload.keys().asSequence().toSet() != setOf("schemaVersion") ||
            payload.optInt("schemaVersion", -1) != 1
        ) {
            return@runBlocking OperationResult.Success(
                CommandResult(
                    CommandExecutionState.FAILED,
                    "INVALID_PAYLOAD",
                    "INVALID_COMMAND_PAYLOAD",
                ),
            )
        }

        return@runBlocking when (val result = sync.syncNow()) {
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
    private val synchronizer: ApplicationPolicySynchronizer,
) : CommandHandler {
    override val commandType: String = "SYNC_APPLICATION_POLICY"
    override val supportedVersion: Int = 1

    override fun handle(command: ManagedCommand): OperationResult<CommandResult> = runBlocking {
        val payload = runCatching { JSONObject(command.payloadJson) }.getOrNull()
            ?: return@runBlocking invalidPayload()

        if (payload.keys().asSequence().toSet() != setOf("policyId", "policyVersion")) {
            return@runBlocking invalidPayload()
        }

        val policyId = if (payload.isNull("policyId")) null else payload.optString("policyId", "").trim()
        val policyVersion = if (payload.isNull("policyVersion")) null else payload.optInt("policyVersion", -1)
        if ((policyId == null) != (policyVersion == null) ||
            (policyId != null && !isUuid(policyId)) ||
            (policyVersion != null && policyVersion < 1)
        ) {
            return@runBlocking invalidPayload()
        }

        when (val result = synchronizer.synchronize(policyId, policyVersion)) {
            is OperationResult.Failure ->
                OperationResult.Success(
                    CommandResult(
                        CommandExecutionState.FAILED,
                        "POLICY_SYNC_FAILED",
                        result.error.name,
                    ),
                )

            is OperationResult.Success -> {
                val outcome = result.value
                when (outcome.status) {
                    ApplicationEnforcementStatus.APPLIED ->
                        OperationResult.Success(
                            CommandResult(
                                CommandExecutionState.SUCCEEDED,
                                "POLICY_ENFORCED",
                                null,
                                JSONObject()
                                    .put("policyVersion", outcome.policyVersion)
                                    .put("enforcementState", outcome.status.name)
                                    .put("attempted", outcome.attempted)
                                    .put("succeeded", outcome.succeeded)
                                    .toString(),
                            ),
                        )

                    ApplicationEnforcementStatus.PARTIALLY_APPLIED ->
                        OperationResult.Success(
                            CommandResult(
                                CommandExecutionState.FAILED,
                                "POLICY_PARTIALLY_ENFORCED",
                                outcome.errorCode,
                                JSONObject()
                                    .put("policyVersion", outcome.policyVersion)
                                    .put("enforcementState", outcome.status.name)
                                    .put("attempted", outcome.attempted)
                                    .put("succeeded", outcome.succeeded)
                                    .put("failed", outcome.failed)
                                    .toString(),
                            ),
                        )

                    else ->
                        OperationResult.Success(
                            CommandResult(
                                CommandExecutionState.FAILED,
                                "POLICY_ENFORCEMENT_FAILED",
                                outcome.errorCode ?: "ENFORCEMENT_FAILED",
                                JSONObject()
                                    .put("policyVersion", outcome.policyVersion)
                                    .put("enforcementState", outcome.status.name)
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
        runCatching { java.util.UUID.fromString(value) }.isSuccess
}
