package com.parento.managed.lifecycle

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.parento.managed.logging.LogLevel
import com.parento.managed.logging.ManagedLogger

enum class NetworkAvailability {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN,
}

interface ConnectivityObserver {
    fun start()
    fun stop()
}

class AndroidConnectivityObserver(
    context: Context,
    private val onChanged: (NetworkAvailability) -> Unit,
    private val logger: ManagedLogger,
) : ConnectivityObserver {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            logger.log(LogLevel.INFO, "Network availability changed: available.")
            onChanged(NetworkAvailability.CONNECTED)
        }

        override fun onLost(network: Network) {
            logger.log(LogLevel.INFO, "Network availability changed: unavailable.")
            onChanged(currentAvailability())
        }

        override fun onUnavailable() {
            logger.log(LogLevel.INFO, "Network availability changed: unavailable.")
            onChanged(NetworkAvailability.DISCONNECTED)
        }
    }

    override fun start() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
            onChanged(currentAvailability())
        }.onFailure {
            logger.log(LogLevel.WARN, "Network availability observation could not start.")
            onChanged(NetworkAvailability.UNKNOWN)
        }
    }

    override fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun currentAvailability(): NetworkAvailability =
        runCatching {
            val network = connectivityManager.activeNetwork
                ?: return@runCatching NetworkAvailability.DISCONNECTED
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@runCatching NetworkAvailability.DISCONNECTED
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                NetworkAvailability.CONNECTED
            } else {
                NetworkAvailability.DISCONNECTED
            }
        }.getOrElse { NetworkAvailability.UNKNOWN }
}
