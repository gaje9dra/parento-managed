package com.parento.managed.communication

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TransportSession(
    val sessionId: String,
    val managedDeviceId: String,
    val sessionToken: String,
    val expiresAtEpochMillis: Long,
)

data class TransportCommand(
    val commandId: String,
    val managedDeviceId: String,
    val type: String,
    val version: Int,
    val payload: String,
    val correlationId: String?,
    val idempotencyKey: String?,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

interface DeviceTransport {
    suspend fun connect(deviceCredential: String): OperationResult<TransportSession>
    suspend fun heartbeat(sessionToken: String): OperationResult<Long>
    suspend fun disconnect(sessionToken: String): OperationResult<Unit>
    suspend fun acknowledge(sessionToken: String, commandId: String): OperationResult<Unit>
    suspend fun start(sessionToken: String, commandId: String): OperationResult<Unit>
    suspend fun result(
        sessionToken: String,
        commandId: String,
        status: String,
        resultCode: String?,
        errorCategory: String?,
        resultMetadata: String?,
    ): OperationResult<Unit>
    suspend fun reportLocation(
        sessionToken: String,
        reportId: String,
        availability: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        observedAt: String,
    ): OperationResult<Unit>
    suspend fun receiveNextCommand(sessionToken: String): OperationResult<TransportCommand?>

    suspend fun streamCommands(
        sessionToken: String,
        onCommand: suspend (TransportCommand) -> Unit,
    ): OperationResult<Unit> = OperationResult.Failure(ManagedError.NETWORK_FAILURE)

    suspend fun reportAudioStarted(
        sessionToken: String,
        audioSessionId: String,
        transportState: String? = null,
    ): OperationResult<Unit> = OperationResult.Failure(ManagedError.INVALID_STATE)

    suspend fun reportAudioStopped(
        sessionToken: String,
        audioSessionId: String,
    ): OperationResult<Unit> = OperationResult.Failure(ManagedError.INVALID_STATE)
}

class HttpsDeviceTransport(
    private val baseUrl: String,
    private val requireHttps: Boolean,
) : DeviceTransport {
    override suspend fun connect(deviceCredential: String): OperationResult<TransportSession> =
        request("/api/v1/device/sessions", "POST", deviceCredential, null) { body ->
            val data = JSONObject(body).getJSONObject("data")
            val session = data.getJSONObject("session")
            TransportSession(
                sessionId = session.getString("id").trim(),
                managedDeviceId = session.getString("managedDeviceId").trim(),
                sessionToken = data.getString("sessionToken").trim(),
                expiresAtEpochMillis = java.time.Instant.parse(session.getString("expiresAt")).toEpochMilli(),
            ).also {
                require(isUuid(it.sessionId) && isUuid(it.managedDeviceId) && isToken(it.sessionToken))
                require(it.expiresAtEpochMillis > System.currentTimeMillis())
            }
        }

    override suspend fun heartbeat(sessionToken: String): OperationResult<Long> =
        request("/api/v1/device/sessions/heartbeat", "POST", sessionToken, null) { body ->
            java.time.Instant.parse(
                JSONObject(body).getJSONObject("data").getJSONObject("session").getString("expiresAt"),
            ).toEpochMilli()
        }

    override suspend fun disconnect(sessionToken: String): OperationResult<Unit> =
        request("/api/v1/device/sessions/disconnect", "POST", sessionToken, null) { Unit }

    override suspend fun acknowledge(sessionToken: String, commandId: String): OperationResult<Unit> =
        request("/api/v1/device/commands/$commandId/ack", "POST", sessionToken, null) { Unit }

    override suspend fun start(sessionToken: String, commandId: String): OperationResult<Unit> =
        request("/api/v1/device/commands/$commandId/start", "POST", sessionToken, null) { Unit }

    override suspend fun result(
        sessionToken: String,
        commandId: String,
        status: String,
        resultCode: String?,
        errorCategory: String?,
        resultMetadata: String?,
    ): OperationResult<Unit> =
        request(
            "/api/v1/device/commands/$commandId/result",
            "POST",
            sessionToken,
            JSONObject()
                .put("status", status)
                .put("resultCode", resultCode)
                .put("errorCategory", errorCategory)
                .put("resultMetadata", resultMetadata?.let { JSONObject(it) }),
        ) { Unit }

    override suspend fun reportLocation(
        sessionToken: String,
        reportId: String,
        availability: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        observedAt: String,
    ): OperationResult<Unit> = request(
        "/api/v1/device/location", "POST", sessionToken,
        JSONObject().apply {
            put("reportId", reportId)
            put("availability", availability)
            if (latitude != null) put("latitude", latitude)
            if (longitude != null) put("longitude", longitude)
            if (accuracyMeters != null) put("accuracyMeters", accuracyMeters)
            put("observedAt", observedAt)
        },
    ) { Unit }

    override suspend fun receiveNextCommand(sessionToken: String): OperationResult<TransportCommand?> =
        OperationResult.Success(null)

    override suspend fun streamCommands(
        sessionToken: String,
        onCommand: suspend (TransportCommand) -> Unit,
    ): OperationResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = URL(baseUrl.trimEnd('/') + "/api/v1/device/stream")
            if (requireHttps && endpoint.protocol != "https") {
                return@withContext OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            }
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 0
                connection.useCaches = false
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                val status = connection.responseCode
                if (status !in 200..299) return@withContext mapFailure(status)
                var eventType: String? = null
                val data = StringBuilder()
                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        when {
                            line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                            line.isEmpty() -> {
                                if (eventType == "command" && data.isNotEmpty()) {
                                    val json = JSONObject(data.toString())
                                    val command = TransportCommand(
                                        commandId = json.getString("commandId"),
                                        managedDeviceId = json.getString("managedDeviceId"),
                                        type = json.getString("type"),
                                        version = json.getInt("version"),
                                        payload = json.getJSONObject("payload").toString(),
                                        correlationId = json.optString("correlationId").ifBlank { null },
                                        idempotencyKey = json.optString("idempotencyKey").ifBlank { null },
                                        createdAtEpochMillis = java.time.Instant.parse(json.getString("createdAt")).toEpochMilli(),
                                        expiresAtEpochMillis = java.time.Instant.parse(json.getString("expiresAt")).toEpochMilli(),
                                    )
                                    onCommand(command)
                                }
                                eventType = null
                                data.clear()
                            }
                        }
                    }
                }
                OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { OperationResult.Failure(ManagedError.NETWORK_FAILURE) }
    }

    override suspend fun reportAudioStarted(
        sessionToken: String,
        audioSessionId: String,
        transportState: String?,
    ): OperationResult<Unit> = request(
        "/api/v1/device/audio-sessions/$audioSessionId/started",
        "POST",
        sessionToken,
        JSONObject().apply {
            if (transportState != null) put("transportState", JSONObject(transportState))
        },
    ) { Unit }

    override suspend fun reportAudioStopped(
        sessionToken: String,
        audioSessionId: String,
    ): OperationResult<Unit> = request(
        "/api/v1/device/audio-sessions/$audioSessionId/stopped",
        "POST",
        sessionToken,
        null,
    ) { Unit }

    private suspend fun <T> request(
        path: String,
        method: String,
        bearer: String,
        body: JSONObject?,
        parse: (String) -> T,
    ): OperationResult<T> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = URL(baseUrl.trimEnd('/') + path)
            if (requireHttps && endpoint.protocol != "https") {
                return@withContext OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            }
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $bearer")
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write((body?.toString() ?: "{}").toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { readBounded(it) }.orEmpty()
                if (status !in 200..299) {
                    mapFailure(status)
                } else {
                    OperationResult.Success(parse(responseBody))
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            if (it is IOException) OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            else OperationResult.Failure(ManagedError.UNKNOWN)
        }
    }

    private fun mapFailure(status: Int): OperationResult<Nothing> =
        when (status) {
            401 -> OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE)
            403 -> OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            408, 429 -> OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            in 500..599 -> OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            else -> OperationResult.Failure(ManagedError.INVALID_STATE)
        }

    private fun readBounded(reader: java.io.Reader): String {
        val buffer = CharArray(4096)
        val result = StringBuilder()
        var total = 0
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) break
            total += count
            if (total > 64 * 1024) throw IOException("Response exceeded allowed size.")
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private companion object {
        val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        fun isToken(value: String): Boolean = TOKEN_PATTERN.matches(value)
        fun isUuid(value: String): Boolean = runCatching { java.util.UUID.fromString(value) }.isSuccess
    }
}
