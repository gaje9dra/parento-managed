package com.parento.managed.network

import org.json.JSONArray
import org.json.JSONObject

object NetworkPolicyJson {
    fun encode(policy: NetworkPolicy): String =
        JSONObject()
            .put("policyId", policy.policyId)
            .put("version", policy.version)
            .put("status", policy.status.name)
            .put("name", policy.name)
            .put("description", policy.description)
            .put(
                "rules",
                JSONArray().apply {
                    policy.rules.forEach {
                        put(
                            JSONObject()
                                .put("ruleId", it.ruleId)
                                .put("domain", it.domain)
                                .put("action", it.action.name)
                                .put("enabled", it.enabled),
                        )
                    }
                },
            )
            .toString()
}
