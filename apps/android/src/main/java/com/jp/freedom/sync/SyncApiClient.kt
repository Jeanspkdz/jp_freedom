package com.jp.freedom.sync

import com.jp.freedom.data.SyncSnapshotCodec
import com.jp.freedom.domain.SyncSnapshot
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class ServerSnapshot(val revision: Long, val snapshot: SyncSnapshot)

sealed interface PutSnapshotResult {
    data class Accepted(val revision: Long) : PutSnapshotResult
    data object RevisionConflict : PutSnapshotResult
}

class SyncFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

class SyncApiClient(private val endpoint: String, private val writeToken: String) {
    fun getSnapshot(): ServerSnapshot {
        val response = request("GET")
        try {
            val body = JSONObject(response.body)
            val revision = body.getLong("revision")
            val snapshot = SyncSnapshotCodec.decode(body.getJSONObject("snapshot"))
            return ServerSnapshot(revision, snapshot)
        } catch (error: Exception) {
            throw SyncFailure("El servidor envió un plan que no se puede leer.", error)
        } finally {
            response.connection.disconnect()
        }
    }

    fun putSnapshot(expectedRevision: Long, snapshot: SyncSnapshot): PutSnapshotResult {
        val body = JSONObject().apply {
            put("expectedRevision", expectedRevision)
            put("snapshot", SyncSnapshotCodec.encode(snapshot))
        }
        val response = request("PUT", body.toString())
        try {
            if (response.status == HttpURLConnection.HTTP_CONFLICT) return PutSnapshotResult.RevisionConflict
            val result = JSONObject(response.body)
            return PutSnapshotResult.Accepted(result.getLong("revision"))
        } catch (error: SyncFailure) {
            throw error
        } catch (error: Exception) {
            throw SyncFailure("El servidor respondió sin confirmar la versión guardada.", error)
        } finally {
            response.connection.disconnect()
        }
    }

    private fun request(method: String, requestBody: String? = null): HttpResponse {
        val connection = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw SyncFailure("La dirección de sincronización no es válida.", error)
        }
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $writeToken")
        connection.setRequestProperty("Accept", "application/json")
        if (requestBody != null) {
            val bytes = requestBody.toByteArray(Charsets.UTF_8)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(bytes.size)
            try {
                connection.outputStream.use { it.write(bytes) }
            } catch (error: Exception) {
                connection.disconnect()
                throw SyncFailure("No se pudo enviar el plan. Revisa la conexión y vuelve a intentar.", error)
            }
        }
        val status = try {
            connection.responseCode
        } catch (error: Exception) {
            connection.disconnect()
            throw SyncFailure("No se pudo conectar con la API. Comprueba internet y la dirección configurada.", error)
        }
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = try {
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (error: Exception) {
            connection.disconnect()
            throw SyncFailure("No se pudo leer la respuesta del servidor.", error)
        }
        if (status == HttpURLConnection.HTTP_CONFLICT) return HttpResponse(status, responseText, connection)
        if (status !in 200..299) {
            connection.disconnect()
            throw SyncFailure(messageFor(status, responseText))
        }
        return HttpResponse(status, responseText, connection)
    }

    private fun messageFor(status: Int, body: String): String {
        val code = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
        return when {
            status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN ->
                "La API rechazó la clave de escritura. Revisa el token configurado en el teléfono."
            status == 422 ->
                "La API rechazó el plan o la lista de sitios. Revisa que no haya horarios superpuestos y vuelve a intentar."
            status >= 500 -> "La API no está disponible ahora. Los cambios siguen guardados en el teléfono y se reintentará."
            code == "invalid_json" -> "La API no reconoció el formato enviado. Actualiza la aplicación o revisa el servidor."
            else -> "La API respondió con error ($status). Revisa la dirección de sincronización."
        }
    }

    private data class HttpResponse(val status: Int, val body: String, val connection: HttpURLConnection)
}
