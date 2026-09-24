package com.parento.managed.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class AndroidPermissionManager(
    context: Context,
) : PermissionManager {
    private val appContext = context.applicationContext

    override fun state(permission: ManagedPermission): PermissionState {
        val manifestPermission = when (permission) {
            ManagedPermission.CAMERA -> Manifest.permission.CAMERA
            ManagedPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            ManagedPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            ManagedPermission.MEDIA_PROJECTION -> return PermissionState.REQUIRES_USER_ACTION
        }

        return when {
            ContextCompat.checkSelfPermission(appContext, manifestPermission) ==
                PackageManager.PERMISSION_GRANTED -> PermissionState.GRANTED
            else -> PermissionState.NOT_REQUESTED
        }
    }
}
