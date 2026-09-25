package com.parento.managed.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.LocalDatabaseProvider
import com.parento.managed.device.AndroidDeviceManagementManager
import com.parento.managed.device.AndroidDeviceManagementPlatform
import com.parento.managed.domain.EnrollmentState
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
        if (enrollment !is com.parento.managed.domain.OperationResult.Success ||
            enrollment.value != EnrollmentState.ENROLLED
        ) return@withContext Result.success()

        val managementDetector = AndroidDeviceManagementManager(
            AndroidDeviceManagementPlatform(applicationContext),
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
        when (useCase.execute()) {
            is com.parento.managed.domain.OperationResult.Success -> Result.success()
            is com.parento.managed.domain.OperationResult.Failure -> Result.retry()
        }
    }
}
