package com.parento.managed.application

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import java.util.UUID

class ApplicationInventoryCollector(
    context: Context,
    private val maxApplications: Int = 1000,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val packageManager = context.applicationContext.packageManager

    fun collect(): ApplicationInventory {
        require(maxApplications > 0)
        val observedAt = nowEpochMillis()
        val applications = packageManager
            .getInstalledApplications(0)
            .asSequence()
            .mapNotNull { info -> mapApplication(info, observedAt) }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }
            .take(maxApplications)
            .toList()
        return ApplicationInventory(UUID.randomUUID().toString(), observedAt, applications)
    }

    @Suppress("DEPRECATION")
    private fun readVersionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    private fun mapApplication(info: ApplicationInfo, observedAt: Long): InstalledApplication? {
        val packageName = normalizeApplicationPackageName(info.packageName) ?: return null
        val label = runCatching {
            packageManager.getApplicationLabel(info).toString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
        val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val versionCode = packageInfo?.let(::readVersionCode)
        return InstalledApplication(
            packageName = packageName,
            displayName = label?.take(255),
            versionName = packageInfo?.versionName?.trim()?.take(128),
            versionCode = versionCode,
            enabled = info.enabled,
            observedAtEpochMillis = observedAt,
        )
    }
}
