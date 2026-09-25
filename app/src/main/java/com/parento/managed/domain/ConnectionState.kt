package com.parento.managed.domain

enum class ConnectionState {
    UNKNOWN,
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    FAILED,
}

fun ConnectionState.canTransitionTo(target: ConnectionState): Boolean =
    when (this) {
        ConnectionState.UNKNOWN -> target == ConnectionState.DISCONNECTED || target == ConnectionState.CONNECTING
        ConnectionState.DISCONNECTED -> target == ConnectionState.CONNECTING
        ConnectionState.CONNECTING -> target == ConnectionState.AUTHENTICATING || target == ConnectionState.DISCONNECTED || target == ConnectionState.FAILED
        ConnectionState.AUTHENTICATING -> target == ConnectionState.CONNECTED || target == ConnectionState.DISCONNECTED || target == ConnectionState.FAILED
        ConnectionState.CONNECTED -> target == ConnectionState.RECONNECTING || target == ConnectionState.DISCONNECTING || target == ConnectionState.DISCONNECTED || target == ConnectionState.FAILED
        ConnectionState.RECONNECTING -> target == ConnectionState.CONNECTING || target == ConnectionState.DISCONNECTED || target == ConnectionState.FAILED
        ConnectionState.DISCONNECTING -> target == ConnectionState.DISCONNECTED || target == ConnectionState.FAILED
        ConnectionState.FAILED -> target == ConnectionState.DISCONNECTED || target == ConnectionState.CONNECTING
    }
