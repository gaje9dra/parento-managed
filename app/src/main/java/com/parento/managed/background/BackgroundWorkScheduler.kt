package com.parento.managed.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

data class BackgroundWorkPolicy(
    val requiresNetwork: Boolean = false,
    val requiresCharging: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
)

interface BackgroundWorkScheduler {
    fun schedule(
        uniqueName: String,
        workerClass: Class<out ListenableWorker>,
        policy: BackgroundWorkPolicy = BackgroundWorkPolicy(),
    )

    fun cancel(uniqueName: String)
}

class WorkManagerBackgroundWorkScheduler(
    context: Context,
) : BackgroundWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(
        uniqueName: String,
        workerClass: Class<out ListenableWorker>,
        policy: BackgroundWorkPolicy,
    ) {
        require(uniqueName.isNotBlank()) { "uniqueName must not be blank." }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (policy.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED,
            )
            .setRequiresCharging(policy.requiresCharging)
            .setRequiresStorageNotLow(policy.requiresStorageNotLow)
            .build()

        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(uniqueName: String) {
        workManager.cancelUniqueWork(uniqueName)
    }
}