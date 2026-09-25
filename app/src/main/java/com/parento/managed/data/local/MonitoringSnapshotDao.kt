package com.parento.managed.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSnapshotDao {
    @Query("SELECT * FROM monitoring_snapshot WHERE id = 1 LIMIT 1")
    suspend fun read(): MonitoringSnapshotEntity?

    @Query("SELECT * FROM monitoring_snapshot WHERE id = 1 LIMIT 1")
    fun observe(): Flow<MonitoringSnapshotEntity?>

    @Upsert
    suspend fun upsert(snapshot: MonitoringSnapshotEntity)

    @Query("DELETE FROM monitoring_snapshot")
    suspend fun clear()
}
