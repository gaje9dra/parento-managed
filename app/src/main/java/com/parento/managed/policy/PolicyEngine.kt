package com.parento.managed.policy

import com.parento.managed.domain.OperationResult

interface PolicyEngine {
    fun validatePolicy(policy: PolicyDefinition): OperationResult<Unit>
}

data class PolicyDefinition(
    val policyId: String,
)
