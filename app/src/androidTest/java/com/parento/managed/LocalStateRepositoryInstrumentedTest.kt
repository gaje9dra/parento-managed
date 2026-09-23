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
import kotlinx.coroutines.runBlocking
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
    fun initialization_read_returnsNull() = runBlocking {
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun writeAndRead_roundTripState() = runBlocking {
        val expected = LocalApplicationState(
            stateVersion = 1,
            lastSynchronizationTimestamp = 1234L,
            initialized = true,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun update_replacesExistingState() = runBlocking {
        repository.write(LocalApplicationState(initialized = false))

        val updated = LocalApplicationState(
            lastSynchronizationTimestamp = 4567L,
            initialized = true,
        )
        repository.write(updated)

        assertEquals(OperationResult.Success(updated), repository.read())
    }

    @Test
    fun clear_removesState() = runBlocking {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(OperationResult.Success(Unit), repository.clear())
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun observe_emitsPersistedState() = runBlocking {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(
            OperationResult.Success(LocalApplicationState(initialized = true)),
            repository.observe().first(),
        )
    }

    @Test
    fun storageContract_exposesSafeResultType() = runBlocking {
        val result = repository.read()

        assertTrue(result is OperationResult.Success)
    }
}
