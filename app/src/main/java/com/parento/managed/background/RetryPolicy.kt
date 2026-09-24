package com.parento.managed.background

import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

enum class BackgroundWorkFailureClass {
    TRANSIENT,
    PERMANENT,
    CONFIGURATION,
    AUTHORIZATION,
}

data class RetryDecision(
    val retry: Boolean,
)

class BackgroundRetryPolicy(
    private val maxAttempts: Int = 3,
) {
    init {
        require(maxAttempts in 1..5)
    }

    fun decide(
        failure: BackgroundWorkFailureClass,
        runAttemptCount: Int,
    ): RetryDecision =
        RetryDecision(
            retry = failure == BackgroundWorkFailureClass.TRANSIENT &&
                runAttemptCount < maxAttempts,
        )

    companion object {
        val workManagerBackoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
        val workManagerBackoffDelay: Pair<Long, TimeUnit> = 10L to TimeUnit.SECONDS
    }
}