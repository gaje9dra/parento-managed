package com.parento.managed.policy

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class DefaultPolicyEngine : PolicyEngine {
    override fun validatePolicy(policy: PolicyDefinition): OperationResult<Unit> {
        if (policy.policyId.isBlank() || policy.policyId.length > 128) {
            return OperationResult.Failure(ManagedError.POLICY_FAILURE)
        }
        if (policy.policyType.isBlank() || policy.version < 1L || policy.updatedAtEpochMillis < 0L) {
            return OperationResult.Failure(ManagedError.POLICY_FAILURE)
        }
        return OperationResult.Success(Unit)
    }
}
