package com.parento.managed.config

import java.net.URI
import com.parento.managed.logging.LogLevel

data class LoggingConfig(
    val minimumLevel: LogLevel,
    val enabled: Boolean,
)

data class FeatureFlags(
    val enrollment: Boolean = false,
    val realtimeCommunication: Boolean = false,
    val location: Boolean = false,
    val screenSharing: Boolean = false,
    val audio: Boolean = false,
    val applicationManagement: Boolean = false,
    val websiteFiltering: Boolean = false,
    val deviceRestrictions: Boolean = false,
)

data class SecurityConfig(
    val requireHttps: Boolean,
    val allowDebugDiagnostics: Boolean,
)

data class AppConfig(
    val environment: AppEnvironment,
    val backendBaseUrl: String,
    val logging: LoggingConfig,
    val featureFlags: FeatureFlags,
    val security: SecurityConfig,
) {
    init {
        require(backendBaseUrl.isNotBlank()) { "Backend base URL must not be blank." }
        val uri = URI.create(backendBaseUrl)
        require(uri.scheme == "https" || uri.scheme == "http") {
            "Backend base URL must use HTTP(S)."
        }
        require(uri.host != null) { "Backend base URL must contain a host." }
        if (security.requireHttps) {
            require(uri.scheme == "https") {
                "Production backend base URL must use HTTPS."
            }
        }
    }

    val isDevelopment: Boolean
        get() = environment == AppEnvironment.DEVELOPMENT

    val isTest: Boolean
        get() = environment == AppEnvironment.TEST

    val isProduction: Boolean
        get() = environment == AppEnvironment.PRODUCTION
}
