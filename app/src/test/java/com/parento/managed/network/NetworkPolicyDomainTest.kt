package com.parento.managed.network

import com.parento.managed.device.DeviceManagementManager
import com.parento.managed.device.ManagementDetectionResult
import com.parento.managed.device.ManagementMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyDomainTest {
    @Test
    fun normalizesExactAndWildcardDomainsWithoutChangingSemantics() {
        assertTrue(NetworkPolicyDomain.normalize(" Example.COM ") == "example.com")
        assertTrue(NetworkPolicyDomain.normalize("*.Example.COM") == "*.example.com")
        assertTrue(NetworkPolicyDomain.matches("*.example.com", "www.example.com"))
        assertTrue(NetworkPolicyDomain.matches("*.example.com", "deep.www.example.com"))
        assertFalse(NetworkPolicyDomain.matches("*.example.com", "example.com"))
    }

    @Test
    fun rejectsMalformedDomainsAndDuplicateRules() {
        assertTrue(NetworkPolicyDomain.normalize("https://example.com") == null)
        assertTrue(NetworkPolicyDomain.normalize("example..com") == null)
        assertTrue(NetworkPolicyDomain.normalize("foo.*.example.com") == null)
        assertFalse(
            NetworkPolicyDomain.validateRules(
                listOf(
                    NetworkPolicyRule("1", "example.com", NetworkRuleAction.BLOCK, true),
                    NetworkPolicyRule("2", "EXAMPLE.COM", NetworkRuleAction.ALLOW, true),
                ),
            ),
        )
    }

    @Test
    fun rejectsHostnamesLongerThanBackendMaximum() {
        val label = "a".repeat(63)
        val tooLong = listOf(label, label, label, "com").joinToString(".")
        assertTrue(tooLong.length > 253)
        assertTrue(NetworkPolicyDomain.normalize(tooLong) == null)
    }

    @Test
    fun androidEnforcerFailsClosedWhenManagementModeIsKnown() {
        val manager = FakeManagementManager(ManagementDetectionResult(ManagementMode.DEVICE_OWNER))
        val enforcer = AndroidNetworkPolicyEnforcer(manager)
        val capability = enforcer.capability()

        assertFalse(capability.supported)
        assertTrue(capability.mode == NetworkCapabilityMode.UNSUPPORTED)
        assertTrue(
            enforcer.apply(
                NetworkPolicy(
                    policyId = "policy",
                    version = 1L,
                    status = NetworkPolicyStatus.ACTIVE,
                    rules = listOf(
                        NetworkPolicyRule("rule", "example.com", NetworkRuleAction.BLOCK, true),
                    ),
                    receivedAtEpochMillis = 1L,
                ),
            ).status == NetworkEnforcementStatus.UNSUPPORTED,
        )
    }

    @Test
    fun policySerializationIsDeterministicAndEscapesJsonStrings() {
        val policy = NetworkPolicy(
            policyId = "550e8400-e29b-41d4-a716-446655440000",
            version = 2L,
            status = NetworkPolicyStatus.ACTIVE,
            name = "A \"policy\"",
            description = "line\nnext",
            rules = listOf(
                NetworkPolicyRule("550e8400-e29b-41d4-a716-446655440001", "example.com", NetworkRuleAction.BLOCK, true),
            ),
            receivedAtEpochMillis = 1L,
        )
        val json = NetworkPolicyJson.encode(policy)
        assertTrue(json.contains("\\\"policy\\\""))
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\"version\":2"))
    }

    private class FakeManagementManager(
        private val detection: ManagementDetectionResult,
    ) : DeviceManagementManager {
        override fun detectManagementMode(): ManagementMode = detection.mode
        override fun detectManagementState(): ManagementDetectionResult = detection
        override fun evaluateCapabilities(
            detection: ManagementDetectionResult,
        ) = emptyList<com.parento.managed.device.CapabilityState>()
    }
}
