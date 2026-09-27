package com.parento.managed.application

import com.parento.managed.device.ManagementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationPolicyEnforcementTest {
    @Test
    fun blockRuleSuspendsOnlyPolicyManagedPackage() {
        val platform = FakePlatform(
            installed = listOf(
                "com.example.block" to false,
                "com.example.other" to false,
                "com.parento.managed" to false,
            ),
        )
        val engine = ApplicationEnforcementEngine(platform, managedPackageName = "com.parento.managed")

        val outcome = engine.enforce(
            policyId = "00000000-0000-4000-8000-000000000001",
            policyVersion = 2,
            rules = listOf(ApplicationPolicyRule("com.example.block", ApplicationDesiredAction.BLOCK)),
        )

        assertEquals(ApplicationEnforcementStatus.APPLIED, outcome.status)
        assertTrue("com.example.block" in platform.suspended)
        assertTrue("com.example.other" !in platform.suspended)
        assertTrue("com.parento.managed" !in platform.suspended)
    }

    @Test
    fun policyRemovalOnlyUnsuspendsPackagesPreviouslyManagedByParento() {
        val platform = FakePlatform(
            installed = listOf(
                "com.example.block" to true,
                "com.example.other" to true,
            ),
        )
        val engine = ApplicationEnforcementEngine(platform, managedPackageName = "com.parento.managed")

        val outcome = engine.enforce(
            policyId = null,
            policyVersion = null,
            rules = emptyList(),
            previouslyManagedBlockedPackages = setOf("com.example.block"),
        )

        assertEquals(ApplicationEnforcementStatus.APPLIED, outcome.status)
        assertTrue("com.example.block" !in platform.suspended)
        assertTrue("com.example.other" in platform.suspended)
    }

    @Test
    fun unmanagedDeviceFailsClosed() {
        val platform = FakePlatform(
            installed = listOf("com.example.block" to false),
            mode = ManagementMode.NOT_MANAGED,
        )
        val engine = ApplicationEnforcementEngine(platform, managedPackageName = "com.parento.managed")

        val outcome = engine.enforce(
            policyId = "00000000-0000-4000-8000-000000000001",
            policyVersion = 1,
            rules = listOf(ApplicationPolicyRule("com.example.block", ApplicationDesiredAction.BLOCK)),
        )

        assertEquals(ApplicationEnforcementStatus.FAILED, outcome.status)
        assertEquals("UNSUPPORTED_MANAGEMENT_MODE", outcome.errorCode)
        assertTrue(platform.suspended.isEmpty())
    }

    @Test
    fun duplicateConflictingRulesFailWithoutChangingPackages() {
        val platform = FakePlatform(
            installed = listOf("com.example.block" to false),
        )
        val engine = ApplicationEnforcementEngine(platform, managedPackageName = "com.parento.managed")

        val outcome = engine.enforce(
            policyId = "00000000-0000-4000-8000-000000000001",
            policyVersion = 1,
            rules = listOf(
                ApplicationPolicyRule("com.example.block", ApplicationDesiredAction.BLOCK),
                ApplicationPolicyRule("com.example.block", ApplicationDesiredAction.ALLOW),
            ),
        )

        assertEquals(ApplicationEnforcementStatus.FAILED, outcome.status)
        assertEquals("INVALID_POLICY", outcome.errorCode)
        assertTrue(platform.suspended.isEmpty())
    }

    private class FakePlatform(
        installed: List<Pair<String, Boolean>>,
        private val mode: ManagementMode = ManagementMode.DEVICE_OWNER,
    ) : ApplicationEnforcementPlatform {
        private val installedState = installed.toMutableMap()
        val suspended = mutableSetOf<String>()

        init {
            installedState.filterValues { it }.keys.forEach { suspended += it }
        }

        override fun managementMode(): ManagementMode = mode

        override fun installedPackagesWithSuspensionState(): List<Pair<String, Boolean>> =
            installedState.keys.sorted().map { it to (it in suspended) }

        override fun setSuspended(packageName: String, suspended: Boolean): Boolean {
            if (!installedState.containsKey(packageName)) return false
            if (suspended) this.suspended += packageName else this.suspended -= packageName
            return true
        }
    }
}
