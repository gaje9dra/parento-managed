package com.parento.managed.security

import com.parento.managed.config.AppConfig

data class SecurityConfiguration(
    val requireHttps: Boolean,
    val debugDiagnosticsAllowed: Boolean,
)

fun AppConfig.securityConfiguration(): SecurityConfiguration =
    SecurityConfiguration(
        requireHttps = security.requireHttps,
        debugDiagnosticsAllowed = security.allowDebugDiagnostics && isDevelopment,
    )
