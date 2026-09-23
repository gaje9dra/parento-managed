package com.parento.managed.ui

import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.ManagedError

sealed interface ManagedUiState {
    data object Loading : ManagedUiState
    data object Unenrolled : ManagedUiState
    data class Content(
        val deviceStatus: DeviceStatus,
        val managementLabel: String,
        val connectionLabel: String,
    ) : ManagedUiState
    data class Error(
        val error: ManagedError,
        val message: String,
        val canRetry: Boolean = false,
    ) : ManagedUiState
}
