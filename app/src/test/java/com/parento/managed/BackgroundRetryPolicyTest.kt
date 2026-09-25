package com.parento.managed

import com.parento.managed.background.BackgroundRetryPolicy
import com.parento.managed.background.BackgroundWorkFailureClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRetryPolicyTest {
    @Test
    fun transientFailureRetriesUntilBoundedLimit() {
        val policy = BackgroundRetryPolicy(3)
        assertTrue(policy.decide(BackgroundWorkFailureClass.TRANSIENT, 0).retry)
        assertTrue(policy.decide(BackgroundWorkFailureClass.TRANSIENT, 2).retry)
        assertFalse(policy.decide(BackgroundWorkFailureClass.TRANSIENT, 3).retry)
    }

    @Test
    fun nonTransientFailuresDoNotRetry() {
        val policy = BackgroundRetryPolicy()
        assertFalse(policy.decide(BackgroundWorkFailureClass.PERMANENT, 0).retry)
        assertFalse(policy.decide(BackgroundWorkFailureClass.CONFIGURATION, 0).retry)
        assertFalse(policy.decide(BackgroundWorkFailureClass.AUTHORIZATION, 0).retry)
    }
}
