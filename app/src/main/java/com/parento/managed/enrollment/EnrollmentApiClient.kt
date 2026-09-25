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
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
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
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (status in 200..299) parseSuccess(responseBody)
            else mapFailure(status, responseBody)
        }.getOrElse {
            if (it is IOException) OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            else OperationResult.Failure(ManagedError.UNKNOWN)
        }
    }

    private fun parseSuccess(body: String): OperationResult<EnrollmentResult> =
        runCatching {
            val data = JSONObject(body).getJSONObject("data")
            val enrollment = data.getJSONObject("enrollment")
            OperationResult.Success(
                EnrollmentResult(
                    managedDeviceId = data.getString("managedDeviceId"),
                    enrollmentId = enrollment.getString("id"),
                    expiresAtEpochMillis =
                        java.time.Instant.parse(enrollment.getString("expiresAt")).toEpochMilli(),
                ),
            )
        }.getOrElse { OperationResult.Failure(ManagedError.UNKNOWN) }

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
            status == 429 || status >= 500 ->
                OperationResult.Failure(ManagedError.NETWORK_FAILURE)
            else -> OperationResult.Failure(ManagedError.UNKNOWN)
        }
    }
}
