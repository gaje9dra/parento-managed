package com.parento.managed.domain

enum class ConnectionState {
    UNKNOWN,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

fun ConnectionState.canTransitionTo(target: ConnectionState): Boolean =
    when (this) {
        UNKNOWN -> target == DISCONNECTED || target == CONNECTING
        DISCONNECTED -> target == CONNECTING
        CONNECTING -> target == CONNECTED || target == DISCONNECTED
        CONNECTED -> target == DISCONNECTED
    }
