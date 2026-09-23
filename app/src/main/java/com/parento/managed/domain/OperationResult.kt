package com.parento.managed.domain

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>

    data class Failure(
        val error: ManagedError,
    ) : OperationResult<Nothing>
}

enum class ManagedError {
    INVALID_STATE,
    NETWORK_FAILURE,
    AUTHENTICATION_FAILURE,
    AUTHORIZATION_FAILURE,
    POLICY_FAILURE,
    PLATFORM_FAILURE,
    STORAGE_FAILURE,
    UNKNOWN,
}
