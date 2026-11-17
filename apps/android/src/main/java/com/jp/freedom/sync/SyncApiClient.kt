package com.jp.freedom.sync

import com.jp.freedom.data.SyncSnapshotCodec
import com.jp.freedom.domain.SyncSnapshot
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.AccessProfile
import java.time.Instant
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class ServerSnapshot(val revision: Long, val snapshot: SyncSnapshot)
data class ServerStudySession(val revision: Long, val session: StudyTimerSession?, val supersededAttemptIds: List<String> = emptyList())

sealed interface PutSnapshotResult {
    data class Accepted(val revision: Long) : PutSnapshotResult
    data object RevisionConflict : PutSnapshotResult
}

class SyncFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

class SyncApiClient(private val endpoint: String, private val writeToken: String) {
    private val sessionEndpoint get() = endpoint.removeSuffix("/v1/snapshot").trimEnd('/') + "/v1/session"
    private val historyEndpoint get() = endpoint.removeSuffix("/v1/snapshot").trimEnd('/') + "/v1/history"

    fun getStudyHistory(): List<StudyTimerSession> {
        val response = request("GET", url = historyEndpoint)
        try {
            val sessions = JSONObject(response.body).getJSONArray("sessions")
            return (0 until sessions.length()).map { decodeStudySession(sessions.getJSONObject(it)) }
        } catch (error: Exception) {
            throw SyncFailure("The server sent study history that couldn't be read.", error)
        } finally { response.connection.disconnect() }
    }

    fun importStudyHistory(sessions: List<StudyTimerSession>) {
        if (sessions.isEmpty()) return
        for (batch in sessions.chunked(1000)) {
            val body = JSONObject().apply {
                put("sessions", org.json.JSONArray().apply { batch.forEach { put(encodeStudySession(it)) } })
            }
            request("PUT", body.toString(), historyEndpoint).connection.disconnect()
        }
    }

    fun resolveStudyHistory(session: StudyTimerSession) {
        val body = JSONObject().put("session", encodeStudySession(session))
        request("PUT", body.toString(), historyEndpoint + "/resolve").connection.disconnect()
    }

    /** The timer is a separate revisioned resource from the day plan. */
    fun getStudySession(): ServerStudySession {
        val response = request("GET", url = sessionEndpoint)
        try {
            val body = JSONObject(response.body)
            val sessionJson = body.optJSONObject("session")
            val superseded = sessionJson?.optJSONArray("supersededAttemptIds")
            return ServerStudySession(body.getLong("revision"), sessionJson?.let(::decodeStudySession),
                if (superseded == null) emptyList() else (0 until superseded.length()).map(superseded::getString))
        } catch (error: Exception) {
            throw SyncFailure("The server sent a study timer that couldn't be read.", error)
        } finally { response.connection.disconnect() }
    }

    fun putStudySession(expectedRevision: Long, session: StudyTimerSession): PutSnapshotResult {
        val body = JSONObject().apply {
            put("expectedRevision", expectedRevision)
            put("session", encodeStudySession(session))
        }
        val response = request("PUT", body.toString(), sessionEndpoint)
        try {
            if (response.status == HttpURLConnection.HTTP_CONFLICT) return PutSnapshotResult.RevisionConflict
            return PutSnapshotResult.Accepted(JSONObject(response.body).getLong("revision"))
        } finally { response.connection.disconnect() }
    }

    private fun encodeStudySession(session: StudyTimerSession) = JSONObject().apply {
        put("id", session.id); put("taskName", session.taskName)
        put("startedAt", session.startedAt.toString()); put("durationMillis", session.durationMillis)
        put("accessProfile", session.accessProfile.name); put("state", session.state.name)
        session.endedAt?.let { put("endedAt", it.toString()) }
        put("actualMillis", session.actualMillis)
        session.outcome?.let { put("outcome", it.name) }
        put("note", session.note); put("remaining", session.remaining)
        session.breakStartedAt?.let { put("breakStartedAt", it.toString()) }
        put("breakDurationMillis", session.breakDurationMillis); put("nextTask", session.nextTask)
        session.focusAreaId?.let { put("focusAreaId", it) }
    }

    private fun decodeStudySession(value: JSONObject) = StudyTimerSession(
        id = value.getString("id"), taskName = value.optString("taskName"),
        durationMillis = value.getLong("durationMillis"),
        accessProfile = AccessProfile.valueOf(value.getString("accessProfile")),
        startedAt = Instant.parse(value.getString("startedAt")),
        endedAt = value.optString("endedAt").takeIf(String::isNotBlank)?.let(Instant::parse),
        actualMillis = value.optLong("actualMillis"),
        state = StudySessionState.valueOf(value.getString("state")),
        focusAreaId = value.optString("focusAreaId").takeIf(String::isNotBlank),
        outcome = value.optString("outcome").takeIf(String::isNotBlank)?.let(com.jp.freedom.domain.SessionOutcome::valueOf),
        note = value.optString("note"), remaining = value.optString("remaining"),
        breakStartedAt = value.optString("breakStartedAt").takeIf(String::isNotBlank)?.let(Instant::parse),
        breakDurationMillis = value.optLong("breakDurationMillis"), nextTask = value.optString("nextTask"),
    )
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

    private fun request(method: String, requestBody: String? = null, url: String = endpoint): HttpResponse {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
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
