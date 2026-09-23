package com.parento.managed.data

import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalStateService(
    private val repository: LocalStateRepository,
) {
    private val initializationMutex = Mutex()
    private val runtimeConnectionState = MutableStateFlow(ConnectionState.UNKNOWN)

    suspend fun initialize(): OperationResult<LocalApplicationState> =
        initializationMutex.withLock {
            val current = when (val result = repository.read()) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return@withLock result
            }

            val initialized = when {
                current == null -> repository.initializeLocalState()
                current.installationId.isNullOrBlank() ||
                    current.identityCreatedAtEpochMillis == null ->
                    repository.initializeLocalState()
                !current.initialized ->
                    repository.initializeLocalState()
                else ->
                    OperationResult.Success(current)
            }

            when (initialized) {
                is OperationResult.Success -> {
                    runtimeConnectionState.value = ConnectionState.UNKNOWN
                    OperationResult.Success(initialized.value.copy(
                        connectionState = ConnectionState.UNKNOWN,
                    ))
                }
                is OperationResult.Failure -> initialized
            }
        }

    suspend fun getLocalIdentity(): OperationResult<LocalDeviceIdentity> =
        repository.getOrCreateIdentity()

    fun observeState(): Flow<OperationResult<LocalApplicationState?>> =
        combine(repository.observe(), runtimeConnectionState) { result, runtimeConnection ->
            when (result) {
                is OperationResult.Success -> {
                    val state = result.value
                    OperationResult.Success(
                        state?.copy(connectionState = runtimeConnection),
                    )
                }
                is OperationResult.Failure -> result
            }
        }

    suspend fun getEnrollmentState(): OperationResult<EnrollmentState> =
        when (val result = repository.read()) {
            is OperationResult.Success ->
                OperationResult.Success(
                    result.value?.enrollmentState ?: EnrollmentState.UNENROLLED,
                )
            is OperationResult.Failure -> result
        }

    suspend fun setEnrollmentState(state: EnrollmentState): OperationResult<Unit> =
        repository.updateEnrollmentState(state)

    suspend fun getConnectionState(): OperationResult<ConnectionState> =
        OperationResult.Success(runtimeConnectionState.value)

    suspend fun setConnectionState(state: ConnectionState): OperationResult<Unit> =
        when (val result = repository.updateConnectionState(state)) {
            is OperationResult.Success -> {
                runtimeConnectionState.value = state
                result
            }
            is OperationResult.Failure -> result
        }

    suspend fun recoverMissingIdentity(): OperationResult<LocalDeviceIdentity> =
        when (val result = repository.getOrCreateIdentity()) {
            is OperationResult.Success -> result
            is OperationResult.Failure -> result
        }
}
