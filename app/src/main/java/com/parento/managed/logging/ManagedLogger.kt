package com.parento.managed.logging

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

interface ManagedLogger {
    fun log(level: LogLevel, message: String)
}
