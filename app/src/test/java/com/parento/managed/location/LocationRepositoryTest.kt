package com.parento.managed.location

import com.parento.managed.data.local.LocationStateDao
import com.parento.managed.data.local.LocationStateEntity
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRepositoryTest {
    @Test
    fun savesOnlyValidatedCoordinatesAndCreatesReportId() = runBlocking {
        val dao = FakeLocationStateDao()
        val repository = RoomLocationRepository(dao)
        val result = repository.saveObserved(
            LocationSample(
                latitude = 26.9124,
                longitude = 75.7873,
                accuracyMeters = 15.0,
                altitudeMeters = null,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                observedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
        assertTrue(result is OperationResult.Success)
        val stored = (result as OperationResult.Success).value
        assertTrue(stored.reportId.isNotBlank())
        assertEquals(LocationAvailability.AVAILABLE, stored.availability)
        assertEquals(26.9124, stored.sample?.latitude ?: 0.0, 0.0)
    }

    @Test
    fun rejectsInvalidCoordinatesWithoutClamping() = runTest {
        val dao = FakeLocationStateDao()
        val repository = RoomLocationRepository(dao)
        val result = repository.saveObserved(
            LocationSample(
                latitude = 91.0,
                longitude = 75.0,
                accuracyMeters = null,
                altitudeMeters = null,
                bearingDegrees = null,
                speedMetersPerSecond = null,
                observedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals(null, dao.value)
    }

    @Test
    fun latestStateIsBoundedToOneRecordAndReportStatusIsTracked() = runTest {
        val dao = FakeLocationStateDao()
        val repository = RoomLocationRepository(dao)
        val first = repository.saveObserved(sample(26.0, 75.0, 1_000L))
        val second = repository.saveObserved(sample(27.0, 76.0, 2_000L))
        val secondLocation = (second as OperationResult.Success).value
        repository.markReportAttempt(secondLocation.reportId, 3_000L)
        repository.markReportSuccess(secondLocation.reportId, 4_000L)

        val latest = (repository.read() as OperationResult.Success).value!!
        assertEquals(secondLocation.reportId, latest.reportId)
        assertEquals(27.0, latest.sample?.latitude ?: 0.0, 0.0)
        assertEquals(LocationReportStatus.REPORTED, latest.lastReportStatus)
        assertEquals(4_000L, latest.lastSuccessfulReportEpochMillis)
        assertTrue((first as OperationResult.Success).value.reportId != secondLocation.reportId)
    }

    private fun sample(latitude: Double, longitude: Double, observedAt: Long) =
        LocationSample(latitude, longitude, 10.0, null, null, null, observedAt)

    private class FakeLocationStateDao : LocationStateDao {
        var value: LocationStateEntity? = null
        override suspend fun read(): LocationStateEntity? = value
        override suspend fun upsert(entity: LocationStateEntity) { value = entity }
        override suspend fun clear() { value = null }
    }
}
