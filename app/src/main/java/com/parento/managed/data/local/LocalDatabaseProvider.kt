package com.parento.managed.data.local

import android.content.Context

object LocalDatabaseProvider {
    @Volatile
    private var database: ParentoDatabase? = null

    fun initialize(context: Context): ParentoDatabase {
        database?.let { return it }

        return synchronized(this) {
            database ?: ParentoDatabase.builder(context)
                .build()
                .also { database = it }
        }
    }

    fun get(): ParentoDatabase =
        database ?: error("Parento local database has not been initialized.")
}
