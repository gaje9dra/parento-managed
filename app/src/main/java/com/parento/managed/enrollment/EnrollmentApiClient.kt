package com.parento.managed.enrollment

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface EnrollmentApiClient {
    suspend fun consume(
        authorization: EnrollmentAuthorization,
        localInstallationIdentity: String,
        name: String,
    ): OperationResult<EnrollmentResult>
}

class HttpEnrollmentApiClient(
    private val baseUrl: String,
    private val requireHttps: Boolean,
) : EnrollmentApiClient {
    override suspend fun consume(
        authorization: EnrollmentAuthorization,
        localInstallationIdentity: String,
        name: String,
    ): OperationResult<EnrollmentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = URL(
                baseUrl.trimEnd('/') + "/api/v1/devices/enrollments/" +
                    authorization.enrollmentId + "/consume",
            )
            if (requireHttps && endpoint.protocol != "https") {
                return@withContext OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            }

            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.useCaches = false
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")

                val body = JSONObject()
                    .put("authorizationSecret", authorization.authorizationSecret)
                    .put("localInstallationIdentity", localInstallationIdentity)
                    .put("name", name.trim())
                    .put("platform", "android")
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { readBounded(it) }.orEmpty()

                if (status in 200..299) parseSuccess(responseBody)
                else mapFailure(status, responseBody)
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            if (it is IOException) OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            else OperationResult.Failure(ManagedError.UNKNOWN)
        }
    }

    private fun parseSuccess(body: String): OperationResult<EnrollmentResult> =
        runCatching {
            val data = JSONObject(body).getJSONObject("data")
            val enrollment = data.getJSONObject("enrollment")
            val managedDeviceId = data.getString("managedDeviceId").trim()
            val enrollmentId = enrollment.getString("id").trim()
            require(managedDeviceId.isNotBlank())
            require(isUuid(enrollmentId))
            EnrollmentResult(
                managedDeviceId = managedDeviceId,
                enrollmentId = enrollmentId,
                expiresAtEpochMillis =
                    java.time.Instant.parse(enrollment.getString("expiresAt")).toEpochMilli(),
            )
        }.fold(
            onSuccess = { OperationResult.Success(it) },
            onFailure = { OperationResult.Failure(ManagedError.UNKNOWN) },
        )

    private fun mapFailure(status: Int, body: String): OperationResult<EnrollmentResult> {
        val code = runCatching {
            JSONObject(body).getJSONObject("error").getString("code")
        }.getOrNull()
        return when {
            status == 401 || status == 403 ->
                OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            status == 404 && code == "ENROLLMENT_NOT_FOUND" ->
                OperationResult.Failure(ManagedError.INVALID_STATE)
            status == 409 || status == 410 ->
                OperationResult.Failure(ManagedError.INVALID_STATE)
            status == 429 || code == "RATE_LIMITED" ->
                OperationResult.Failure(ManagedError.RATE_LIMITED)
            status >= 500 ->
                OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            status == 408 ->
                OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            status == 400 ->
                OperationResult.Failure(ManagedError.INVALID_STATE)
            else -> OperationResult.Failure(ManagedError.UNKNOWN)
        }
    }

    private fun readBounded(reader: java.io.Reader): String {
        val buffer = CharArray(4096)
        val result = StringBuilder()
        var total = 0
        while (true) {
            val count = reader.read(buffer)
            if (count == -1) break
            total += count
            if (total > MAX_RESPONSE_CHARS) {
                throw IOException("Enrollment response exceeded the allowed size.")
            }
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_CHARS = 64 * 1024

        fun isUuid(value: String): Boolean =
            runCatching { java.util.UUID.fromString(value) }.isSuccess
    }
}
