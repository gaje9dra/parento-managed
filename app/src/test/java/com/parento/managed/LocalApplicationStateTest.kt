package com.parento.managed

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalApplicationStateTest {
    @Test
    fun defaultState_isSafeAndUnenrolled() {
        val state = LocalApplicationState()

        assertEquals(1, state.stateVersion)
        assertEquals(false, state.initialized)
        assertEquals(EnrollmentState.UNENROLLED, state.enrollmentState)
        assertEquals(ConnectionState.UNKNOWN, state.connectionState)
    }
}
