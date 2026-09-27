package com.parento.managed.network

import java.util.Locale

object NetworkPolicyDomain {
    private const val MAX_HOSTNAME_LENGTH = 253
    private const val MAX_RULES = 500
    private val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val labelPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun normalize(input: String): String? {
        val value = input.trim().lowercase(Locale.ROOT)
        if (value.isEmpty() || value.length > MAX_HOSTNAME_LENGTH) return null
        val wildcard = value.startsWith("*.")
        if (value.contains("*") && !wildcard) return null
        val hostname = if (wildcard) value.removePrefix("*.") else value
        if (hostname.isEmpty() || hostname.contains("..") || hostname.contains('/') ||
            hostname.contains(':') || hostname.contains('?') || hostname.contains('#') ||
            hostname.any { it.isWhitespace() || it.isISOControl() }
        ) return null
        val labels = hostname.split('.')
        if (labels.size < 2 || labels.any { !labelPattern.matches(it) }) return null
        return if (wildcard) "*.$hostname" else hostname
    }

    fun matches(ruleDomain: String, hostname: String): Boolean {
        val rule = normalize(ruleDomain) ?: return false
        val candidate = normalize(hostname) ?: return false
        if (!rule.startsWith("*.")) return rule == candidate
        val suffix = rule.removePrefix("*.")
        return candidate.endsWith(".$suffix") && candidate != suffix
    }

    fun validateRules(rules: List<NetworkPolicyRule>): Boolean {
        if (rules.size > MAX_RULES) return false
        val normalized = rules.map { normalize(it.domain) ?: return false }
        return normalized.distinct().size == normalized.size &&
            rules.all { uuidPattern.matches(it.ruleId) }
    }
}
