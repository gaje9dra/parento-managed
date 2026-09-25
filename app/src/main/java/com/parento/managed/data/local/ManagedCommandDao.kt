package com.parento.managed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ManagedCommandDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(command: ManagedCommandEntity): Long

    @Query("SELECT * FROM managed_commands WHERE commandId = :commandId LIMIT 1")
    suspend fun find(commandId: String): ManagedCommandEntity?

    @Query("""
        UPDATE managed_commands
        SET state = :state,
            resultCode = :resultCode,
            errorCategory = :errorCategory,
            resultMetadataJson = :resultMetadataJson,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE commandId = :commandId
    """)
    suspend fun updateState(
        commandId: String,
        state: String,
        resultCode: String?,
        errorCategory: String?,
        resultMetadataJson: String?,
        updatedAtEpochMillis: Long,
    )

    @Query("DELETE FROM managed_commands WHERE updatedAtEpochMillis < :beforeEpochMillis")
    suspend fun deleteOlderThan(beforeEpochMillis: Long)
}
