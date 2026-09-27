package com.parento.managed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_policy_state")
data class NetworkPolicyStateEntity(
    @PrimaryKey val id: Int = 1,
    val desiredPolicyId: String? = null,
    val desiredPolicyVersion: Long? = null,
    val desiredPolicyJson: String? = null,
    val appliedPolicyId: String? = null,
    val appliedPolicyVersion: Long? = null,
    val enforcementStatus: String = "UNKNOWN",
    val capabilityMode: String = "UNKNOWN",
    val capabilitySupported: Boolean = false,
    val capabilityVersion: Int? = null,
    val lastSynchronizedAtEpochMillis: Long? = null,
    val pendingSynchronization: Boolean = false,
    val lastErrorCode: String? = null,
)
