package com.parento.managed.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parento.managed.ParentoApplication
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as ParentoApplication
        val enrollment = app.localStateRepository.getEnrollmentState()
        if (enrollment !is OperationResult.Success || enrollment.value != EnrollmentState.ENROLLED) return@withContext Result.success()

        if (!app.ensureConnectedForBackgroundWork()) return@withContext Result.retry()

        when (val result = app.locationCoordinator.collectAndReport()) {
            is OperationResult.Success -> when (result.value) {
                LocationCapabilityState.PERMISSION_REQUIRED,
                LocationCapabilityState.PERMISSION_DENIED,
                LocationCapabilityState.BACKGROUND_PERMISSION_REQUIRED,
                LocationCapabilityState.LOCATION_SERVICES_DISABLED,
                LocationCapabilityState.PROVIDER_UNAVAILABLE -> Result.success()
                LocationCapabilityState.TEMPORARILY_UNAVAILABLE -> Result.retry()
                LocationCapabilityState.AVAILABLE -> Result.success()
                LocationCapabilityState.ERROR -> Result.retry()
            }
            is OperationResult.Failure -> when (result.error) {
                com.parento.managed.domain.ManagedError.AUTHENTICATION_FAILURE,
                com.parento.managed.domain.ManagedError.AUTHORIZATION_FAILURE -> Result.success()
                else -> Result.retry()
            }
        }
    }
}
