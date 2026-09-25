package com.parento.managed.location

import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.domain.OperationResult
import java.time.Instant

class LocationReporter(
    private val repository: LocationRepository,
    private val sessionManager: DeviceCommunicationSessionManager,
) {
    suspend fun reportLatest(): OperationResult<Unit> {
        val storedResult = repository.read()
        val stored = when (storedResult) {
            is OperationResult.Failure -> return storedResult
            is OperationResult.Success -> storedResult.value
        } ?: return OperationResult.Success(Unit)

        val now = System.currentTimeMillis()
        repository.markReportAttempt(stored.reportId, now)
        val result = sessionManager.reportLocation(
            reportId = stored.reportId,
            availability = stored.availability.name,
            latitude = stored.sample?.latitude,
            longitude = stored.sample?.longitude,
            accuracyMeters = stored.sample?.accuracyMeters,
            observedAt = Instant.ofEpochMilli(stored.observedAtEpochMillis).toString(),
        )
        return when (result) {
            is OperationResult.Success -> {
                repository.markReportSuccess(stored.reportId, now)
                OperationResult.Success(Unit)
            }
            is OperationResult.Failure -> {
                repository.markReportFailure(stored.reportId, now)
                result
            }
        }
    }
}
