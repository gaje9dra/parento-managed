package com.parento.managed.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parento.managed.ParentoApplication
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MonitoringWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = LocalDatabaseProvider.get()
        val localState = RoomLocalStateRepository(database.localApplicationStateDao())
        val enrollment = localState.getEnrollmentState()
        if (enrollment !is OperationResult.Success ||
            enrollment.value != EnrollmentState.ENROLLED
        ) {
            return@withContext Result.success()
        }

        val managementDetector = com.parento.managed.device.AndroidDeviceManagementManager(
            com.parento.managed.device.AndroidDeviceManagementPlatform(applicationContext),
        )
        val managementProvider = AndroidManagementInfoProvider(managementDetector)
        val repository = RoomMonitoringRepository(database.monitoringSnapshotDao())
        val useCase = CollectDeviceMonitoringSnapshot(
            AndroidDeviceInfoProvider(applicationContext, localState, managementProvider),
            AndroidBatteryInfoProvider(applicationContext),
            AndroidNetworkInfoProvider(applicationContext),
            AndroidStorageInfoProvider(applicationContext),
            AndroidMemoryInfoProvider(applicationContext),
            managementProvider,
            localState,
            repository,
        )

        val snapshot = when (val collected = useCase.execute()) {
            is OperationResult.Failure -> return@withContext Result.retry()
            is OperationResult.Success -> collected.value
        }

        val application = applicationContext as? ParentoApplication
            ?: return@withContext Result.failure()

        val communication = application.deviceCommunicationSessionManager
        when (val connection = communication.ensureConnected()) {
            is OperationResult.Failure -> return@withContext Result.retry()
            is OperationResult.Success -> Unit
        }

        when (MonitoringTelemetryReporter(communication).submit(snapshot)) {
            is OperationResult.Success -> Result.success()
            is OperationResult.Failure -> Result.retry()
        }
    }
}
