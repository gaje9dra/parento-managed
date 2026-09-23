package com.parento.managed

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStateRepositoryTest {
    @Test
    fun stateModel_defaultsToUninitialized() {
        val state = LocalApplicationState()

        assertEquals(1, state.stateVersion)
        assertEquals(null, state.lastSynchronizationTimestamp)
        assertEquals(false, state.initialized)
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
