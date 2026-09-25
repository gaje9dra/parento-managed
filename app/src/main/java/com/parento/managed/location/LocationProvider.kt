package com.parento.managed.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

interface LocationProvider {
    fun capability(): LocationCapabilityState
    suspend fun currentLocation(): LocationCollectionResult
}

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override fun capability(): LocationCapabilityState {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return LocationCapabilityState.PERMISSION_REQUIRED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return LocationCapabilityState.BACKGROUND_PERMISSION_REQUIRED
        val providers = runCatching { locationManager?.allProviders.orEmpty() }.getOrDefault(emptyList())
        if (providers.isEmpty()) return LocationCapabilityState.PROVIDER_UNAVAILABLE
        val enabled = providers.any { runCatching { locationManager?.isProviderEnabled(it) == true }.getOrDefault(false) }
        if (!enabled) return LocationCapabilityState.LOCATION_SERVICES_DISABLED
        return LocationCapabilityState.AVAILABLE
    }

    override suspend fun currentLocation(): LocationCollectionResult {
        val capability = capability()
        if (capability != LocationCapabilityState.AVAILABLE) return LocationCollectionResult.Unavailable(capability)
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            fine && isEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            isEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return LocationCollectionResult.Unavailable(LocationCapabilityState.PROVIDER_UNAVAILABLE)
        }

        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(20_000L) {
                suspendCancellableCoroutine { continuation ->
                    val executor = Executors.newSingleThreadExecutor()
                    val cancellation = android.os.CancellationSignal()
                    continuation.invokeOnCancellation {
                        cancellation.cancel()
                        executor.shutdownNow()
                    }
                    locationManager?.getCurrentLocation(provider, cancellation, executor) {
                        executor.shutdown()
                        if (continuation.isActive) continuation.resume(it)
                    } ?: continuation.resume(null)
                }
            }
        } else {
            val cached = runCatching { locationManager?.getLastKnownLocation(provider) }.getOrNull()
            if (cached != null && System.currentTimeMillis() - cached.time <= 5 * 60_000L) cached
            else null
        }

        return location?.let { validate(it) } ?: LocationCollectionResult.Unavailable(LocationCapabilityState.TEMPORARILY_UNAVAILABLE)
    }

    private fun isEnabled(provider: String): Boolean =
        runCatching { locationManager?.isProviderEnabled(provider) == true }.getOrDefault(false)

    private fun validate(location: Location): LocationCollectionResult {
        val latitude = location.latitude
        val longitude = location.longitude
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return LocationCollectionResult.Unavailable(LocationCapabilityState.ERROR)

        val accuracy = location.accuracy.toDouble().takeIf { it.isFinite() && it >= 0.0 }
        val altitude = location.takeIf { it.hasAltitude() }?.altitude?.takeIf { it.isFinite() }
        val bearing = location.takeIf { it.hasBearing() }?.bearing?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 && it <= 360.0 }
        val speed = location.takeIf { it.hasSpeed() }?.speed?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }

        return LocationCollectionResult.Success(
            LocationSample(latitude, longitude, accuracy, altitude, bearing, speed, location.time)
        )
    }
}
