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
    @Test fun stateModel_defaultsToSafeInitialState() {
        val state = LocalApplicationState()
        assertEquals(1, state.stateVersion)
        assertEquals(null, state.lastSynchronizationTimestamp)
        assertEquals(false, state.initialized)
        assertEquals(EnrollmentState.UNENROLLED, state.enrollmentState)
        assertEquals(ConnectionState.UNKNOWN, state.connectionState)
        assertEquals(null, state.managedDeviceId)
    }

    @Test fun connectionTransitions_coverSessionLifecycle() {
        assertTrue(ConnectionState.UNKNOWN.canTransitionTo(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionState.DISCONNECTED.canTransitionTo(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.AUTHENTICATING))
        assertTrue(ConnectionState.AUTHENTICATING.canTransitionTo(ConnectionState.CONNECTED))
        assertTrue(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.RECONNECTING))
        assertTrue(ConnectionState.RECONNECTING.canTransitionTo(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.DISCONNECTING))
        assertTrue(ConnectionState.DISCONNECTING.canTransitionTo(ConnectionState.DISCONNECTED))
        assertFalse(ConnectionState.UNKNOWN.canTransitionTo(ConnectionState.CONNECTED))
        assertFalse(ConnectionState.DISCONNECTED.canTransitionTo(ConnectionState.CONNECTED))
    }

    @Test fun storageFailure_isRepresentedByExistingOperationResultContract() {
        val result: OperationResult<Unit> = OperationResult.Failure(ManagedError.STORAGE_FAILURE)
        assertTrue(result is OperationResult.Failure)
        assertEquals(ManagedError.STORAGE_FAILURE, (result as OperationResult.Failure).error)
    }
}
