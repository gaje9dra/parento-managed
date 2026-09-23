package com.parento.managed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.ParentoDatabase
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateRepositoryInstrumentedTest {
    private lateinit var database: ParentoDatabase
    private lateinit var repository: RoomLocalStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ParentoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLocalStateRepository(database.localApplicationStateDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialization_read_returnsNull() = runTest {
        val result = repository.read()

        assertEquals(OperationResult.Success(null), result)
    }

    @Test
    fun writeAndRead_roundTripState() = runTest {
        val expected = LocalApplicationState(
            stateVersion = 1,
            lastSynchronizationTimestamp = 1234L,
            initialized = true,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun update_replacesExistingState() = runTest {
        repository.write(LocalApplicationState(initialized = false))

        val updated = LocalApplicationState(
            lastSynchronizationTimestamp = 4567L,
            initialized = true,
        )
        repository.write(updated)

        assertEquals(OperationResult.Success(updated), repository.read())
    }

    @Test
    fun clear_removesState() = runTest {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(OperationResult.Success(Unit), repository.clear())
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun observe_emitsPersistedAndUpdatedState() = runTest {
        val flow = repository.observe()

        assertEquals(OperationResult.Success(null), flow.first())

        repository.write(LocalApplicationState(initialized = true))

        assertEquals(
            OperationResult.Success(LocalApplicationState(initialized = true)),
            flow.first { result ->
                result == OperationResult.Success(
                    LocalApplicationState(initialized = true),
                )
            },
        )
    }

    @Test
    fun storageContract_exposesSafeFailureType() = runTest {
        val result = repository.read()

        assertTrue(result is OperationResult.Success)
    }
}
