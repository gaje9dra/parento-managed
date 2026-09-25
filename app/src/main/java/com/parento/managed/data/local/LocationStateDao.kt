package com.parento.managed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationStateDao {
    @Query("SELECT * FROM location_state WHERE id = 1 LIMIT 1")
    suspend fun read(): LocationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocationStateEntity)

    @Query("DELETE FROM location_state")
    suspend fun clear()
}
