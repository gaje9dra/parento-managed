package com.parento.managed.enrollment

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult

interface EnrollmentStore {
    fun read(): OperationResult<PendingEnrollment?>
    fun save(enrollment: PendingEnrollment): OperationResult<Unit>
    fun clear(): OperationResult<Unit>
}

class AndroidSecureEnrollmentStore(context: Context) : EnrollmentStore {
    private val preferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "parento_secure_enrollment",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): OperationResult<PendingEnrollment?> = runCatching {
        val id = preferences.getString("enrollment_id", null)
        val secret = preferences.getString("authorization_secret", null)
        val expires = preferences.getLong("expires_at", 0L)
        if (id.isNullOrBlank() || secret.isNullOrBlank() || expires <= 0L) {
            OperationResult.Success(null)
        } else {
            OperationResult.Success(PendingEnrollment(id, secret, expires))
        }
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun save(enrollment: PendingEnrollment): OperationResult<Unit> = runCatching {
        preferences.edit()
            .putString("enrollment_id", enrollment.enrollmentId)
            .putString("authorization_secret", enrollment.authorizationSecret)
            .putLong("expires_at", enrollment.expiresAtEpochMillis)
            .apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }

    override fun clear(): OperationResult<Unit> = runCatching {
        preferences.edit().clear().apply()
        OperationResult.Success(Unit)
    }.getOrElse { OperationResult.Failure(ManagedError.STORAGE_FAILURE) }
}
