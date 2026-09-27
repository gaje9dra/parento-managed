package com.parento.managed.network

import com.parento.managed.device.DeviceManagementManager
import com.parento.managed.device.ManagementMode

interface NetworkPolicyEnforcer {
    fun capability(): NetworkPolicyCapability
    fun apply(policy: NetworkPolicy): NetworkPolicyEnforcementResult
    fun remove(policyId: String?): NetworkPolicyEnforcementResult
}

class AndroidNetworkPolicyEnforcer(
    private val deviceManagementManager: DeviceManagementManager,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : NetworkPolicyEnforcer {
    override fun capability(): NetworkPolicyCapability {
        val detection = deviceManagementManager.detectManagementState()
        val mode = when (detection.error) {
            null -> when (detection.mode) {
                ManagementMode.DEVICE_OWNER,
                ManagementMode.PROFILE_OWNER,
                ManagementMode.NOT_MANAGED -> NetworkCapabilityMode.UNSUPPORTED
                ManagementMode.UNKNOWN -> NetworkCapabilityMode.UNKNOWN
            }
            else -> NetworkCapabilityMode.UNKNOWN
        }
        return NetworkPolicyCapability(
            supported = false,
            mode = mode,
            capabilityVersion = if (mode == NetworkCapabilityMode.UNKNOWN) null else 1,
            reportedAtEpochMillis = nowEpochMillis(),
        )
    }

    override fun apply(policy: NetworkPolicy): NetworkPolicyEnforcementResult {
        if (!NetworkPolicyDomain.validateRules(policy.rules)) {
            return NetworkPolicyEnforcementResult(
                NetworkEnforcementStatus.FAILED,
                applied = false,
                errorCode = "INVALID_POLICY",
            )
        }
        return NetworkPolicyEnforcementResult(
            NetworkEnforcementStatus.UNSUPPORTED,
            applied = false,
            errorCode = "ANDROID_NETWORK_POLICY_UNSUPPORTED",
        )
    }

    override fun remove(policyId: String?): NetworkPolicyEnforcementResult =
        NetworkPolicyEnforcementResult(
            NetworkEnforcementStatus.UNSUPPORTED,
            applied = false,
            errorCode = "ANDROID_NETWORK_POLICY_UNSUPPORTED",
        )
}
