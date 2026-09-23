package com.parento.managed.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalApplicationStateDao {
    @Query(
        "SELECT * FROM local_application_state WHERE id = 1 LIMIT 1",
    )
    fun observe(): Flow<LocalApplicationStateEntity?>

    @Query(
        "SELECT * FROM local_application_state WHERE id = 1 LIMIT 1",
    )
    suspend fun read(): LocalApplicationStateEntity?

    @Upsert
    suspend fun upsert(state: LocalApplicationStateEntity)

    @Query("DELETE FROM local_application_state")
    suspend fun clear()
}
