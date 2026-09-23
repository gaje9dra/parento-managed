package com.parento.managed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_application_state")
data class LocalApplicationStateEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val stateVersion: Int = 1,
    val lastSynchronizationTimestamp: Long? = null,
    val initialized: Boolean = false,
    val installationId: String? = null,
    val identityCreatedAtEpochMillis: Long? = null,
    val enrollmentState: String = "UNENROLLED",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
