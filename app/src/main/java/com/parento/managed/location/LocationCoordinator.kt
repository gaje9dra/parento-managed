package com.parento.managed.location

import com.parento.managed.domain.OperationResult

class LocationCoordinator(
    private val provider: LocationProvider,
    private val repository: LocationRepository,
    private val reporter: LocationReporter,
) {
    fun capability(): LocationCapabilityState = provider.capability()

    suspend fun collectAndReport(): OperationResult<LocationCapabilityState> {
        return when (val result = provider.currentLocation()) {
            is LocationCollectionResult.Success -> {
                when (val saved = repository.saveObserved(result.location)) {
                    is OperationResult.Failure -> saved
                    is OperationResult.Success -> {
                        when (val reported = reporter.reportLatest()) {
                            is OperationResult.Failure -> reported
                            is OperationResult.Success -> OperationResult.Success(LocationCapabilityState.AVAILABLE)
                        }
                    }
                }
            }
            is LocationCollectionResult.Unavailable -> {
                if (result.state == LocationCapabilityState.LOCATION_SERVICES_DISABLED ||
                    result.state == LocationCapabilityState.PROVIDER_UNAVAILABLE ||
                    result.state == LocationCapabilityState.TEMPORARILY_UNAVAILABLE
                ) {
                    repository.saveUnavailable()
                    reporter.reportLatest()
                }
                OperationResult.Success(result.state)
            }
        }
    }

    suspend fun latest(): OperationResult<StoredLocation?> = repository.read()
}
