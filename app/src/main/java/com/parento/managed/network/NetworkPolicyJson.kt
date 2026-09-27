package com.parento.managed.network

object NetworkPolicyJson {
    fun encode(policy: NetworkPolicy): String = buildString {
        append('{')
        appendField("policyId", policy.policyId)
        append(',')
        append("\"version\":").append(policy.version)
        append(',')
        appendField("status", policy.status.name)
        if (policy.name != null) {
            append(',')
            appendField("name", policy.name)
        }
        if (policy.description != null) {
            append(',')
            appendField("description", policy.description)
        }
        append(",\"rules\":[")
        policy.rules.forEachIndexed { index, rule ->
            if (index > 0) append(',')
            append('{')
            appendField("ruleId", rule.ruleId)
            append(',')
            appendField("domain", rule.domain)
            append(',')
            appendField("action", rule.action.name)
            append(",\"enabled\":").append(rule.enabled)
            append('}')
        }
        append("]}")
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append('\"').append(escape(name)).append("\":\"").append(escape(value)).append('\"')
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
