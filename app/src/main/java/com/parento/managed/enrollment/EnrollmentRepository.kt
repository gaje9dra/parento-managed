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
        val pending = when (val result = secureStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }

        val state = when (val result = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }

        if (pending == null) {
            if (state == EnrollmentState.ENROLLING) {
                localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
            }
            return@withLock OperationResult.Success(null)
        }

        if (pending.expiresAtEpochMillis <= System.currentTimeMillis()) {
            when (val clearResult = secureStore.clear()) {
                is OperationResult.Failure -> return@withLock clearResult
                is OperationResult.Success -> Unit
            }
            if (state == EnrollmentState.ENROLLING || state == EnrollmentState.ERROR) {
                localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
            }
            return@withLock OperationResult.Success(null)
        }

        when (state) {
            EnrollmentState.UNENROLLED -> {
                when (val transition = localStateRepository.updateEnrollmentState(EnrollmentState.ENROLLING)) {
                    is OperationResult.Failure -> {
                        secureStore.clear()
                        return@withLock transition
                    }
                    is OperationResult.Success -> Unit
                }
            }
            EnrollmentState.ENROLLING -> Unit
            EnrollmentState.ERROR,
            EnrollmentState.ENROLLED,
            EnrollmentState.REVOKED -> {
                // Never replay a one-time authorization after an uncertain or terminal
                // local outcome. A fresh administrator-issued enrollment is required.
                when (val clearResult = secureStore.clear()) {
                    is OperationResult.Failure -> return@withLock clearResult
                    is OperationResult.Success -> Unit
                }
                if (state == EnrollmentState.ERROR) {
                    localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
                }
                return@withLock OperationResult.Success(null)
            }
        }

        OperationResult.Success(pending)
    }

    suspend fun begin(
        enrollmentId: String,
        authorizationSecret: String,
        expiresAtEpochMillis: Long,
    ): OperationResult<PendingEnrollment> = mutex.withLock {
        val normalizedId = enrollmentId.trim()
        val normalizedSecret = authorizationSecret.trim()
        if (
            !isValidEnrollmentId(normalizedId) ||
            !isValidAuthorizationSecret(normalizedSecret) ||
            expiresAtEpochMillis <= System.currentTimeMillis()
        ) {
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        val current = when (val result = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }
        if (current != EnrollmentState.UNENROLLED && current != EnrollmentState.ERROR) {
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        // ERROR is a terminal local outcome for the previous one-time authorization.
        // Starting again replaces it with a fresh administrator-issued authorization.
        when (val clearResult = secureStore.clear()) {
            is OperationResult.Failure -> return@withLock clearResult
            is OperationResult.Success -> Unit
        }
        val pending = PendingEnrollment(normalizedId, normalizedSecret, expiresAtEpochMillis)
        when (val saved = secureStore.save(pending)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> {
                when (val transition = localStateRepository.updateEnrollmentState(EnrollmentState.ENROLLING)) {
                    is OperationResult.Failure -> {
                        secureStore.clear()
                        transition
                    }
                    is OperationResult.Success -> OperationResult.Success(pending)
                }
            }
        }
    }

    suspend fun enroll(name: String): OperationResult<EnrollmentResult> = mutex.withLock {
        val normalizedName = name.trim()
        if (
            normalizedName.isBlank() ||
            normalizedName.length > 100 ||
            normalizedName.any { it.isISOControl() }
        ) {
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        when (val state = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> return@withLock state
            is OperationResult.Success ->
                if (state.value != EnrollmentState.ENROLLING) {
                    return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
                }
        }

        val pending = when (val stored = secureStore.read()) {
            is OperationResult.Failure -> return@withLock stored
            is OperationResult.Success -> stored.value
        } ?: run {
            localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
            return@withLock OperationResult.Failure(ManagedError.INVALID_STATE)
        }

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
                normalizedName,
            )
        ) {
            is OperationResult.Failure -> {
                // The backend consume operation is one-time. A timeout or transport failure
                // may occur after the server commits, so the same authorization must never
                // be retried automatically or restored after process death.
                secureStore.clear()
                localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
                result
            }
            is OperationResult.Success -> {
                val response = result.value
                if (
                    response.enrollmentId != pending.enrollmentId ||
                    response.managedDeviceId.isBlank()
                ) {
                    secureStore.clear()
                    localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
                    return@withLock OperationResult.Failure(ManagedError.UNKNOWN)
                }

                when (val state = localStateRepository.completeEnrollment(response.managedDeviceId)) {
                    is OperationResult.Failure -> {
                        // The backend has already consumed the one-time authorization. Do not
                        // retain it while local persistence is recovering from an error.
                        secureStore.clear()
                        localStateRepository.updateEnrollmentState(EnrollmentState.ERROR)
                        state
                    }
                    is OperationResult.Success -> {
                        secureStore.clear()
                        result
                    }
                }
            }
        }
    }

    suspend fun cancel(): OperationResult<Unit> = mutex.withLock {
        val clearResult = secureStore.clear()
        if (clearResult is OperationResult.Failure) return@withLock clearResult

        when (val state = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> state
            is OperationResult.Success ->
                if (state.value == EnrollmentState.ENROLLING ||
                    state.value == EnrollmentState.ERROR
                ) {
                    localStateRepository.updateEnrollmentState(EnrollmentState.UNENROLLED)
                } else {
                    OperationResult.Success(Unit)
                }
        }
    }
}

private fun isValidEnrollmentId(value: String): Boolean =
    runCatching { java.util.UUID.fromString(value) }.isSuccess

private fun isValidAuthorizationSecret(value: String): Boolean =
    value.length == 43 && value.matches(Regex("[A-Za-z0-9_-]{43}"))
