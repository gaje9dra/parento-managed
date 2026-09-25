package com.parento.managed.location

import com.parento.managed.data.local.LocationStateDao
import com.parento.managed.data.local.LocationStateEntity
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LocationRepository {
    suspend fun read(): OperationResult<StoredLocation?>
    suspend fun saveObserved(location: LocationSample): OperationResult<StoredLocation>
    suspend fun saveUnavailable(): OperationResult<StoredLocation>
    suspend fun markReportAttempt(reportId: String, atEpochMillis: Long): OperationResult<Unit>
    suspend fun markReportSuccess(reportId: String, atEpochMillis: Long): OperationResult<Unit>
    suspend fun markReportFailure(reportId: String, atEpochMillis: Long): OperationResult<Unit>
}

class RoomLocationRepository(
    private val dao: LocationStateDao,
) : LocationRepository {
    private val mutex = Mutex()

    override suspend fun read(): OperationResult<StoredLocation?> = runCatching {
        OperationResult.Success(dao.read()?.toDomain())
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override suspend fun saveObserved(location: LocationSample): OperationResult<StoredLocation> = mutex.withLock {
        runCatching {
            require(location.latitude.isFinite() && location.longitude.isFinite())
            require(location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0)
            val current = dao.read()?.toDomain()
            val stored = StoredLocation(
                reportId = UUID.randomUUID().toString(),
                availability = LocationAvailability.AVAILABLE,
                sample = location,
                observedAtEpochMillis = location.observedAtEpochMillis,
                lastReportStatus = LocationReportStatus.PENDING,
                lastReportAttemptEpochMillis = current?.lastReportAttemptEpochMillis,
                lastSuccessfulReportEpochMillis = current?.lastSuccessfulReportEpochMillis,
            )
            dao.upsert(stored.toEntity())
            OperationResult.Success(stored)
        }.getOrElse { OperationResult.Failure(ManagedError.INVALID_STATE) }
    }

    override suspend fun saveUnavailable(): OperationResult<StoredLocation> = mutex.withLock {
        runCatching {
            val now = System.currentTimeMillis()
            val current = dao.read()?.toDomain()
            val stored = StoredLocation(
                reportId = UUID.randomUUID().toString(),
                availability = LocationAvailability.UNAVAILABLE,
                sample = null,
                observedAtEpochMillis = now,
                lastReportStatus = LocationReportStatus.PENDING,
                lastReportAttemptEpochMillis = current?.lastReportAttemptEpochMillis,
                lastSuccessfulReportEpochMillis = current?.lastSuccessfulReportEpochMillis,
            )
            dao.upsert(stored.toEntity())
            OperationResult.Success(stored)
        }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }
    }

    override suspend fun markReportAttempt(reportId: String, atEpochMillis: Long): OperationResult<Unit> =
        update(reportId) { it.copy(lastReportStatus = LocationReportStatus.PENDING, lastReportAttemptEpochMillis = atEpochMillis) }

    override suspend fun markReportSuccess(reportId: String, atEpochMillis: Long): OperationResult<Unit> =
        update(reportId) { it.copy(lastReportStatus = LocationReportStatus.REPORTED, lastSuccessfulReportEpochMillis = atEpochMillis) }

    override suspend fun markReportFailure(reportId: String, atEpochMillis: Long): OperationResult<Unit> =
        update(reportId) { it.copy(lastReportStatus = LocationReportStatus.FAILED, lastReportAttemptEpochMillis = atEpochMillis) }

    private suspend fun update(reportId: String, transform: (StoredLocation) -> StoredLocation): OperationResult<Unit> =
        mutex.withLock {
            runCatching {
                val current = dao.read()?.toDomain() ?: return@runCatching
                if (current.reportId == reportId) dao.upsert(transform(current).toEntity())
            }.fold({ OperationResult.Success(Unit) }, { OperationResult.Failure(ManagedError.STORAGE_FAILURE) })
        }
}

private fun StoredLocation.toEntity() = LocationStateEntity(
    id = 1,
    reportId = reportId,
    availability = availability.name,
    latitude = sample?.latitude,
    longitude = sample?.longitude,
    accuracyMeters = sample?.accuracyMeters,
    altitudeMeters = sample?.altitudeMeters,
    bearingDegrees = sample?.bearingDegrees,
    speedMetersPerSecond = sample?.speedMetersPerSecond,
    observedAtEpochMillis = observedAtEpochMillis,
    lastReportStatus = lastReportStatus.name,
    lastReportAttemptEpochMillis = lastReportAttemptEpochMillis,
    lastSuccessfulReportEpochMillis = lastSuccessfulReportEpochMillis,
)

private fun LocationStateEntity.toDomain() = StoredLocation(
    reportId = reportId,
    availability = runCatching { LocationAvailability.valueOf(availability) }.getOrDefault(LocationAvailability.UNAVAILABLE),
    sample = if (latitude != null && longitude != null) LocationSample(
        latitude, longitude, accuracyMeters, altitudeMeters, bearingDegrees, speedMetersPerSecond, observedAtEpochMillis
    ) else null,
    observedAtEpochMillis = observedAtEpochMillis,
    lastReportStatus = runCatching { LocationReportStatus.valueOf(lastReportStatus) }.getOrDefault(LocationReportStatus.FAILED),
    lastReportAttemptEpochMillis = lastReportAttemptEpochMillis,
    lastSuccessfulReportEpochMillis = lastSuccessfulReportEpochMillis,
)
