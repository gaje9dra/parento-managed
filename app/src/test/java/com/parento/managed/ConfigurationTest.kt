package com.parento.managed

import com.parento.managed.config.AppConfig
import com.parento.managed.config.AppEnvironment
import com.parento.managed.config.FeatureFlags
import com.parento.managed.config.LoggingConfig
import com.parento.managed.config.SecurityConfig
import com.parento.managed.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationTest {
    private fun config(
        environment: AppEnvironment = AppEnvironment.DEVELOPMENT,
        backendBaseUrl: String = "https://backend.example.invalid",
        requireHttps: Boolean = false,
    ) = AppConfig(
        environment = environment,
        backendBaseUrl = backendBaseUrl,
        logging = LoggingConfig(LogLevel.DEBUG, enabled = true),
        featureFlags = FeatureFlags(),
        security = SecurityConfig(
            requireHttps = requireHttps,
            allowDebugDiagnostics = environment == AppEnvironment.DEVELOPMENT,
        ),
    )

    @Test
    fun validConfigurationLoads() {
        val result = config()
        assertEquals(AppEnvironment.DEVELOPMENT, result.environment)
        assertEquals("https://backend.example.invalid", result.backendBaseUrl)
    }

    @Test
    fun environmentsRemainDistinct() {
        assertTrue(config(AppEnvironment.DEVELOPMENT).isDevelopment)
        assertTrue(config(AppEnvironment.TEST).isTest)
        assertTrue(config(AppEnvironment.PRODUCTION, requireHttps = true).isProduction)
    }

    @Test
    fun invalidBackendUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "not-a-url")
        }
    }

    @Test
    fun productionRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            config(
                environment = AppEnvironment.PRODUCTION,
                backendBaseUrl = "http://backend.example.invalid",
                requireHttps = true,
            )
        }
    }

    @Test
    fun backendUrlRejectsEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "https://user:password@backend.example.invalid")
        }
    }

    @Test
    fun backendUrlRejectsUnexpectedPath() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "https://backend.example.invalid/api")
        }
    }

    @Test
    fun productionDoesNotAllowDebugDiagnostics() {
        val result = config(AppEnvironment.PRODUCTION, requireHttps = true)
        assertFalse(result.security.allowDebugDiagnostics && result.isProduction)
    }

    @Test
    fun productionLoggingIsExpectedToBeRestricted() {
        val result = config(AppEnvironment.PRODUCTION, requireHttps = true)
            .copy(logging = LoggingConfig(LogLevel.WARN, enabled = true))
        assertEquals(LogLevel.WARN, result.logging.minimumLevel)
        assertTrue(result.logging.enabled)
    }

    @Test
    fun featureFlagsDefaultToDisabled() {
        assertFalse(config().featureFlags.enrollment)
        assertFalse(config().featureFlags.location)
        assertFalse(config().featureFlags.screenSharing)
        assertFalse(config().featureFlags.audio)
        assertFalse(config().featureFlags.applicationManagement)
        assertFalse(config().featureFlags.websiteFiltering)
        assertFalse(config().featureFlags.deviceRestrictions)
    }
}
