package com.parento.managed.network

enum class NetworkRuleAction { ALLOW, BLOCK }

enum class NetworkPolicyStatus { ACTIVE, DISABLED }

enum class NetworkEnforcementStatus {
    UNKNOWN, PENDING, APPLIED, PARTIALLY_APPLIED, FAILED, UNSUPPORTED, STALE, REVOKED,
}

enum class NetworkCapabilityMode { UNKNOWN, UNSUPPORTED, SUPPORTED }

data class NetworkPolicyRule(
    val ruleId: String,
    val domain: String,
    val action: NetworkRuleAction,
    val enabled: Boolean,
)

data class NetworkPolicy(
    val policyId: String,
    val version: Long,
    val status: NetworkPolicyStatus,
    val name: String? = null,
    val description: String? = null,
    val rules: List<NetworkPolicyRule>,
    val receivedAtEpochMillis: Long,
)

data class NetworkPolicyCapability(
    val supported: Boolean,
    val mode: NetworkCapabilityMode,
    val capabilityVersion: Int?,
    val reportedAtEpochMillis: Long,
)

data class NetworkPolicyEnforcementResult(
    val status: NetworkEnforcementStatus,
    val applied: Boolean,
    val errorCode: String? = null,
)
