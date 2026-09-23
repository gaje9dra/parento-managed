package com.parento.managed.ui

import androidx.lifecycle.ViewModel
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.ManagedError

class ManagedStatusViewModel : ViewModel() {
    var uiState: ManagedUiState = ManagedUiState.Unenrolled
        private set

    fun showLoading() {
        uiState = ManagedUiState.Loading
    }

    fun showUnenrolled() {
        uiState = ManagedUiState.Unenrolled
    }

    fun showDeviceState(
        status: DeviceStatus,
        connectionState: ConnectionState,
    ) {
        val managementLabel = when (status) {
            DeviceStatus.UNENROLLED -> "Not enrolled"
            DeviceStatus.ENROLLING -> "Enrollment in progress"
            DeviceStatus.ENROLLED -> "Enrolled"
            DeviceStatus.CONNECTED -> "Managed and connected"
            DeviceStatus.DISCONNECTED -> "Managed and disconnected"
            DeviceStatus.REVOKED -> "Management revoked"
            DeviceStatus.ERROR -> "Management state error"
        }

        val connectionLabel = when (connectionState) {
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.CONNECTING -> "Connection pending"
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.ERROR -> "Connection error"
        }

        uiState = ManagedUiState.Content(
            deviceStatus = status,
            managementLabel = managementLabel,
            connectionLabel = connectionLabel,
        )
    }

    fun showError(
        error: ManagedError,
        message: String,
        canRetry: Boolean = false,
    ) {
        uiState = ManagedUiState.Error(error, message, canRetry)
    }
}
