package com.parento.managed.policy

data class PolicyDefinition(
    val policyId: String,
    val policyType: String = "UNSPECIFIED",
    val enabled: Boolean = false,
    val configuration: Map<String, String> = emptyMap(),
    val version: Long = 1L,
    val updatedAtEpochMillis: Long = 0L,
    val source: String = "LOCAL",
)

interface PolicyEngine {
    fun validatePolicy(policy: PolicyDefinition): com.parento.managed.domain.OperationResult<Unit>
}
