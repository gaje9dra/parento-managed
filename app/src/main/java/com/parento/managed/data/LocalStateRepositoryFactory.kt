package com.parento.managed.data

import android.content.Context
import com.parento.managed.data.local.LocalDatabaseProvider

object LocalStateRepositoryFactory {
    fun create(context: Context): LocalStateRepository =
        RoomLocalStateRepository(
            LocalDatabaseProvider.initialize(context).localApplicationStateDao(),
        )
}
