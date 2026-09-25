package com.parento.managed.communication

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

interface DeviceCredentialStore {
    fun read(): OperationResult<String?>
    fun save(credential: String): OperationResult<Unit>
    fun clear(): OperationResult<Unit>
}

class AndroidDeviceCredentialStore(context: Context) : DeviceCredentialStore {
    private val preferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "parento_device_credential",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): OperationResult<String?> = runCatching {
        val value = preferences.getString(KEY, null)
        if (value.isNullOrBlank()) OperationResult.Success(null)
        else {
            require(isValidCredential(value))
            OperationResult.Success(value)
        }
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun save(credential: String): OperationResult<Unit> = runCatching {
        require(isValidCredential(credential))
        preferences.edit().putString(KEY, credential).apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun clear(): OperationResult<Unit> = runCatching {
        preferences.edit().remove(KEY).apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    private companion object {
        const val KEY = "device_credential"
        val CREDENTIAL_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        fun isValidCredential(value: String): Boolean = CREDENTIAL_PATTERN.matches(value)
    }
}
