package com.parento.managed.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalApplicationStateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ParentoDatabase : RoomDatabase() {
    abstract fun localApplicationStateDao(): LocalApplicationStateDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN installationId TEXT",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN identityCreatedAtEpochMillis INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN enrollmentState TEXT NOT NULL DEFAULT 'UNENROLLED'",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN connectionState TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
            }
        }

        fun builder(context: Context): RoomDatabase.Builder<ParentoDatabase> =
            Room.databaseBuilder(
                context.applicationContext,
                ParentoDatabase::class.java,
                "parento-managed.db",
            ).addMigrations(MIGRATION_1_2)
    }
}
