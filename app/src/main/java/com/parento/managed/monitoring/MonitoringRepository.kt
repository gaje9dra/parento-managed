package com.parento.managed.monitoring

import com.parento.managed.data.local.MonitoringSnapshotDao
import com.parento.managed.data.local.toDomain
import com.parento.managed.data.local.toEntity
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MonitoringRepository {
    suspend fun read(): OperationResult<MonitoringSnapshot?>
    suspend fun save(snapshot: MonitoringSnapshot): OperationResult<Unit>
    fun observe(): Flow<OperationResult<MonitoringSnapshot?>>
}

class RoomMonitoringRepository(
    private val dao: MonitoringSnapshotDao,
) : MonitoringRepository {
    override suspend fun read(): OperationResult<MonitoringSnapshot?> = runCatching {
        OperationResult.Success(dao.read()?.toDomain())
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override suspend fun save(snapshot: MonitoringSnapshot): OperationResult<Unit> = runCatching {
        dao.upsert(snapshot.toEntity())
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun observe(): Flow<OperationResult<MonitoringSnapshot?>> = dao.observe().map {
        try {
            OperationResult.Success<MonitoringSnapshot?>(it?.toDomain())
        } catch (_: Exception) {
            OperationResult.Failure(ManagedError.STORAGE_FAILURE)
        }
    }
}
