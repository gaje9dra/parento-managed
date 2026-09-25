package com.parento.managed

import com.parento.managed.domain.ManagedError
import com.parento.managed.lifecycle.ApplicationInitializationState
import com.parento.managed.lifecycle.InitializationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationInitializationStateTest {
    @Test
    fun defaultsToNotStarted() {
        assertEquals(
            InitializationStatus.NOT_STARTED,
            ApplicationInitializationState().status,
        )
    }

    @Test
    fun failedStateIdentifiesSubsystem() {
        val state = ApplicationInitializationState(
            status = InitializationStatus.FAILED,
            failedSubsystem = "database",
            error = ManagedError.STORAGE_FAILURE,
        )
        assertEquals(InitializationStatus.FAILED, state.status)
        assertEquals("database", state.failedSubsystem)
        assertEquals(ManagedError.STORAGE_FAILURE, state.error)
    }
}
