package com.parento.managed.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EnrollmentUiState(
    val pending: PendingEnrollment? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class EnrollmentViewModel(private val repository: EnrollmentRepository) : ViewModel() {
    private val _state = MutableStateFlow(EnrollmentUiState())
    val state: StateFlow<EnrollmentUiState> = _state.asStateFlow()

    fun restore() {
        viewModelScope.launch {
            when (val result = repository.restorePending()) {
                is OperationResult.Failure ->
                    _state.value = _state.value.copy(message = messageFor(result.error))
                is OperationResult.Success ->
                    _state.value = _state.value.copy(pending = result.value, message = null)
            }
        }
    }

    fun start(enrollmentId: String, secret: String, expiresAt: Long) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            when (val result = repository.begin(enrollmentId, secret, expiresAt)) {
                is OperationResult.Failure ->
                    _state.value = _state.value.copy(busy = false, message = messageFor(result.error))
                is OperationResult.Success ->
                    _state.value = _state.value.copy(
                        busy = false,
                        pending = result.value,
                        message = "Enrollment authorization saved.",
                    )
            }
        }
    }

    fun enroll(name: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            when (val result = repository.enroll(name)) {
                is OperationResult.Failure ->
                    _state.value = _state.value.copy(busy = false, message = messageFor(result.error))
                is OperationResult.Success ->
                    _state.value = _state.value.copy(
                        busy = false,
                        pending = null,
                        message = "Enrollment completed.",
                    )
            }
        }
    }

    fun cancel() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            repository.cancel()
            _state.value = _state.value.copy(
                busy = false,
                pending = null,
                message = "Enrollment cancelled.",
            )
        }
    }

    private fun messageFor(error: ManagedError): String = when (error) {
        ManagedError.NETWORK_FAILURE -> "Unable to reach the Parento backend."
        ManagedError.AUTHORIZATION_FAILURE -> "The enrollment authorization was rejected."\n        ManagedError.RATE_LIMITED -> "Too many enrollment attempts. Try again later."
        ManagedError.AUTHENTICATION_FAILURE -> "Authentication failed."
        ManagedError.INVALID_STATE -> "This enrollment is expired, already used, revoked, or unavailable."
        ManagedError.STORAGE_FAILURE -> "Secure local enrollment storage is unavailable."
        else -> "Enrollment could not be completed."
    }
}
