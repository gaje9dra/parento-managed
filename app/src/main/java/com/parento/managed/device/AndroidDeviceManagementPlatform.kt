package com.parento.managed.device

import android.app.admin.DevicePolicyManager
import android.content.Context

class AndroidDeviceManagementPlatform(
    context: Context,
) : DeviceManagementPlatform {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)

    override fun isDeviceOwner(): Boolean =
        devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    override fun isProfileOwner(): Boolean =
        devicePolicyManager.isProfileOwnerApp(appContext.packageName)
}
