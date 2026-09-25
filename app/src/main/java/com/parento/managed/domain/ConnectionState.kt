package com.parento.managed.domain

enum class ConnectionState {
    UNKNOWN,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

fun ConnectionState.canTransitionTo(target: ConnectionState): Boolean =
    when (this) {
        ConnectionState.UNKNOWN ->
            target == ConnectionState.DISCONNECTED || target == ConnectionState.CONNECTING
        ConnectionState.DISCONNECTED ->
            target == ConnectionState.CONNECTING
        ConnectionState.CONNECTING ->
            target == ConnectionState.CONNECTED || target == ConnectionState.DISCONNECTED
        ConnectionState.CONNECTED ->
            target == ConnectionState.DISCONNECTED
    }
