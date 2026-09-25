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
class ParentoDatabaseMigrationTest {
    @Test fun migrate1To2_preservesExistingStateAndAddsDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("parento-migration-test")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE local_application_state (id INTEGER NOT NULL, stateVersion INTEGER NOT NULL, lastSynchronizationTimestamp INTEGER, initialized INTEGER NOT NULL, PRIMARY KEY(id))")
                    }
                    override fun onUpgrade(database: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val database = helper.writableDatabase
        database.execSQL("INSERT INTO local_application_state (id,stateVersion,lastSynchronizationTimestamp,initialized) VALUES (1,1,42,1)")
        ParentoDatabase.MIGRATION_1_2.migrate(database)
        database.query("SELECT stateVersion,lastSynchronizationTimestamp,initialized,installationId,identityCreatedAtEpochMillis,enrollmentState,connectionState FROM local_application_state WHERE id=1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0)); assertEquals(42L, cursor.getLong(1)); assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.isNull(3)); assertTrue(cursor.isNull(4)); assertEquals("UNENROLLED", cursor.getString(5)); assertEquals("UNKNOWN", cursor.getString(6))
        }
        database.close()
    }

    @Test fun migrate5To6_addsCommandPersistenceTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("parento-migration-test-v6")
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE local_application_state (id INTEGER NOT NULL, stateVersion INTEGER NOT NULL, lastSynchronizationTimestamp INTEGER, initialized INTEGER NOT NULL, installationId TEXT, identityCreatedAtEpochMillis INTEGER, enrollmentState TEXT NOT NULL, managementMode TEXT NOT NULL, managementCapabilities TEXT NOT NULL, managementStateUpdatedAtEpochMillis INTEGER, connectionState TEXT NOT NULL, managedDeviceId TEXT, PRIMARY KEY(id))")
                    }
                    override fun onUpgrade(database: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val database = helper.writableDatabase
        ParentoDatabase.MIGRATION_5_6.migrate(database)
        database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='managed_commands'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("managed_commands", cursor.getString(0))
        }
        database.close()
    }
}
