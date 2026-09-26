package com.parento.managed.communication

import com.parento.managed.data.LocalStateRepository
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.domain.canTransitionTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceCommunicationSessionManager(
    private val localStateRepository: LocalStateRepository,
    private val credentialStore: DeviceCredentialStore,
    private val sessionStore: DeviceSessionStore,
    private val transport: DeviceTransport,
) {
    private val mutex = Mutex()
    private var commandStreamJob: Job? = null
    private val _state = MutableStateFlow(ConnectionState.UNKNOWN)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    suspend fun recover(): OperationResult<ConnectionState> = mutex.withLock {
        val stored = when (val result = sessionStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }
        if (stored != null && stored.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessionStore.clear()
        }
        transition(ConnectionState.DISCONNECTED)
        OperationResult.Success(ConnectionState.DISCONNECTED)
    }

    suspend fun connect(): OperationResult<ConnectionState> = mutex.withLock {
        val enrollment = when (val result = localStateRepository.getEnrollmentState()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        }
        if (enrollment != EnrollmentState.ENROLLED) return@withLock fail(ManagedError.AUTHORIZATION_FAILURE)

        val deviceId = when (val result = localStateRepository.getManagedDeviceId()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        } ?: return@withLock fail(ManagedError.AUTHORIZATION_FAILURE)

        val credential = when (val result = credentialStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        } ?: return@withLock fail(ManagedError.AUTHORIZATION_FAILURE)

        transition(ConnectionState.CONNECTING)
        transition(ConnectionState.AUTHENTICATING)
        when (val result = transport.connect(credential)) {
            is OperationResult.Failure -> {
                transition(ConnectionState.FAILED)
                transition(ConnectionState.DISCONNECTED)
                result
            }
            is OperationResult.Success -> {
                if (result.value.managedDeviceId != deviceId) {
                    sessionStore.clear()
                    transition(ConnectionState.FAILED)
                    transition(ConnectionState.DISCONNECTED)
                    OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
                } else {
                    val saved = sessionStore.save(DeviceSession(result.value.sessionId, result.value.managedDeviceId, result.value.sessionToken, result.value.expiresAtEpochMillis))
                    if (saved is OperationResult.Failure) {
                        transition(ConnectionState.FAILED)
                        transition(ConnectionState.DISCONNECTED)
                        saved
                    } else {
                        transition(ConnectionState.CONNECTED)
                        OperationResult.Success(ConnectionState.CONNECTED)
                    }
                }
            }
        }
    }

    suspend fun currentManagedDeviceId(): String? =
        when (val result = localStateRepository.getManagedDeviceId()) {
            is OperationResult.Failure -> null
            is OperationResult.Success -> result.value
        }

    suspend fun currentSessionToken(): String? = when (val result = sessionStore.read()) {
        is OperationResult.Failure -> null
        is OperationResult.Success -> result.value?.takeIf {
            it.expiresAtEpochMillis > System.currentTimeMillis()
        }?.sessionToken
    }

    fun transportBoundary(): DeviceTransport = transport

    suspend fun receiveNextCommand(): OperationResult<TransportCommand?> = mutex.withLock {
        val session = when (val result = sessionStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        } ?: return@withLock OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)

        if (session.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessionStore.clear()
            transition(ConnectionState.DISCONNECTED)
            return@withLock OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        }

        when (val result = transport.receiveNextCommand(session.sessionToken)) {
            is OperationResult.Failure -> {
                if (result.error == ManagedError.AUTHENTICATION_FAILURE ||
                    result.error == ManagedError.AUTHORIZATION_FAILURE
                ) {
                    sessionStore.clear()
                    transition(ConnectionState.DISCONNECTED)
                }
                result
            }
            is OperationResult.Success -> OperationResult.Success(result.value)
        }
    }

    fun startCommandStream(
        scope: CoroutineScope,
        onCommand: suspend (TransportCommand) -> Unit,
    ) {
        if (commandStreamJob?.isActive == true) return
        commandStreamJob = scope.launch {
            while (isActive) {
                val token = currentSessionToken()
                if (token == null) {
                    delay(1_000)
                    continue
                }
                when (transport.streamCommands(token, onCommand)) {
                    is OperationResult.Success -> Unit
                    is OperationResult.Failure -> delay(1_000)
                }
            }
        }
    }

    fun stopCommandStream() {
        commandStreamJob?.cancel()
        commandStreamJob = null
    }

    suspend fun heartbeat(): OperationResult<ConnectionState> = mutex.withLock {
        val session = when (val result = sessionStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        } ?: return@withLock fail(ManagedError.AUTHENTICATION_FAILURE)

        if (session.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessionStore.clear()
            transition(ConnectionState.DISCONNECTED)
            return@withLock OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        }

        when (val result = transport.heartbeat(session.sessionToken)) {
            is OperationResult.Failure -> {
                transition(ConnectionState.RECONNECTING)
                transition(ConnectionState.DISCONNECTED)
                result
            }
            is OperationResult.Success -> {
                sessionStore.save(session.copy(expiresAtEpochMillis = result.value))
                transition(ConnectionState.CONNECTED)
                OperationResult.Success(ConnectionState.CONNECTED)
            }
        }
    }

    suspend fun reportLocation(
        reportId: String,
        availability: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        observedAt: String,
    ): OperationResult<Unit> = mutex.withLock {
        val session = when (val result = sessionStore.read()) {
            is OperationResult.Failure -> return@withLock result
            is OperationResult.Success -> result.value
        } ?: return@withLock OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)

        if (session.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessionStore.clear()
            transition(ConnectionState.DISCONNECTED)
            return@withLock OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        }

        val result = transport.reportLocation(
            sessionToken = session.sessionToken,
            reportId = reportId,
            availability = availability,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            observedAt = observedAt,
        )
        if (result is OperationResult.Failure &&
            (result.error == ManagedError.AUTHENTICATION_FAILURE || result.error == ManagedError.AUTHORIZATION_FAILURE)
        ) {
            sessionStore.clear()
            transition(ConnectionState.DISCONNECTED)
        }
        result
    }

    suspend fun reportAudioStarted(
        audioSessionId: String,
        transportState: String? = null,
    ): OperationResult<Unit> {
        val token = currentSessionToken() ?: return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        return transport.reportAudioStarted(token, audioSessionId, transportState)
    }

    suspend fun reportAudioStopped(audioSessionId: String): OperationResult<Unit> {
        val token = currentSessionToken() ?: return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        return transport.reportAudioStopped(token, audioSessionId)
    }

    suspend fun disconnect(): OperationResult<ConnectionState> = mutex.withLock {
        commandStreamJob?.cancel()
        commandStreamJob = null
        transition(ConnectionState.DISCONNECTING)
        val session = when (val result = sessionStore.read()) {
            is OperationResult.Failure -> {
                transition(ConnectionState.DISCONNECTED)
                return@withLock result
            }
            is OperationResult.Success -> result.value
        }
        val result = if (session == null) OperationResult.Success(Unit) else transport.disconnect(session.sessionToken)
        sessionStore.clear()
        transition(ConnectionState.DISCONNECTED)
        when (result) {
            is OperationResult.Failure -> result
            is OperationResult.Success -> OperationResult.Success(ConnectionState.DISCONNECTED)
        }
    }

    private suspend fun fail(error: ManagedError): OperationResult<ConnectionState> {
        transition(ConnectionState.FAILED)
        transition(ConnectionState.DISCONNECTED)
        return OperationResult.Failure(error)
    }

    private suspend fun transition(target: ConnectionState) {
        val current = _state.value
        if (current == target) return
        if (!current.canTransitionTo(target)) {
            _state.value = ConnectionState.FAILED
            localStateRepository.updateConnectionState(ConnectionState.FAILED)
            if (target != ConnectionState.FAILED && ConnectionState.FAILED.canTransitionTo(target)) {
                _state.value = target
                localStateRepository.updateConnectionState(target)
            }
            return
        }
        _state.value = target
        localStateRepository.updateConnectionState(target)
    }
}
