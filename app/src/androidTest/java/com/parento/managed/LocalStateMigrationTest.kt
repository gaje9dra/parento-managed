package com.parento.managed

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE local_application_state (
                        id INTEGER NOT NULL PRIMARY KEY,
                        stateVersion INTEGER NOT NULL,
                        lastSynchronizationTimestamp INTEGER,
                        initialized INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build(),
        )
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migration1To3_preservesExistingStateAndRemovesRuntimeConnection() {
        val sqlite = helper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO local_application_state
                (id, stateVersion, lastSynchronizationTimestamp, initialized)
            VALUES (1, 7, 1234, 1)
            """.trimIndent(),
        )

        ParentoDatabase.MIGRATION_1_2.migrate(sqlite)
        sqlite.execSQL(
            "UPDATE local_application_state SET installationId = 'stable-id', " +
                "identityCreatedAtEpochMillis = 123, enrollmentState = 'ENROLLED', " +
                "connectionState = 'CONNECTED' WHERE id = 1",
        )
        ParentoDatabase.MIGRATION_2_3.migrate(sqlite)

        sqlite.query(
            "SELECT stateVersion, lastSynchronizationTimestamp, initialized, " +
                "installationId, identityCreatedAtEpochMillis, enrollmentState " +
                "FROM local_application_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(1234L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("stable-id", cursor.getString(3))
            assertEquals(123L, cursor.getLong(4))
            assertEquals("ENROLLED", cursor.getString(5))
        }
    }
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
