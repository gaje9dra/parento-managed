package com.parento.managed.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentStateSecurityTest {
    @Test
    fun enrolledCannotReturnToEnrolling() {
        assertFalse(EnrollmentState.ENROLLED.canTransitionTo(EnrollmentState.ENROLLING))
    }

    @Test
    fun revokedCannotReturnToEnrolled() {
        assertFalse(EnrollmentState.REVOKED.canTransitionTo(EnrollmentState.ENROLLED))
    }

    @Test
    fun errorCanRecoverOnlyThroughFreshEnrollmentFlow() {
        assertTrue(EnrollmentState.ERROR.canTransitionTo(EnrollmentState.UNENROLLED))
        assertTrue(EnrollmentState.ERROR.canTransitionTo(EnrollmentState.ENROLLING))
        assertFalse(EnrollmentState.ERROR.canTransitionTo(EnrollmentState.ENROLLED))
    }

    @Test
    fun enrollingCanCompleteOrFailButCannotSkipToRevoked() {
        assertTrue(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.ENROLLED))
        assertTrue(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.ERROR))
        assertFalse(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.REVOKED))
    }
}
