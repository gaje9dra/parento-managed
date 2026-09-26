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
    val connectionState: String = "UNKNOWN",
    val managedDeviceId: String? = null,
    val managementMode: String = "NOT_MANAGED",
    val managementCapabilities: String = "",
    val managementStateUpdatedAtEpochMillis: Long? = null,
    val applicationInventorySyncStatus: String = "NEVER_SYNCED",
    val lastApplicationInventoryObservedAtEpochMillis: Long? = null,
    val lastApplicationInventorySuccessfulSyncAtEpochMillis: Long? = null,
    val desiredApplicationPolicyId: String? = null,
    val desiredApplicationPolicyVersion: Int? = null,
    val acceptedApplicationPolicyVersion: Int? = null,
    val applicationPolicySyncStatus: String = "NONE",
    val applicationEnforcementStatus: String = "UNKNOWN",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
