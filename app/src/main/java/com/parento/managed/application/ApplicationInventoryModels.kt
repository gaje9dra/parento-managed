package com.parento.managed.application

enum class ApplicationInventorySyncStatus {
    NEVER_SYNCED,
    SYNCING,
    SYNCED,
    FAILED,
}

enum class ApplicationPolicySyncStatus {
    NONE,
    PENDING,
    APPLIED,
    STALE,
    FAILED,
    UNAVAILABLE,
}

enum class ApplicationEnforcementStatus {
    UNKNOWN,
    PENDING,
    APPLIED,
    PARTIALLY_APPLIED,
    FAILED,
    STALE,
}

data class InstalledApplication(
    val packageName: String,
    val displayName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val enabled: Boolean?,
    val observedAtEpochMillis: Long,
)

data class ApplicationInventory(
    val synchronizationId: String,
    val observedAtEpochMillis: Long,
    val applications: List<InstalledApplication>,
)

data class ApplicationInventorySyncResult(
    val synchronizationId: String,
    val applicationCount: Int,
    val successfulAtEpochMillis: Long,
)

data class ApplicationPolicyReference(
    val policyId: String,
    val policyVersion: Int,
    val status: ApplicationPolicySyncStatus,
)
