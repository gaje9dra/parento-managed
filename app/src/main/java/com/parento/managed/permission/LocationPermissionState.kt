package com.parento.managed.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

enum class LocationPermissionCapability {
    GRANTED_FOREGROUND,
    GRANTED_BACKGROUND,
    FOREGROUND_REQUIRED,
    BACKGROUND_REQUIRED,
    LOCATION_SERVICES_DISABLED,
}

class AndroidLocationPermissionCapability(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun state(): LocationPermissionCapability {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return LocationPermissionCapability.FOREGROUND_REQUIRED

        val servicesEnabled = runCatching {
            manager?.allProviders.orEmpty().any { manager?.isProviderEnabled(it) == true }
        }.getOrDefault(false)
        if (!servicesEnabled) return LocationPermissionCapability.LOCATION_SERVICES_DISABLED

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val background = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!background) return LocationPermissionCapability.BACKGROUND_REQUIRED
            return LocationPermissionCapability.GRANTED_BACKGROUND
        }
        return LocationPermissionCapability.GRANTED_FOREGROUND
    }
}
