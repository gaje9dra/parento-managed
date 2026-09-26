package com.parento.managed.application

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parento.managed.ParentoApplication
import com.parento.managed.background.BackgroundRetryPolicy
import com.parento.managed.background.BackgroundWorkFailureClass
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class ApplicationInventoryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? ParentoApplication ?: return Result.failure()
        val retryPolicy = BackgroundRetryPolicy()
        if (!application.ensureConnectedForBackgroundWork()) {
            return if (retryPolicy.decide(
                    BackgroundWorkFailureClass.TRANSIENT,
                    runAttemptCount,
                ).retry
            ) Result.retry() else Result.failure()
        }

        return when (val result = application.applicationInventorySync.syncNow()) {
            is OperationResult.Success -> Result.success()
            is OperationResult.Failure -> {
                val failureClass = when (result.error) {
                    ManagedError.NETWORK_FAILURE,
                    ManagedError.AUTHENTICATION_FAILURE,
                    ManagedError.UNKNOWN,
                    -> BackgroundWorkFailureClass.TRANSIENT
                    ManagedError.AUTHORIZATION_FAILURE -> BackgroundWorkFailureClass.AUTHORIZATION
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
