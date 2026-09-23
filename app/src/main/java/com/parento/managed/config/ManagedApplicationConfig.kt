package com.parento.managed.config

object ManagedApplicationConfig {
    @Volatile
    private var current: AppConfig? = null

    fun initialize(config: AppConfig = BuildConfiguration.load()) {
        current = config
    }

    fun get(): AppConfig =
        current ?: error("Parento application configuration has not been initialized.")
}
