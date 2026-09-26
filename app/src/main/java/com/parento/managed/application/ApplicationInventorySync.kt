package com.parento.managed.application

import com.parento.managed.communication.DeviceCommunicationSessionManager
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class ApplicationInventorySync(
    private val collector: ApplicationInventoryCollector,
    private val localStateRepository: LocalStateRepository,
    private val sessionManager: DeviceCommunicationSessionManager,
    private val maxPayloadChars: Int = 60 * 1024,
) {
    suspend fun syncNow(): OperationResult<ApplicationInventorySyncResult> {
        val state = when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> return result
            is OperationResult.Success -> result.value
        } ?: return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)

        if (state.enrollmentState != EnrollmentState.ENROLLED ||
            state.managedDeviceId.isNullOrBlank() ||
            (state.managementMode != ManagementMode.DEVICE_OWNER &&
                state.managementMode != ManagementMode.PROFILE_OWNER)
        ) {
            return OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
        }

        val token = sessionManager.currentSessionToken()
            ?: return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)

        if (sessionManager.state.value != ConnectionState.CONNECTED) {
            return OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
        }

        localStateRepository.updateApplicationInventorySync(ApplicationInventorySyncStatus.SYNCING)

        val inventory = runCatching { collector.collect() }.getOrElse {
            localStateRepository.updateApplicationInventorySync(ApplicationInventorySyncStatus.FAILED)
            return OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        val payload = serialize(inventory)
        if (payload.length > maxPayloadChars) {
            localStateRepository.updateApplicationInventorySync(
                ApplicationInventorySyncStatus.FAILED,
                observedAtEpochMillis = inventory.observedAtEpochMillis,
            )
            return OperationResult.Failure(ManagedError.INVALID_STATE)
        }

        return when (
            val result = sessionManager.transportBoundary()
                .uploadApplicationInventory(token, payload)
        ) {
            is OperationResult.Failure -> {
                localStateRepository.updateApplicationInventorySync(
                    ApplicationInventorySyncStatus.FAILED,
                    observedAtEpochMillis = inventory.observedAtEpochMillis,
                )
                result
            }
            is OperationResult.Success -> {
                val successfulAt = System.currentTimeMillis()
                localStateRepository.updateApplicationInventorySync(
                    ApplicationInventorySyncStatus.SYNCED,
                    observedAtEpochMillis = inventory.observedAtEpochMillis,
                    successfulSyncAtEpochMillis = successfulAt,
                )
                OperationResult.Success(
                    ApplicationInventorySyncResult(
                        synchronizationId = inventory.synchronizationId,
                        applicationCount = inventory.applications.size,
                        successfulAtEpochMillis = successfulAt,
                    ),
                )
            }
        }
    }

    private fun serialize(inventory: ApplicationInventory): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("synchronizationId", inventory.synchronizationId)
            .put("observedAt", Instant.ofEpochMilli(inventory.observedAtEpochMillis).toString())
            .put(
                "applications",
                JSONArray().apply {
                    inventory.applications.forEach { application ->
                        put(
                            JSONObject()
                                .put("packageName", application.packageName)
                                .put("label", application.displayName)
                                .put("versionName", application.versionName)
                                .put("versionCode", application.versionCode)
                                .put("installState", "INSTALLED")
                                .put("enabled", application.enabled)
                                .put("category", null),
                        )
                    }
                },
            )
            .toString()
}
