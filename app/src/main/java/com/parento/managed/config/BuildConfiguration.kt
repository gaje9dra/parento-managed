package com.parento.managed.config

import com.parento.managed.BuildConfig
import com.parento.managed.logging.LogLevel

object BuildConfiguration {
    fun load(): AppConfig {
        val environment = when (BuildConfig.PARENTO_ENVIRONMENT) {
            "development" -> AppEnvironment.DEVELOPMENT
            "test" -> AppEnvironment.TEST
            "production" -> AppEnvironment.PRODUCTION
            else -> error("Unsupported Parento environment configuration.")
        }

        val logLevel = when (BuildConfig.PARENTO_LOG_LEVEL.uppercase()) {
            "DEBUG" -> LogLevel.DEBUG
            "INFO" -> LogLevel.INFO
            "WARN", "WARNING" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            else -> error("Unsupported Parento log level configuration.")
        }

        return AppConfig(
            environment = environment,
            backendBaseUrl = BuildConfig.PARENTO_BACKEND_BASE_URL,
            logging = LoggingConfig(
                minimumLevel = logLevel,
                enabled = BuildConfig.PARENTO_LOGGING_ENABLED,
            ),
            featureFlags = FeatureFlags(
                enrollment = BuildConfig.PARENTO_FEATURE_ENROLLMENT,
                realtimeCommunication = BuildConfig.PARENTO_FEATURE_REALTIME,
                location = BuildConfig.PARENTO_FEATURE_LOCATION,
                screenSharing = BuildConfig.PARENTO_FEATURE_SCREEN_SHARING,
                audio = BuildConfig.PARENTO_FEATURE_AUDIO,
                applicationManagement = BuildConfig.PARENTO_FEATURE_APPLICATION_MANAGEMENT,
                websiteFiltering = BuildConfig.PARENTO_FEATURE_WEBSITE_FILTERING,
                deviceRestrictions = BuildConfig.PARENTO_FEATURE_DEVICE_RESTRICTIONS,
            ),
            security = SecurityConfig(
                requireHttps = BuildConfig.PARENTO_REQUIRE_HTTPS,
                allowDebugDiagnostics = BuildConfig.PARENTO_DEBUG_DIAGNOSTICS,
            ),
        )
    }
}
