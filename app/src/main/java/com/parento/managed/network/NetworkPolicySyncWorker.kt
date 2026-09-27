package com.parento.managed.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parento.managed.ParentoApplication
import com.parento.managed.background.BackgroundRetryPolicy
import com.parento.managed.background.BackgroundWorkFailureClass
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class NetworkPolicySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? ParentoApplication ?: return Result.failure()
        val enrollment = application.localStateRepository.getEnrollmentState()
        if (enrollment !is OperationResult.Success || enrollment.value != EnrollmentState.ENROLLED) {
            return Result.success()
        }

        val retryPolicy = BackgroundRetryPolicy()
        if (!application.ensureConnectedForBackgroundWork()) {
            return if (retryPolicy.decide(
                    BackgroundWorkFailureClass.TRANSIENT,
                    runAttemptCount,
                ).retry
            ) Result.retry() else Result.failure()
        }

        return when (val result = application.networkPolicySynchronizer.synchronize(null, null)) {
            is OperationResult.Success -> Result.success()
            is OperationResult.Failure -> {
                val failureClass = when (result.error) {
                    ManagedError.NETWORK_FAILURE,
                    ManagedError.UNKNOWN,
                    -> BackgroundWorkFailureClass.TRANSIENT
                    ManagedError.AUTHENTICATION_FAILURE,
                    ManagedError.AUTHORIZATION_FAILURE,
                    -> BackgroundWorkFailureClass.AUTHORIZATION
                    else -> BackgroundWorkFailureClass.PERMANENT
                }
                if (retryPolicy.decide(failureClass, runAttemptCount).retry) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
