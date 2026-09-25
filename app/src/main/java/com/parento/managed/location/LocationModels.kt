package com.parento.managed.location

enum class LocationCapabilityState {
    AVAILABLE,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    LOCATION_SERVICES_DISABLED,
    PROVIDER_UNAVAILABLE,
    TEMPORARILY_UNAVAILABLE,
    ERROR,
}

enum class LocationAvailability { AVAILABLE, UNAVAILABLE }

enum class LocationReportStatus { NOT_REPORTED, PENDING, REPORTED, FAILED }

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val bearingDegrees: Double?,
    val speedMetersPerSecond: Double?,
    val observedAtEpochMillis: Long,
)

data class StoredLocation(
    val reportId: String,
    val availability: LocationAvailability,
    val sample: LocationSample?,
    val observedAtEpochMillis: Long,
    val lastReportStatus: LocationReportStatus,
    val lastReportAttemptEpochMillis: Long?,
    val lastSuccessfulReportEpochMillis: Long?,
)

sealed interface LocationCollectionResult {
    data class Success(val location: LocationSample) : LocationCollectionResult
    data class Unavailable(val state: LocationCapabilityState) : LocationCollectionResult
}
