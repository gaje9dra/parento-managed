package com.parento.managed

import com.parento.managed.config.AppConfig
import com.parento.managed.config.AppEnvironment
import com.parento.managed.config.FeatureFlags
import com.parento.managed.config.LoggingConfig
import com.parento.managed.config.SecurityConfig
import com.parento.managed.logging.LogLevel
import com.parento.managed.security.securityConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    private fun config(environment: AppEnvironment, allowDiagnostics: Boolean) =
        AppConfig(
            environment = environment,
            backendBaseUrl = "https://backend.example.invalid",
            logging = LoggingConfig(LogLevel.WARN, enabled = true),
            featureFlags = FeatureFlags(),
            security = SecurityConfig(
                requireHttps = environment == AppEnvironment.PRODUCTION,
                allowDebugDiagnostics = allowDiagnostics,
            ),
        )

    @Test
    fun developmentCanEnableDiagnostics() {
        val security = config(AppEnvironment.DEVELOPMENT, allowDiagnostics = true)
            .securityConfiguration()

        assertFalse(security.requireHttps)
        assertTrue(security.debugDiagnosticsAllowed)
    }

    @Test
    fun testAndProductionCannotEnableDebugDiagnostics() {
        val testSecurity = config(AppEnvironment.TEST, allowDiagnostics = true)
            .securityConfiguration()
        val productionSecurity = config(AppEnvironment.PRODUCTION, allowDiagnostics = true)
            .securityConfiguration()

        assertFalse(testSecurity.debugDiagnosticsAllowed)
        assertTrue(productionSecurity.requireHttps)
        assertFalse(productionSecurity.debugDiagnosticsAllowed)
    }
}
