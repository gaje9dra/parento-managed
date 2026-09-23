package com.parento.managed.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.ManagedError

class ManagedStatusViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    var uiState: ManagedUiState = restoreState()
        private set

    fun showLoading() {
        updateState(ManagedUiState.Loading)
    }

    fun showUnenrolled() {
        updateState(ManagedUiState.Unenrolled)
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
            ConnectionState.UNKNOWN -> "Connection unknown"
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.CONNECTING -> "Connection pending"
            ConnectionState.CONNECTED -> "Connected"
        }

        updateState(
            ManagedUiState.Content(
                deviceStatus = status,
                managementLabel = managementLabel,
                connectionLabel = connectionLabel,
            ),
        )
    }

    fun showError(
        error: ManagedError,
        message: String,
        canRetry: Boolean = false,
    ) {
        updateState(ManagedUiState.Error(error, message, canRetry))
    }

    private fun updateState(state: ManagedUiState) {
        uiState = state
        saveState(state)
    }

    private fun saveState(state: ManagedUiState) {
        savedStateHandle[STATE_KEY] = when (state) {
            ManagedUiState.Loading -> STATE_LOADING
            ManagedUiState.Unenrolled -> STATE_UNENROLLED
            is ManagedUiState.Content -> {
                savedStateHandle[DEVICE_STATUS_KEY] = state.deviceStatus.name
                savedStateHandle[MANAGEMENT_LABEL_KEY] = state.managementLabel
                savedStateHandle[CONNECTION_LABEL_KEY] = state.connectionLabel
                STATE_CONTENT
            }
            is ManagedUiState.Error -> {
                savedStateHandle[ERROR_KEY] = state.error.name
                savedStateHandle[ERROR_MESSAGE_KEY] = state.message
                savedStateHandle[ERROR_RETRY_KEY] = state.canRetry
                STATE_ERROR
            }
        }
    }

    private fun restoreState(): ManagedUiState {
        return when (savedStateHandle.get<String>(STATE_KEY)) {
            STATE_LOADING -> ManagedUiState.Loading
            STATE_CONTENT -> {
                val status = savedStateHandle.get<String>(DEVICE_STATUS_KEY)
                    ?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
                val managementLabel = savedStateHandle.get<String>(MANAGEMENT_LABEL_KEY)
                val connectionLabel = savedStateHandle.get<String>(CONNECTION_LABEL_KEY)
                if (status != null && managementLabel != null && connectionLabel != null) {
                    ManagedUiState.Content(status, managementLabel, connectionLabel)
                } else {
                    ManagedUiState.Unenrolled
                }
            }
            STATE_ERROR -> {
                val error = savedStateHandle.get<String>(ERROR_KEY)
                    ?.let { runCatching { ManagedError.valueOf(it) }.getOrNull() }
                val message = savedStateHandle.get<String>(ERROR_MESSAGE_KEY)
                if (error != null && message != null) {
                    ManagedUiState.Error(
                        error = error,
                        message = message,
                        canRetry = savedStateHandle[ERROR_RETRY_KEY] ?: false,
                    )
                } else {
                    ManagedUiState.Unenrolled
                }
            }
            else -> ManagedUiState.Unenrolled
        }
    }

    private companion object {
        const val STATE_KEY = "managed_ui_state"
        const val STATE_LOADING = "loading"
        const val STATE_UNENROLLED = "unenrolled"
        const val STATE_CONTENT = "content"
        const val STATE_ERROR = "error"
        const val DEVICE_STATUS_KEY = "device_status"
        const val MANAGEMENT_LABEL_KEY = "management_label"
        const val CONNECTION_LABEL_KEY = "connection_label"
        const val ERROR_KEY = "error_type"
        const val ERROR_MESSAGE_KEY = "error_message"
        const val ERROR_RETRY_KEY = "error_can_retry"
    }
}
