package com.parento.managed.monitoring

import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

class MonitoringTelemetryReporter(
    private val communicationSessionManager: DeviceCommunicationSessionManager,
) {
    suspend fun submit(snapshot: MonitoringSnapshot): OperationResult<Unit> {
        val managedDeviceId = snapshot.deviceInfo.managedDeviceId
            ?: return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)

        return communicationSessionManager.submitMonitoring(
            managedDeviceId = managedDeviceId,
            payload = MonitoringTelemetrySerializer.serialize(snapshot).toString(),
            collectedAtEpochMillis = snapshot.collectedAtEpochMillis,
        )
    }
}
