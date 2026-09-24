package com.parento.managed.domain

/**
 * Presentation status derived from enrollment state.
 *
 * Connection state is intentionally modeled separately by [ConnectionState].
 */
enum class DeviceStatus {
    UNENROLLED,
    ENROLLING,
    ENROLLED,
    REVOKED,
    ERROR,
}
