package com.parento.managed.network

data class LocalNetworkPolicyState(
    val desiredPolicyId: String? = null,
    val desiredPolicyVersion: Long? = null,
    val desiredPolicyJson: String? = null,
    val appliedPolicyId: String? = null,
    val appliedPolicyVersion: Long? = null,
    val enforcementStatus: NetworkEnforcementStatus = NetworkEnforcementStatus.UNKNOWN,
    val capabilityMode: NetworkCapabilityMode = NetworkCapabilityMode.UNKNOWN,
    val capabilitySupported: Boolean = false,
    val capabilityVersion: Int? = null,
    val lastSynchronizedAtEpochMillis: Long? = null,
    val pendingSynchronization: Boolean = false,
    val lastErrorCode: String? = null,
)
