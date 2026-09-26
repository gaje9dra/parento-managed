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
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        assertTrue(runCatching { java.util.UUID.fromString(first.installationId) }.isSuccess)
    }

    @Test
    fun repeatedIdentityInitialization_preservesSingleIdentity() = runBlocking {
        repeat(20) { repository.getOrCreateIdentity() }

        val identity = (repository.getOrCreateIdentity() as OperationResult.Success).value
        val state = (repository.read() as OperationResult.Success).value

        assertEquals(identity.installationId, state?.installationId)
        assertEquals(identity.createdAtEpochMillis, state?.identityCreatedAtEpochMillis)
    }

    @Test
    fun concurrentIdentityInitialization_returnsOneStableIdentity() = runBlocking {
        val identities = coroutineScope {
            (1..50).map { async { (repository.getOrCreateIdentity() as OperationResult.Success).value } }
                .awaitAll()
        }

        assertEquals(1, identities.map { it.installationId }.distinct().size)
        assertEquals(1, identities.map { it.createdAtEpochMillis }.distinct().size)
    }

    @Test
    fun writeAndRead_roundTripState() = runBlocking {
        val expected = LocalApplicationState(
            initialized = true,
            enrollmentState = EnrollmentState.UNENROLLED,
            connectionState = ConnectionState.UNKNOWN,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun updateEnrollmentState_persistsValidTransition() = runBlocking {
        assertEquals(OperationResult.Success(Unit), repository.updateEnrollmentState(EnrollmentState.ENROLLING))
        assertEquals(OperationResult.Success(Unit), repository.updateEnrollmentState(EnrollmentState.ENROLLED))

        val state = (repository.read() as OperationResult.Success).value
        assertEquals(EnrollmentState.ENROLLED, state?.enrollmentState)
    }

    @Test
    fun updateEnrollmentState_rejectsInvalidTransition() = runBlocking {
        val result = repository.updateEnrollmentState(EnrollmentState.REVOKED)

        assertEquals(OperationResult.Failure(ManagedError.INVALID_STATE), result)
        assertEquals(
            EnrollmentState.UNENROLLED,
            (repository.read() as OperationResult.Success).value?.enrollmentState,
        )
    }

    @Test
    fun updateConnectionState_persistsValidTransition() = runBlocking {
        assertEquals(OperationResult.Success(Unit), repository.updateConnectionState(ConnectionState.CONNECTING))
        assertEquals(OperationResult.Success(Unit), repository.updateConnectionState(ConnectionState.CONNECTED))

        val state = (repository.read() as OperationResult.Success).value
        assertEquals(ConnectionState.CONNECTED, state?.connectionState)
    }

    @Test
    fun updateConnectionState_rejectsInvalidTransition() = runBlocking {
        val result = repository.updateConnectionState(ConnectionState.CONNECTED)

        assertEquals(OperationResult.Failure(ManagedError.INVALID_STATE), result)
        assertEquals(
            ConnectionState.UNKNOWN,
            (repository.read() as OperationResult.Success).value?.connectionState,
        )
    }

    @Test
    fun identityRecovery_doesNotGenerateReplacementForManagedState() = runBlocking {
        repository.write(
            LocalApplicationState(
                initialized = true,
                enrollmentState = EnrollmentState.ENROLLED,
                managedDeviceId = "backend-device-1",
            ),
        )

        val result = repository.getOrCreateIdentity()

        assertEquals(OperationResult.Failure(ManagedError.STORAGE_FAILURE), result)
        val state = (repository.read() as OperationResult.Success).value
        assertEquals(null, state?.installationId)
        assertEquals("backend-device-1", state?.managedDeviceId)
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

        val result = repository.observe().first()
        assertEquals(OperationResult.Success(LocalApplicationState(initialized = true)), result)
    }
}
