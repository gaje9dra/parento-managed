package com.parento.managed.data

import com.parento.managed.data.local.LocalApplicationStateDao
import com.parento.managed.data.local.LocalApplicationStateEntity
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface LocalStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>
    suspend fun write(state: LocalApplicationState): OperationResult<Unit>
    suspend fun clear(): OperationResult<Unit>
    fun observe(): Flow<OperationResult<LocalApplicationState?>>
    suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity>
    suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit>
    suspend fun updateConnectionState(state: ConnectionState): OperationResult<Unit>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation { dao.read()?.toDomain() }

    override suspend fun write(
        state: LocalApplicationState,
    ): OperationResult<Unit> =
        runStorageOperation { dao.upsert(state.toEntity()) }

    override suspend fun clear(): OperationResult<Unit> =
        runStorageOperation { dao.clear() }

    override fun observe(): Flow<OperationResult<LocalApplicationState?>> =
        dao.observe().map { entity ->
            try {
                OperationResult.Success(entity?.toDomain())
            } catch (_: Exception) {
                OperationResult.Failure(ManagedError.STORAGE_FAILURE)
            }
        }

    override suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity> =
        runStorageOperation {
            val existing = dao.read()
            val existingId = existing?.installationId
            val existingCreatedAt = existing?.identityCreatedAtEpochMillis
            if (!existingId.isNullOrBlank() && existingCreatedAt != null) {
                return@runStorageOperation LocalDeviceIdentity(existingId, existingCreatedAt)
            }

            val now = System.currentTimeMillis()
            val identity = LocalDeviceIdentity(UUID.randomUUID().toString(), now)
            val base = existing?.toDomain() ?: LocalApplicationState()
            dao.upsert(
                base.copy(
                    installationId = identity.installationId,
                    identityCreatedAtEpochMillis = identity.createdAtEpochMillis,
                ).toEntity(),
            )
            identity
        }

    override suspend fun updateEnrollmentState(
        state: EnrollmentState,
    ): OperationResult<Unit> =
        updateState { copy(enrollmentState = state) }

    override suspend fun updateConnectionState(
        state: ConnectionState,
    ): OperationResult<Unit> =
        updateState { copy(connectionState = state) }

    private suspend fun updateState(
        transform: LocalApplicationState.() -> LocalApplicationState,
    ): OperationResult<Unit> =
        runStorageOperation {
            val current = dao.read()?.toDomain() ?: LocalApplicationState()
            dao.upsert(current.transform().toEntity())
        }

    private suspend fun <T> runStorageOperation(
        operation: suspend () -> T,
    ): OperationResult<T> =
        try {
            OperationResult.Success(operation())
        } catch (_: Exception) {
            OperationResult.Failure(ManagedError.STORAGE_FAILURE)
        }
}

private fun LocalApplicationStateEntity.toDomain() =
    LocalApplicationState(
        stateVersion = stateVersion,
        lastSynchronizationTimestamp = lastSynchronizationTimestamp,
        initialized = initialized,
        installationId = installationId,
        identityCreatedAtEpochMillis = identityCreatedAtEpochMillis,
        enrollmentState = EnrollmentState.valueOf(enrollmentState),
        connectionState = ConnectionState.valueOf(connectionState),
    )

private fun LocalApplicationState.toEntity() =
    LocalApplicationStateEntity(
        stateVersion = stateVersion,
        lastSynchronizationTimestamp = lastSynchronizationTimestamp,
        initialized = initialized,
        installationId = installationId,
        identityCreatedAtEpochMillis = identityCreatedAtEpochMillis,
        enrollmentState = enrollmentState.name,
        connectionState = connectionState.name,
    )
