package com.parento.managed.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.ManagementMode
import com.parento.managed.device.ManagementModeDetector
import com.parento.managed.domain.OperationResult
import java.io.File

interface DeviceInfoProvider { suspend fun get(): OperationResult<DeviceInfo> }
interface BatteryInfoProvider { fun get(): OperationResult<BatteryInfo> }
interface NetworkInfoProvider { fun get(): OperationResult<NetworkInfo> }
interface StorageInfoProvider { fun get(): OperationResult<StorageInfo> }
interface MemoryInfoProvider { fun get(): OperationResult<MemoryInfo> }
interface ManagementInfoProvider { fun get(): OperationResult<ManagementMode> }

class AndroidDeviceInfoProvider(
    private val context: Context,
    private val localStateRepository: LocalStateRepository,
    private val managementInfoProvider: ManagementInfoProvider,
) : DeviceInfoProvider {
    override suspend fun get(): OperationResult<DeviceInfo> = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val managedDeviceId = when (val r = localStateRepository.getManagedDeviceId()) {
            is OperationResult.Success -> r.value
            is OperationResult.Failure -> null
        }
        val installationId = when (val r = localStateRepository.getOrCreateIdentity()) {
            is OperationResult.Success -> r.value.installationId
            is OperationResult.Failure -> null
        }
        val mode = when (val r = managementInfoProvider.get()) {
            is OperationResult.Success -> r.value
            is OperationResult.Failure -> ManagementMode.UNKNOWN
        }
        OperationResult.Success(
            DeviceInfo(
                managedDeviceId = managedDeviceId,
                installationId = installationId,
                managementMode = mode,
                androidVersion = Build.VERSION.RELEASE ?: "unknown",
                apiLevel = Build.VERSION.SDK_INT,
                appVersion = packageInfo.versionName ?: "unknown",
                appVersionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong(),
            ),
        )
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}

class AndroidBatteryInfoProvider(private val context: Context) : BatteryInfoProvider {
    override fun get(): OperationResult<BatteryInfo> = runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return OperationResult.Success(BatteryInfo(null, ChargingState.UNKNOWN, BatteryStatus.UNKNOWN))
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val chargingState = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingState.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargingState.FULL
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingState.DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingState.NOT_CHARGING
            else -> ChargingState.UNKNOWN
        }
        val status = when {
            percentage == null -> BatteryStatus.UNKNOWN
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
            percentage <= 10 -> BatteryStatus.CRITICAL
            percentage <= 20 -> BatteryStatus.LOW
            else -> BatteryStatus.NORMAL
        }
        OperationResult.Success(BatteryInfo(percentage, chargingState, status))
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}

class AndroidNetworkInfoProvider(private val context: Context) : NetworkInfoProvider {
    override fun get(): OperationResult<NetworkInfo> = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return OperationResult.Success(NetworkInfo(NetworkState.UNKNOWN))
        val network = cm.activeNetwork ?: return OperationResult.Success(NetworkInfo(NetworkState.OFFLINE))
        val capabilities = cm.getNetworkCapabilities(network)
            ?: return OperationResult.Success(NetworkInfo(NetworkState.UNKNOWN))
        val state = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkState.OTHER
            else -> NetworkState.UNKNOWN
        }
        OperationResult.Success(NetworkInfo(state))
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}

class AndroidStorageInfoProvider(private val context: Context) : StorageInfoProvider {
    override fun get(): OperationResult<StorageInfo> = runCatching {
        val stats = StatFs(File(context.filesDir.absolutePath).path)
        val total = stats.totalBytes
        val available = stats.availableBytes
        OperationResult.Success(
            StorageInfo(
                totalBytes = total.takeIf { it >= 0L },
                availableBytes = available.takeIf { it >= 0L },
                usedBytes = if (total >= 0L && available >= 0L) (total - available).coerceAtLeast(0L) else null,
            ),
        )
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}

class AndroidMemoryInfoProvider(private val context: Context) : MemoryInfoProvider {
    override fun get(): OperationResult<MemoryInfo> = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return OperationResult.Success(MemoryInfo(null, null, null))
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        OperationResult.Success(
            MemoryInfo(
                totalBytes = info.totalMem.takeIf { it > 0L },
                availableBytes = info.availMem.takeIf { it >= 0L },
                lowMemory = info.lowMemory,
            ),
        )
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}

class AndroidManagementInfoProvider(
    private val detector: ManagementModeDetector,
) : ManagementInfoProvider {
    override fun get(): OperationResult<ManagementMode> = runCatching {
        OperationResult.Success(detector.detect().mode)
    }.getOrElse { OperationResult.Failure(com.parento.managed.domain.ManagedError.PLATFORM_FAILURE) }
}
