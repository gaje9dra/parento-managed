package com.parento.managed.data

import com.parento.managed.data.local.LocalApplicationStateDao
import com.parento.managed.data.local.LocalApplicationStateEntity
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ConnectionState
import com.parento.managed.device.CapabilityState
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.application.ApplicationEnforcementStatus
import com.parento.managed.application.ApplicationInventorySyncStatus
import com.parento.managed.application.ApplicationPolicySyncStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.domain.canTransitionTo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DeviceIdentityRepository {
    suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity>
}

interface EnrollmentStateRepository {
    suspend fun getEnrollmentState(): OperationResult<EnrollmentState>
    suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit>
}

interface ConnectionStateRepository {
    suspend fun getConnectionState(): OperationResult<ConnectionState>
    suspend fun updateConnectionState(state: ConnectionState): OperationResult<Unit>
}

interface ManagedDeviceStateRepository {
    suspend fun getManagedDeviceId(): OperationResult<String?>
    suspend fun completeEnrollment(managedDeviceId: String): OperationResult<Unit>
    suspend fun updateApplicationInventorySync(
        status: ApplicationInventorySyncStatus,
        observedAtEpochMillis: Long? = null,
        successfulSyncAtEpochMillis: Long? = null,
    ): OperationResult<Unit>
    suspend fun updateApplicationPolicyReference(
        policyId: String,
        policyVersion: Int,
        status: ApplicationPolicySyncStatus,
    ): OperationResult<Unit>
    suspend fun updateApplicationEnforcementStatus(
        status: ApplicationEnforcementStatus,
    ): OperationResult<Unit>
}

interface LocalStateRepository :
    DeviceIdentityRepository,
    EnrollmentStateRepository,
    ConnectionStateRepository,
    ManagedDeviceStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>
    suspend fun write(state: LocalApplicationState): OperationResult<Unit>
    suspend fun clear(): OperationResult<Unit>
    fun observe(): Flow<OperationResult<LocalApplicationState?>>
    suspend fun initializeLocalState(): OperationResult<LocalApplicationState>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    private val stateMutex = Mutex()

    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation { dao.read()?.toDomain() }

    override suspend fun write(state: LocalApplicationState): OperationResult<Unit> =
        stateMutex.withLock { runStorageOperation { dao.upsert(state.toEntity()) } }

    override suspend fun clear(): OperationResult<Unit> =
        stateMutex.withLock { runStorageOperation { dao.clear() } }

    override fun observe(): Flow<OperationResult<LocalApplicationState?>> =
        dao.observe().map { entity ->
            try {
                OperationResult.Success(entity?.toDomain())
            } catch (_: Exception) {
                OperationResult.Failure(ManagedError.STORAGE_FAILURE)
            }
        }

    override suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity> =
        stateMutex.withLock {
            runStorageOperation {
                val existing = dao.read()
                val existingId = existing?.installationId
                val existingCreatedAt = existing?.identityCreatedAtEpochMillis
                if (!existingId.isNullOrBlank() && existingCreatedAt != null) {
                    if (!isValidInstallationId(existingId)) {
                        throw IllegalStateException("Invalid persisted installation identity.")
                    }
                    if (existingCreatedAt <= 0L) {
                        throw IllegalStateException("Invalid persisted identity timestamp.")
                    }
                    return@runStorageOperation LocalDeviceIdentity(existingId, existingCreatedAt)
                }

                val base = existing?.toDomain() ?: LocalApplicationState()
                if (
                    base.enrollmentState != EnrollmentState.UNENROLLED ||
                    base.managedDeviceId != null
                ) {
                    throw IllegalStateException("Cannot recreate installation identity for existing managed state.")
                }
                val now = System.currentTimeMillis()
                val identity = LocalDeviceIdentity(UUID.randomUUID().toString(), now)
                dao.upsert(
                    base.copy(
                        installationId = identity.installationId,
                        identityCreatedAtEpochMillis = identity.createdAtEpochMillis,
                    ).toEntity(),
                )
                identity
            }
        }

    override suspend fun initializeLocalState(): OperationResult<LocalApplicationState> =
        stateMutex.withLock {
            runStorageOperation {
                val current = dao.read()?.toDomain() ?: LocalApplicationState()
                val identity = if (
                    !current.installationId.isNullOrBlank() &&
                    current.identityCreatedAtEpochMillis != null
                ) {
                    if (!isValidInstallationId(current.installationId)) {
                        throw IllegalStateException("Invalid persisted installation identity.")
                    }
                    if (current.identityCreatedAtEpochMillis <= 0L) {
                        throw IllegalStateException("Invalid persisted identity timestamp.")
                    }
                    LocalDeviceIdentity(current.installationId, current.identityCreatedAtEpochMillis)
                } else {
                    if (
                        current.enrollmentState != EnrollmentState.UNENROLLED ||
                        current.managedDeviceId != null
                    ) {
                        throw IllegalStateException("Cannot recreate installation identity for existing managed state.")
                    }
                    LocalDeviceIdentity(UUID.randomUUID().toString(), System.currentTimeMillis())
                }

                val initialized = current.copy(
                    initialized = true,
                    installationId = identity.installationId,
                    identityCreatedAtEpochMillis = identity.createdAtEpochMillis,
                )
                dao.upsert(initialized.toEntity())
                initialized
            }
        }

    override suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit> =
        stateMutex.withLock {
            runStorageOperation {
                val current = dao.read()?.toDomain() ?: LocalApplicationState()
                if (!current.enrollmentState.canTransitionTo(state)) {
                    throw InvalidLocalStateTransitionException()
                }
                dao.upsert(current.copy(enrollmentState = state).toEntity())
            }
        }

    override suspend fun getEnrollmentState(): OperationResult<EnrollmentState> =
        runStorageOperation { dao.read()?.toDomain()?.enrollmentState ?: EnrollmentState.UNENROLLED }

    override suspend fun getConnectionState(): OperationResult<ConnectionState> =
        runStorageOperation { dao.read()?.toDomain()?.connectionState ?: ConnectionState.UNKNOWN }

    override suspend fun getManagedDeviceId(): OperationResult<String?> =
        runStorageOperation { dao.read()?.managedDeviceId }

    override suspend fun completeEnrollment(managedDeviceId: String): OperationResult<Unit> =
        stateMutex.withLock {
            runStorageOperation {
                require(managedDeviceId.isNotBlank())
                val current = dao.read()?.toDomain() ?: LocalApplicationState()
                if (current.enrollmentState != EnrollmentState.ENROLLING) {
                    throw InvalidLocalStateTransitionException()
                }
                dao.upsert(
                    current.copy(
                        managedDeviceId = managedDeviceId.trim(),
                        enrollmentState = EnrollmentState.ENROLLED,
                    ).toEntity(),
                )
            }
        }

    override suspend fun updateApplicationInventorySync(
        status: ApplicationInventorySyncStatus,
        observedAtEpochMillis: Long?,
        successfulSyncAtEpochMillis: Long?,
    ): OperationResult<Unit> = stateMutex.withLock {
        runStorageOperation {
            val current = dao.read()?.toDomain() ?: LocalApplicationState()
            dao.upsert(
                current.copy(
                    applicationInventorySyncStatus = status,
                    lastApplicationInventoryObservedAtEpochMillis =
                        observedAtEpochMillis ?: current.lastApplicationInventoryObservedAtEpochMillis,
                    lastApplicationInventorySuccessfulSyncAtEpochMillis =
                        successfulSyncAtEpochMillis ?: current.lastApplicationInventorySuccessfulSyncAtEpochMillis,
                ).toEntity(),
            )
        }
    }

    override suspend fun updateApplicationPolicyReference(
        policyId: String,
        policyVersion: Int,
        status: ApplicationPolicySyncStatus,
    ): OperationResult<Unit> = stateMutex.withLock {
        runStorageOperation {
            val current = dao.read()?.toDomain() ?: LocalApplicationState()
            dao.upsert(
                current.copy(
                    desiredApplicationPolicyId = policyId,
                    desiredApplicationPolicyVersion = policyVersion,
                    acceptedApplicationPolicyVersion =
                        maxOf(current.acceptedApplicationPolicyVersion ?: 0, policyVersion),
                    applicationPolicySyncStatus = status,
                    applicationEnforcementStatus =
                        if (status == ApplicationPolicySyncStatus.PENDING) {
                            ApplicationEnforcementStatus.PENDING
                        } else {
                            current.applicationEnforcementStatus
                        },
                ).toEntity(),
            )
        }
    }

    override suspend fun updateApplicationEnforcementStatus(
        status: ApplicationEnforcementStatus,
    ): OperationResult<Unit> = stateMutex.withLock {
        runStorageOperation {
            val current = dao.read()?.toDomain() ?: LocalApplicationState()
            dao.upsert(current.copy(applicationEnforcementStatus = status).toEntity())
        }
    }

    override suspend fun updateConnectionState(state: ConnectionState): OperationResult<Unit> =
        stateMutex.withLock {
            runStorageOperation {
                val current = dao.read()?.toDomain() ?: LocalApplicationState()
                if (!current.connectionState.canTransitionTo(state)) {
                    throw InvalidLocalStateTransitionException()
                }
                dao.upsert(current.copy(connectionState = state).toEntity())
            }
        }

    private suspend fun <T> runStorageOperation(operation: suspend () -> T): OperationResult<T> =
        try {
            OperationResult.Success(operation())
        } catch (_: InvalidLocalStateTransitionException) {
            OperationResult.Failure(ManagedError.INVALID_STATE)
        } catch (_: Exception) {
            OperationResult.Failure(ManagedError.STORAGE_FAILURE)
        }
}

private class InvalidLocalStateTransitionException : IllegalStateException()

private fun LocalApplicationStateEntity.toDomain(): LocalApplicationState {
    if (!installationId.isNullOrBlank() && !isValidInstallationId(installationId)) {
        throw IllegalStateException("Invalid persisted installation identity.")
    }
    if (identityCreatedAtEpochMillis != null && identityCreatedAtEpochMillis <= 0L) {
        throw IllegalStateException("Invalid persisted identity timestamp.")
    }

    return LocalApplicationState(
        stateVersion = stateVersion,
        lastSynchronizationTimestamp = lastSynchronizationTimestamp,
        initialized = initialized,
        installationId = installationId,
        identityCreatedAtEpochMillis = identityCreatedAtEpochMillis,
        managedDeviceId = managedDeviceId,
        enrollmentState = runCatching { EnrollmentState.valueOf(enrollmentState) }
            .getOrElse { throw IllegalStateException("Invalid persisted enrollment state.") },
        connectionState = runCatching { ConnectionState.valueOf(connectionState) }
            .getOrElse { throw IllegalStateException("Invalid persisted connection state.") },
        managementMode = runCatching { ManagementMode.valueOf(managementMode) }
            .getOrElse { throw IllegalStateException("Invalid persisted management mode.") },
        managementCapabilities = decodeCapabilities(managementCapabilities),
        managementStateUpdatedAtEpochMillis = managementStateUpdatedAtEpochMillis,
        applicationInventorySyncStatus = runCatching {
            ApplicationInventorySyncStatus.valueOf(applicationInventorySyncStatus)
        }.getOrElse { throw IllegalStateException("Invalid persisted application inventory sync status.") },
        lastApplicationInventoryObservedAtEpochMillis = lastApplicationInventoryObservedAtEpochMillis,
        lastApplicationInventorySuccessfulSyncAtEpochMillis = lastApplicationInventorySuccessfulSyncAtEpochMillis,
        desiredApplicationPolicyId = desiredApplicationPolicyId,
        desiredApplicationPolicyVersion = desiredApplicationPolicyVersion,
        acceptedApplicationPolicyVersion = acceptedApplicationPolicyVersion,
        applicationPolicySyncStatus = runCatching {
            ApplicationPolicySyncStatus.valueOf(applicationPolicySyncStatus)
        }.getOrElse { throw IllegalStateException("Invalid persisted application policy sync status.") },
        applicationEnforcementStatus = runCatching {
            ApplicationEnforcementStatus.valueOf(applicationEnforcementStatus)
        }.getOrElse { throw IllegalStateException("Invalid persisted application enforcement status.") },
    )
}

private fun LocalApplicationState.toEntity(): LocalApplicationStateEntity =
    LocalApplicationStateEntity(
        stateVersion = stateVersion,
        lastSynchronizationTimestamp = lastSynchronizationTimestamp,
        initialized = initialized,
        installationId = installationId,
        identityCreatedAtEpochMillis = identityCreatedAtEpochMillis,
        enrollmentState = enrollmentState.name,
        connectionState = connectionState.name,
        managedDeviceId = managedDeviceId,
        managementMode = managementMode.name,
        managementCapabilities = encodeCapabilities(managementCapabilities),
        managementStateUpdatedAtEpochMillis = managementStateUpdatedAtEpochMillis,
        applicationInventorySyncStatus = applicationInventorySyncStatus.name,
        lastApplicationInventoryObservedAtEpochMillis = lastApplicationInventoryObservedAtEpochMillis,
        lastApplicationInventorySuccessfulSyncAtEpochMillis = lastApplicationInventorySuccessfulSyncAtEpochMillis,
        desiredApplicationPolicyId = desiredApplicationPolicyId,
        desiredApplicationPolicyVersion = desiredApplicationPolicyVersion,
        acceptedApplicationPolicyVersion = acceptedApplicationPolicyVersion,
        applicationPolicySyncStatus = applicationPolicySyncStatus.name,
        applicationEnforcementStatus = applicationEnforcementStatus.name,
    )


private fun isValidInstallationId(value: String): Boolean =
    runCatching { UUID.fromString(value) }.isSuccess


private fun encodeCapabilities(states: List<CapabilityState>): String =
    states.joinToString(";") { "${it.capability.name}:${it.status.name}" }

private fun decodeCapabilities(value: String): List<CapabilityState> =
    if (value.isBlank()) {
        emptyList()
    } else {
        value.split(";").map { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) throw IllegalStateException("Invalid persisted capability state.")
            CapabilityState(
                capability = runCatching { DeviceManagementCapability.valueOf(parts[0]) }
                    .getOrElse { throw IllegalStateException("Invalid persisted capability.") },
                status = runCatching { CapabilityStatus.valueOf(parts[1]) }
                    .getOrElse { throw IllegalStateException("Invalid persisted capability status.") },
            )
        }
    }
