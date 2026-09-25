package com.parento.managed.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalApplicationStateEntity::class],
    version = 5,
    exportSchema = false,
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE local_application_state_new (
                        id INTEGER NOT NULL,
                        stateVersion INTEGER NOT NULL,
                        lastSynchronizationTimestamp INTEGER,
                        initialized INTEGER NOT NULL,
                        installationId TEXT,
                        identityCreatedAtEpochMillis INTEGER,
                        enrollmentState TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO local_application_state_new (
                        id,
                        stateVersion,
                        lastSynchronizationTimestamp,
                        initialized,
                        installationId,
                        identityCreatedAtEpochMillis,
                        enrollmentState
                    )
                    SELECT
                        id,
                        stateVersion,
                        lastSynchronizationTimestamp,
                        initialized,
                        installationId,
                        identityCreatedAtEpochMillis,
                        enrollmentState
                    FROM local_application_state
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE local_application_state")
                database.execSQL(
                    "ALTER TABLE local_application_state_new RENAME TO local_application_state",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN managementMode TEXT NOT NULL DEFAULT 'NOT_MANAGED'",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN managementCapabilities TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN managementStateUpdatedAtEpochMillis INTEGER",
                )
            }
        }


        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN connectionState TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state ADD COLUMN managedDeviceId TEXT",
                )
            }
        }

        fun builder(context: Context): RoomDatabase.Builder<ParentoDatabase> =
            Room.databaseBuilder(
                context.applicationContext,
                ParentoDatabase::class.java,
                "parento-managed.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
