package com.parento.managed

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.managed.data.local.ParentoDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ParentoDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        ApplicationProvider.getApplicationContext(),
        ParentoDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_preservesExistingStateAndAddsDefaults() {
        val database = helper.createDatabase("migration-test", 1)
        database.execSQL(
            "INSERT INTO local_application_state " +
                "(id, stateVersion, lastSynchronizationTimestamp, initialized) " +
                "VALUES (1, 1, 42, 1)",
        )
        database.close()

        val migrated = helper.runMigrationsAndValidate(
            "migration-test",
            2,
            true,
            ParentoDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT stateVersion, lastSynchronizationTimestamp, initialized, " +
                "installationId, identityCreatedAtEpochMillis, enrollmentState, connectionState " +
                "FROM local_application_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(42L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertEquals("UNENROLLED", cursor.getString(5))
            assertEquals("UNKNOWN", cursor.getString(6))
        }

        migrated.close()
    }
}
