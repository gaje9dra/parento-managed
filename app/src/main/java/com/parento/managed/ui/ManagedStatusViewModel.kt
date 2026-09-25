package com.parento.managed.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.ManagedError
import com.parento.managed.device.ManagementMode
import com.parento.managed.lifecycle.ManagementState
import com.parento.managed.monitoring.MonitoringSnapshot

class ManagedStatusViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    var uiState: ManagedUiState = restoreState()
        private set

    fun showLoading() = updateState(ManagedUiState.Loading)
    fun showUnenrolled() = updateState(ManagedUiState.Unenrolled)

    fun showManagementState(
        managementState: ManagementState,
        enrollmentState: com.parento.managed.domain.EnrollmentState,
        connectionState: ConnectionState,
        monitoringSnapshot: MonitoringSnapshot? = null,
    ) {
        val status = when (enrollmentState) {
            com.parento.managed.domain.EnrollmentState.UNENROLLED -> DeviceStatus.UNENROLLED
            com.parento.managed.domain.EnrollmentState.ENROLLING -> DeviceStatus.ENROLLING
            com.parento.managed.domain.EnrollmentState.ENROLLED -> DeviceStatus.ENROLLED
            com.parento.managed.domain.EnrollmentState.REVOKED -> DeviceStatus.REVOKED
            com.parento.managed.domain.EnrollmentState.ERROR -> DeviceStatus.ERROR
        }
        val managementLabel = when (managementState.managementMode) {
            ManagementMode.NOT_MANAGED -> "Not managed by Android Enterprise"
            ManagementMode.PROFILE_OWNER -> "Profile Owner"
            ManagementMode.DEVICE_OWNER -> "Device Owner"
            ManagementMode.UNKNOWN -> "Management mode unavailable"
        }
        updateState(ManagedUiState.Content(status, managementLabel, connectionLabel(connectionState), monitoringSnapshot))
    }

    fun showDeviceState(status: DeviceStatus, connectionState: ConnectionState, monitoringSnapshot: MonitoringSnapshot? = null) {
        val managementLabel = when (status) {
            DeviceStatus.UNENROLLED -> "Not enrolled"
            DeviceStatus.ENROLLING -> "Enrollment in progress"
            DeviceStatus.ENROLLED -> "Enrolled"
            DeviceStatus.REVOKED -> "Management revoked"
            DeviceStatus.ERROR -> "Management state error"
        }
        updateState(ManagedUiState.Content(status, managementLabel, connectionLabel(connectionState), monitoringSnapshot))
    }

    fun showError(error: ManagedError, message: String, canRetry: Boolean = false) {
        updateState(ManagedUiState.Error(error, message, canRetry))
    }

    private fun connectionLabel(state: ConnectionState): String =
        when (state) {
            ConnectionState.UNKNOWN -> "Connection unknown"
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.CONNECTING -> "Connecting"
            ConnectionState.AUTHENTICATING -> "Authenticating"
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.RECONNECTING -> "Reconnecting"
            ConnectionState.DISCONNECTING -> "Disconnecting"
            ConnectionState.FAILED -> "Connection failed"
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

    private fun restoreState(): ManagedUiState =
        when (savedStateHandle.get<String>(STATE_KEY)) {
            STATE_LOADING -> ManagedUiState.Loading
            STATE_CONTENT -> {
                val status = savedStateHandle.get<String>(DEVICE_STATUS_KEY)?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
                val managementLabel = savedStateHandle.get<String>(MANAGEMENT_LABEL_KEY)
                val connectionLabel = savedStateHandle.get<String>(CONNECTION_LABEL_KEY)
                if (status != null && managementLabel != null && connectionLabel != null) ManagedUiState.Content(status, managementLabel, connectionLabel)
                else ManagedUiState.Unenrolled
            }
            STATE_ERROR -> {
                val error = savedStateHandle.get<String>(ERROR_KEY)?.let { runCatching { ManagedError.valueOf(it) }.getOrNull() }
                val message = savedStateHandle.get<String>(ERROR_MESSAGE_KEY)
                if (error != null && message != null) ManagedUiState.Error(error, message, savedStateHandle[ERROR_RETRY_KEY] ?: false)
                else ManagedUiState.Unenrolled
            }
            else -> ManagedUiState.Unenrolled
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
