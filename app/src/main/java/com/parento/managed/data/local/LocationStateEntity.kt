package com.parento.managed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_state")
data class LocationStateEntity(
    @PrimaryKey val id: Int = 1,
    val reportId: String,
    val availability: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val bearingDegrees: Double?,
    val speedMetersPerSecond: Double?,
    val observedAtEpochMillis: Long,
    val lastReportStatus: String,
    val lastReportAttemptEpochMillis: Long?,
    val lastSuccessfulReportEpochMillis: Long?,
)
