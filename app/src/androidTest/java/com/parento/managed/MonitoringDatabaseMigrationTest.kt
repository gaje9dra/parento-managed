package com.parento.managed

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.managed.data.local.ParentoDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoringDatabaseMigrationTest {
    @Test fun migrate6To7_addsMonitoringSnapshotTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("parento-monitoring-migration")
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE local_application_state (id INTEGER NOT NULL, stateVersion INTEGER NOT NULL, lastSynchronizationTimestamp INTEGER, initialized INTEGER NOT NULL, installationId TEXT, identityCreatedAtEpochMillis INTEGER, enrollmentState TEXT NOT NULL, managementMode TEXT NOT NULL, managementCapabilities TEXT NOT NULL, managementStateUpdatedAtEpochMillis INTEGER, connectionState TEXT NOT NULL, managedDeviceId TEXT, PRIMARY KEY(id))")
                        database.execSQL("CREATE TABLE managed_commands (commandId TEXT NOT NULL PRIMARY KEY, managedDeviceId TEXT NOT NULL, type TEXT NOT NULL, version INTEGER NOT NULL, payloadJson TEXT NOT NULL, correlationId TEXT, idempotencyKey TEXT, createdAtEpochMillis INTEGER NOT NULL, expiresAtEpochMillis INTEGER NOT NULL, state TEXT NOT NULL, resultCode TEXT, errorCategory TEXT, resultMetadataJson TEXT, updatedAtEpochMillis INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(database: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val database = helper.writableDatabase
        ParentoDatabase.MIGRATION_6_7.migrate(database)
        database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='monitoring_snapshot'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("monitoring_snapshot", cursor.getString(0))
        }
        database.close()
    }
}
