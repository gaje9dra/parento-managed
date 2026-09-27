package com.parento.managed.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkPolicyStateDao {
    @Query("SELECT * FROM network_policy_state WHERE id = 1 LIMIT 1")
    suspend fun read(): NetworkPolicyStateEntity?

    @Query("SELECT * FROM network_policy_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<NetworkPolicyStateEntity?>

    @Upsert
    suspend fun upsert(state: NetworkPolicyStateEntity)

    @Query("DELETE FROM network_policy_state")
    suspend fun clear()
}
