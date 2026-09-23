package com.parento.managed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.RoomLocalStateRepository
import com.parento.managed.data.local.ParentoDatabase
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        database = Room.inMemoryDatabaseBuilder(context, ParentoDatabase::class.java).build()
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
    fun identity_isCreatedAndStable() = runBlocking {
        val first = (repository.getOrCreateIdentity() as OperationResult.Success).value
        val second = (repository.getOrCreateIdentity() as OperationResult.Success).value

        assertEquals(first, second)
        assertNotEquals("", first.installationId)
    }

    @Test
    fun writeAndRead_roundTripState() = runBlocking {
        val expected = LocalApplicationState(
            stateVersion = 1,
            lastSynchronizationTimestamp = 1234L,
            initialized = true,
            enrollmentState = EnrollmentState.ENROLLED,
            connectionState = ConnectionState.CONNECTED,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun updateEnrollmentState_persists() = runBlocking {
        assertEquals(
            OperationResult.Success(Unit),
            repository.updateEnrollmentState(EnrollmentState.ENROLLING),
        )

        val state = (repository.read() as OperationResult.Success).value
        assertEquals(EnrollmentState.ENROLLING, state?.enrollmentState)
    }

    @Test
    fun updateConnectionState_persists() = runBlocking {
        assertEquals(
            OperationResult.Success(Unit),
            repository.updateConnectionState(ConnectionState.CONNECTING),
        )

        val state = (repository.read() as OperationResult.Success).value
        assertEquals(ConnectionState.CONNECTING, state?.connectionState)
    }

    @Test
    fun clear_removesState() = runBlocking {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(OperationResult.Success(Unit), repository.clear())
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun observe_emitsPersistedState() = runBlocking {
        repository.write(
            LocalApplicationState(
                initialized = true,
                enrollmentState = EnrollmentState.ENROLLED,
                connectionState = ConnectionState.CONNECTED,
            ),
        )

        val result = repository.observe().first()
        assertEquals(
            OperationResult.Success(
                LocalApplicationState(
                    initialized = true,
                    enrollmentState = EnrollmentState.ENROLLED,
                    connectionState = ConnectionState.CONNECTED,
                ),
            ),
            result,
        )
    }

    @Test
    fun storageContract_exposesSafeResultType() = runBlocking {
        val result = repository.read()
        assertTrue(result is OperationResult.Success)
    }
}
