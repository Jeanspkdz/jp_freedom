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
            throw SyncFailure("The server sent a plan that couldn't be read.", error)
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
            throw SyncFailure("The server responded without confirming the saved version.", error)
        } finally {
            response.connection.disconnect()
        }
    }

    private fun request(method: String, requestBody: String? = null): HttpResponse {
        val connection = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw SyncFailure("The sync address is invalid.", error)
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
                throw SyncFailure("Couldn't send the plan. Check your connection and try again.", error)
            }
        }
        val status = try {
            connection.responseCode
        } catch (error: Exception) {
            connection.disconnect()
            throw SyncFailure("Couldn't connect to the API. Check your internet connection and configured address.", error)
        }
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = try {
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (error: Exception) {
            connection.disconnect()
            throw SyncFailure("Couldn't read the server response.", error)
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
                "The API rejected the write key. Check the token configured on your phone."
            status == 422 ->
                "The API rejected the plan or website list. Check for overlapping schedules and try again."
            status >= 500 -> "The API is unavailable right now. Changes are still saved on your phone and will be retried."
            code == "invalid_json" -> "The API didn't recognize the submitted format. Update the app or check the server."
            else -> "The API returned an error ($status). Check the sync address."
        }
    }

    private data class HttpResponse(val status: Int, val body: String, val connection: HttpURLConnection)
}
