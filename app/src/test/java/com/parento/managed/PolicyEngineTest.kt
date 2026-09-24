package com.parento.managed

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.policy.DefaultPolicyEngine
import com.parento.managed.policy.PolicyDefinition
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    private val engine = DefaultPolicyEngine()

    @Test
    fun validPolicyBoundaryIsAccepted() {
        val result = engine.validatePolicy(
            PolicyDefinition(
                policyId = "status",
                policyType = "STATUS",
                version = 1L,
                updatedAtEpochMillis = 1L,
            ),
        )
        assertTrue(result is OperationResult.Success)
    }

    @Test
    fun invalidPolicyBoundaryIsRejected() {
        val result = engine.validatePolicy(PolicyDefinition(policyId = ""))
        assertTrue(result is OperationResult.Failure)
        assertTrue((result as OperationResult.Failure).error == ManagedError.POLICY_FAILURE)
    }
}
