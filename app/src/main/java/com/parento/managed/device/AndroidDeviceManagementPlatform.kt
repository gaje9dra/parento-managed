package com.parento.managed.device

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager

class AndroidDeviceManagementPlatform(
    context: Context,
) : DeviceManagementPlatform {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val devicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)

    override fun isDeviceOwner(): Boolean =
        devicePolicyManager?.isDeviceOwnerApp(appContext.packageName) ?: false

    override fun isProfileOwner(): Boolean =
        devicePolicyManager?.isProfileOwnerApp(appContext.packageName) ?: false

    override fun isDevicePolicySupported(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) &&
            devicePolicyManager != null
}
