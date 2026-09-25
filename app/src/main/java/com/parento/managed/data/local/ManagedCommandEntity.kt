package com.parento.managed.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "managed_commands")
data class ManagedCommandEntity(
    @PrimaryKey val commandId: String,
    val managedDeviceId: String,
    val type: String,
    val version: Int,
    val payloadJson: String,
    val correlationId: String?,
    val idempotencyKey: String?,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val state: String,
    val resultCode: String?,
    val errorCategory: String?,
    val resultMetadataJson: String?,
    val updatedAtEpochMillis: Long,
)
