package com.parento.managed.communication

import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TransportSession(val sessionId:String,val managedDeviceId:String,val sessionToken:String,val expiresAtEpochMillis:Long)
data class TransportCommand(val commandId:String,val managedDeviceId:String,val type:String,val version:Int,val payload:String,val correlationId:String?,val idempotencyKey:String?,val createdAtEpochMillis:Long,val expiresAtEpochMillis:Long)

interface DeviceTransport {
    suspend fun connect(deviceCredential:String):OperationResult<TransportSession>
    suspend fun heartbeat(sessionToken:String):OperationResult<Long>
    suspend fun disconnect(sessionToken:String):OperationResult<Unit>
    suspend fun acknowledge(sessionToken:String,commandId:String):OperationResult<Unit>
    suspend fun start(sessionToken:String,commandId:String):OperationResult<Unit>
    suspend fun result(sessionToken:String,commandId:String,status:String,resultCode:String?,errorCategory:String?,resultMetadata:String?):OperationResult<Unit>
    suspend fun receiveNextCommand(sessionToken:String):OperationResult<TransportCommand?>
}

class HttpsDeviceTransport(private val baseUrl:String,private val requireHttps:Boolean):DeviceTransport {
    override suspend fun connect(deviceCredential:String)=request("/api/v1/device/sessions", "POST", deviceCredential, null){body->
        val data=JSONObject(body).getJSONObject("data"); val session=data.getJSONObject("session")
        TransportSession(session.getString("id").trim(),session.getString("managedDeviceId").trim(),data.getString("sessionToken").trim(),java.time.Instant.parse(session.getString("expiresAt")).toEpochMilli()).also{
            require(isUuid(it.sessionId)&&isUuid(it.managedDeviceId)&&isToken(it.sessionToken)&&it.expiresAtEpochMillis>System.currentTimeMillis())
        }
    }
    override suspend fun heartbeat(sessionToken:String)=request("/api/v1/device/sessions/heartbeat","POST",sessionToken,null){body->
        java.time.Instant.parse(JSONObject(body).getJSONObject("data").getJSONObject("session").getString("expiresAt")).toEpochMilli()
    }
    override suspend fun disconnect(sessionToken:String)=request("/api/v1/device/sessions/disconnect","POST",sessionToken,null){Unit}
    override suspend fun acknowledge(sessionToken:String,commandId:String)=request("/api/v1/device/commands/$commandId/ack","POST",sessionToken,null){Unit}
    override suspend fun start(sessionToken:String,commandId:String)=request("/api/v1/device/commands/$commandId/start","POST",sessionToken,null){Unit}
    override suspend fun result(sessionToken:String,commandId:String,status:String,resultCode:String?,errorCategory:String?,resultMetadata:String?)=
        request("/api/v1/device/commands/$commandId/result","POST",sessionToken,JSONObject().put("status",status).put("resultCode",resultCode).put("errorCategory",errorCategory).put("resultMetadata",resultMetadata?.let{JSONObject(it)})){Unit}
    override suspend fun receiveNextCommand(sessionToken:String):OperationResult<TransportCommand?> =
        // Phase 6.1 has no device-side command delivery endpoint. Do not invent polling/realtime.
        OperationResult.Success(null)

    private suspend fun <T> request(path:String,method:String,bearer:String,body:JSONObject?,parse:(String)->T):OperationResult<T>=withContext(Dispatchers.IO){
        runCatching{
            val endpoint=URL(baseUrl.trimEnd('/')+path)
            if(requireHttps&&endpoint.protocol!="https") return@withContext OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE)
            val connection=endpoint.openConnection() as HttpURLConnection
            try{
                connection.requestMethod=method; connection.connectTimeout=10_000; connection.readTimeout=15_000; connection.useCaches=false
                connection.setRequestProperty("Accept","application/json"); connection.setRequestProperty("Authorization","Bearer $bearer")
                connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use{it.write((body?.toString()?:"{}").toByteArray(Charsets.UTF_8))}
                val status=connection.responseCode; val stream=if(status in 200..299)connection.inputStream else connection.errorStream
                val responseBody=stream?.bufferedReader()?.use{readBounded(it)}.orEmpty()
                if(status !in 200..299) mapFailure(status) else OperationResult.Success(parse(responseBody))
            }finally{connection.disconnect()}
        }.getOrElse{if(it is IOException)OperationResult.Failure(ManagedError.NETWORK_FAILURE) else OperationResult.Failure(ManagedError.UNKNOWN)}
    }
    private fun mapFailure(status:Int):OperationResult<Nothing>=when(status){401->OperationResult.Failure(ManagedError.AUTHENTICATION_FAILURE);403->OperationResult.Failure(ManagedError.AUTHORIZATION_FAILURE);408,429->OperationResult.Failure(ManagedError.NETWORK_FAILURE);in 500..599->OperationResult.Failure(ManagedError.NETWORK_FAILURE);else->OperationResult.Failure(ManagedError.INVALID_STATE)}
    private fun readBounded(reader:java.io.Reader):String{val b=CharArray(4096);val r=StringBuilder();var n=0;while(true){val c=reader.read(b);if(c==-1)break;n+=c;if(n>64*1024)throw IOException("Response exceeded allowed size.");r.append(b,0,c)};return r.toString()}
    private companion object{val TOKEN_PATTERN=Regex("[A-Za-z0-9_-]{43}");fun isToken(v:String)=TOKEN_PATTERN.matches(v);fun isUuid(v:String)=runCatching{java.util.UUID.fromString(v)}.isSuccess}
}
