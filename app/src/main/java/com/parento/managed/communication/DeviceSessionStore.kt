package com.parento.managed.communication

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

data class DeviceSession(
    val sessionId: String,
    val managedDeviceId: String,
    val sessionToken: String,
    val expiresAtEpochMillis: Long,
)

interface DeviceSessionStore {
    fun read(): OperationResult<DeviceSession?>
    fun save(session: DeviceSession): OperationResult<Unit>
    fun clear(): OperationResult<Unit>
}

class AndroidDeviceSessionStore(context: Context) : DeviceSessionStore {
    private val preferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "parento_device_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): OperationResult<DeviceSession?> = runCatching {
        val id = preferences.getString(ID_KEY, null)
        val deviceId = preferences.getString(DEVICE_ID_KEY, null)
        val token = preferences.getString(TOKEN_KEY, null)
        val expires = preferences.getLong(EXPIRES_KEY, 0L)
        if (id.isNullOrBlank() || deviceId.isNullOrBlank() || token.isNullOrBlank() || expires <= 0L) {
            OperationResult.Success(null)
        } else {
            require(isUuid(id) && isUuid(deviceId) && isValidToken(token))
            OperationResult.Success(DeviceSession(id, deviceId, token, expires))
        }
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun save(session: DeviceSession): OperationResult<Unit> = runCatching {
        require(isUuid(session.sessionId) && isUuid(session.managedDeviceId))
        require(isValidToken(session.sessionToken) && session.expiresAtEpochMillis > System.currentTimeMillis())
        preferences.edit()
            .putString(ID_KEY, session.sessionId)
            .putString(DEVICE_ID_KEY, session.managedDeviceId)
            .putString(TOKEN_KEY, session.sessionToken)
            .putLong(EXPIRES_KEY, session.expiresAtEpochMillis)
            .apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun clear(): OperationResult<Unit> = runCatching {
        preferences.edit().clear().apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    private companion object {
        const val ID_KEY = "session_id"
        const val DEVICE_ID_KEY = "managed_device_id"
        const val TOKEN_KEY = "session_token"
        const val EXPIRES_KEY = "expires_at"
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        fun isValidToken(value: String): Boolean = TOKEN_PATTERN.matches(value)
        fun isUuid(value: String): Boolean = runCatching { java.util.UUID.fromString(value) }.isSuccess
    }
}
