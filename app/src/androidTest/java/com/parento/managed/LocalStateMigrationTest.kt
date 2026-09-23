package com.parento.managed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.managed.data.local.ParentoDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateMigrationTest {
    private lateinit var database: ParentoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ParentoDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun migration1To2_preservesExistingStateAndAddsSafeDefaults() {
        val sqlite = database.openHelper.writableDatabase

        sqlite.execSQL(
            """
            CREATE TABLE local_application_state (
                id INTEGER NOT NULL PRIMARY KEY,
                stateVersion INTEGER NOT NULL,
                lastSynchronizationTimestamp INTEGER,
                initialized INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            INSERT INTO local_application_state
                (id, stateVersion, lastSynchronizationTimestamp, initialized)
            VALUES (1, 7, 1234, 1)
            """.trimIndent(),
        )

        ParentoDatabase.MIGRATION_1_2.migrate(sqlite)

        sqlite.query(
            "SELECT stateVersion, lastSynchronizationTimestamp, initialized, " +
                "installationId, identityCreatedAtEpochMillis, enrollmentState, connectionState " +
                "FROM local_application_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(1234L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(null, cursor.getString(3))
            assertEquals(null, cursor.getLongOrNull(4))
            assertEquals("UNENROLLED", cursor.getString(5))
            assertEquals("UNKNOWN", cursor.getString(6))
        }
    }
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
