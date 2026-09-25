package com.parento.managed.enrollment

import com.parento.managed.data.LocalStateRepository
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EnrollmentRepository(
    private val apiClient: EnrollmentApiClient,
    private val localStateRepository: LocalStateRepository,
    private val secureStore: EnrollmentStore,
) {
    private val mutex = Mutex()

    suspend fun restorePending(): OperationResult<PendingEnrollment?> = mutex.withLock {
        when (val result = secureStore.read()) {
            is OperationResult.Failure -> result
            is OperationResult.Success -> {
                val pending = result.value ?: return@withLock result
                if (pending.expiresAtEpochMillis <= System.currentTimeMillis()) {
                    secureStore.clear()
                    localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
                    OperationResult.Success(null)
                } else {
                    OperationResult.Success(pending)
                }
            }
        }
    }

    suspend fun begin(
        enrollmentId: String,
        authorizationSecret: String,
        expiresAtEpochMillis: Long,
    ): OperationResult<PendingEnrollment> = mutex.withLock {
        if (
            enrollmentId.isBlank() ||
            authorizationSecret.isBlank() ||
            expiresAtEpochMillis <= System.currentTimeMillis()
        ) {
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }
        when (val current = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> return@withLock current
            is OperationResult.Success ->
                if (current.value == EnrollmentState.ENROLLED) {
                    return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
                }
        }
        val pending = PendingEnrollment(
            enrollmentId.trim(),
            authorizationSecret.trim(),
            expiresAtEpochMillis,
        )
        when (val saved = secureStore.save(pending)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> {
                val state = localStateRepository.getEnrollmentState()
                if (state is OperationResult.Failure) return@withLock state
                if (state is OperationResult.Success &&
                    (state.value == EnrollmentState.UNENROLLED ||
                        state.value == EnrollmentState.ERROR)
                ) {
                    localStateRepository.updateEnrollmentState(EnrollmentState.ENROLLING)
                }
                OperationResult.Success(pending)
            }
        }
    }

    suspend fun enroll(name: String): OperationResult<EnrollmentResult> = mutex.withLock {\n        val normalizedName = name.trim()\n        if (normalizedName.isBlank() || normalizedName.length > 100) {\n            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)\n        }
        val pending = when (val stored = secureStore.read()) {
            is OperationResult.Failure -> return@withLock stored
            is OperationResult.Success -> stored.value
        } ?: return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)

        if (pending.expiresAtEpochMillis <= System.currentTimeMillis()) {
            secureStore.clear()
            localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        val identity = when (val result = localStateRepository.getOrCreateIdentity()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }

        when (
            val result = apiClient.consume(
                EnrollmentAuthorization(pending.enrollmentId, pending.authorizationSecret),
                identity.installationId,
                name,
            )
        ) {
            is OperationResult.Failure -> {
                localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
                result
            }
            is OperationResult.Success -> {
                when (val state = localStateRepository.completeEnrollment(result.value.managedDeviceId)) {
                    is OperationResult.Failure -> state
                    is OperationResult.Success -> {
                        secureStore.clear()
                        result
                    }
                }
            }
        }
    }

    suspend fun cancel(): OperationResult<Unit> = mutex.withLock {
        secureStore.clear()
        when (val state = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> state
            is OperationResult.Success ->
                if (state.value == EnrollmentState.ENROLLING) {
                    localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
                } else {
                    OperationResult.Success(Unit)
                }
        }
    }
}


private fun isValidEnrollmentId(value: String): Boolean =
    runCatching { java.util.UUID.fromString(value.trim()) }.isSuccess

private fun isValidAuthorizationSecret(value: String): Boolean =
    value.trim().length == 43 && value.trim().matches(Regex("[A-Za-z0-9_-]{43}"))
