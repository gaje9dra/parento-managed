package com.parento.managed

import com.parento.managed.domain.EnrollmentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentStateTest {
    @Test
    fun allowsOnlyValidTransitions() {
        assertTrue(EnrollmentState.UNENROLLED.canTransitionTo(EnrollmentState.ENROLLING))
        assertTrue(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.ENROLLED))
        assertTrue(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.UNENROLLED))
        assertTrue(EnrollmentState.ENROLLED.canTransitionTo(EnrollmentState.REVOKED))
        assertFalse(EnrollmentState.UNENROLLED.canTransitionTo(EnrollmentState.ENROLLED))
        assertFalse(EnrollmentState.ERROR.canTransitionTo(EnrollmentState.ENROLLED))
        assertFalse(EnrollmentState.REVOKED.canTransitionTo(EnrollmentState.ENROLLING))
    }
}
