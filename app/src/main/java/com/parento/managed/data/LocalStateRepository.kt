package com.parento.managed.data

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.data.local.LocalApplicationStateDao
import com.parento.managed.data.local.LocalApplicationStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LocalApplicationState(
    val stateVersion: Int = 1,
    val lastSynchronizationTimestamp: Long? = null,
    val initialized: Boolean = false,
)

interface LocalStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>
    suspend fun write(state: LocalApplicationState): OperationResult<Unit>
    suspend fun clear(): OperationResult<Unit>
    fun observe(): Flow<OperationResult<LocalApplicationState?>>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation {
            dao.read()?.toDomain()
        }

    override suspend fun write(
        state: LocalApplicationState,
    ): OperationResult<Unit> =
        runStorageOperation {
            dao.upsert(state.toEntity())
        }

    override suspend fun clear(): OperationResult<Unit> =
        runStorageOperation {
            dao.clear()
        }

    override fun observe(): Flow<OperationResult<LocalApplicationState?>> =
        dao.observe().map { entity ->
            try {
                OperationResult.Success(entity?.toDomain())
            } catch (_: Exception) {
                OperationResult.Failure(ManagedError.STORAGE_FAILURE)
            }
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
    )

private fun LocalApplicationState.toEntity() =
    LocalApplicationStateEntity(
        stateVersion = stateVersion,
        lastSynchronizationTimestamp = lastSynchronizationTimestamp,
        initialized = initialized,
    )
