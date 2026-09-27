package com.parento.managed.application

private val PACKAGE_NAME_PATTERN =
    Regex("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$")

fun normalizeApplicationPackageName(value: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty() || normalized.length > 255) return null
    if (normalized.any(Char::isISOControl)) return null
    return normalized.takeIf { PACKAGE_NAME_PATTERN.matches(it) }
}
