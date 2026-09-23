package com.parento.managed

import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.ManagedError
import com.parento.managed.ui.ManagedStatusViewModel
import com.parento.managed.ui.ManagedUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedStatusViewModelTest {
    @Test
    fun defaultState_isUnenrolled() {
        assertEquals(ManagedUiState.Unenrolled, ManagedStatusViewModel().uiState)
    }

    @Test
    fun stateTransitions_areStronglyTyped() {
        val viewModel = ManagedStatusViewModel()

        viewModel.showLoading()
        assertEquals(ManagedUiState.Loading, viewModel.uiState)

        viewModel.showDeviceState(DeviceStatus.CONNECTED, ConnectionState.CONNECTED)
        assertEquals(
            ManagedUiState.Content(
                DeviceStatus.CONNECTED,
                "Managed and connected",
                "Connected",
            ),
            viewModel.uiState,
        )

        viewModel.showError(
            ManagedError.PLATFORM_FAILURE,
            "The application could not load its current state.",
            canRetry = true,
        )
        assertEquals(
            ManagedUiState.Error(
                ManagedError.PLATFORM_FAILURE,
                "The application could not load its current state.",
                true,
            ),
            viewModel.uiState,
        )
    }
}
