package com.parento.managed

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.domain.canTransitionTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStateRepositoryTest {
    @Test
    fun stateModel_defaultsToSafeInitialState() {
        val state = LocalApplicationState()

        assertEquals(1, state.stateVersion)
        assertEquals(null, state.lastSynchronizationTimestamp)
        assertEquals(false, state.initialized)
        assertEquals(EnrollmentState.UNENROLLED, state.enrollmentState)
        assertEquals(ConnectionState.UNKNOWN, state.connectionState)
        assertEquals(null, state.managedDeviceId)
    }

    @Test
    fun enrollmentTransitions_rejectSkippingRequiredLifecycleStages() {
        assertTrue(EnrollmentState.UNENROLLED.canTransitionTo(EnrollmentState.ENROLLING))
        assertTrue(EnrollmentState.ENROLLING.canTransitionTo(EnrollmentState.ENROLLED))
        assertTrue(EnrollmentState.ENROLLED.canTransitionTo(EnrollmentState.REVOKED))
        assertFalse(EnrollmentState.UNENROLLED.canTransitionTo(EnrollmentState.REVOKED))
        assertFalse(EnrollmentState.REVOKED.canTransitionTo(EnrollmentState.ENROLLED))
    }

    @Test
    fun connectionTransitions_rejectUnknownToConnected() {
        assertTrue(ConnectionState.UNKNOWN.canTransitionTo(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionState.DISCONNECTED.canTransitionTo(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.CONNECTED))
        assertTrue(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.DISCONNECTED))
        assertFalse(ConnectionState.UNKNOWN.canTransitionTo(ConnectionState.CONNECTED))
    }

    @Test
    fun storageFailure_isRepresentedByExistingOperationResultContract() {
        val result: OperationResult<Unit> =
            OperationResult.Failure(ManagedError.STORAGE_FAILURE)

        assertTrue(result is OperationResult.Failure)
        assertEquals(
            ManagedError.STORAGE_FAILURE,
            (result as OperationResult.Failure).error,
        )
    }
}
