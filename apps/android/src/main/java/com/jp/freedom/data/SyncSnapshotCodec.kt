package com.jp.freedom.data

import com.jp.freedom.domain.AccessProfile

import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
import com.jp.freedom.domain.SharedStudySession
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object SyncSnapshotCodec {
    fun encode(snapshot: SyncSnapshot): JSONObject = JSONObject().apply {
        put("version", 1)
        put(
            "settings",
            JSONObject().apply {
                put("requiredBlockCount", snapshot.settings.requiredBlockCount)
                put("eveningCutoff", snapshot.settings.eveningCutoff.apiTime())
            },
        )
        put("desktopBlockedDomains", JSONArray(snapshot.desktopBlockedDomains))
        put("blocks", JSONArray().apply {
            snapshot.blocks.forEach { block ->
                put(
                    JSONObject().apply {
                        put("id", block.id)
                        put("date", block.date.toString())
                        put("name", block.name)
                        put("startTime", block.startTime.apiTime())
                        put("endTime", block.endTime.apiTime())
                        put("completionStatus", block.completionStatus.name)
                        block.note?.let { put("note", it) }
                    },
                )
            }
        })
        put("activeStudySession", snapshot.activeStudySession?.let { session ->
            JSONObject().apply {
                put("id", session.id)
                put("taskName", session.taskName)
                put("startedAt", session.startedAt.toString())
                put("endsAt", session.endsAt.toString())
                put("accessProfile", session.accessProfile.name)
                put("profileId", session.profileId ?: JSONObject.NULL)
                put("profileName", session.profileName)
                session.allowedPackages?.let { put("allowedPackages", JSONArray(it.sorted())) }
            }
        } ?: JSONObject.NULL)
        put("sessionStartCandidates", JSONArray().apply {
            snapshot.sessionStartCandidates.forEach { session ->
                put(JSONObject().apply {
                    put("id", session.id)
                    put("taskName", session.taskName)
                    put("startedAt", session.startedAt.toString())
                    put("endsAt", session.endsAt.toString())
                    put("accessProfile", session.accessProfile.name)
                    put("profileId", session.profileId ?: JSONObject.NULL)
                    put("profileName", session.profileName)
                    session.allowedPackages?.let { put("allowedPackages", JSONArray(it.sorted())) }
                })
            }
        })
        put("fieldConflicts", JSONArray().apply {
            snapshot.fieldConflicts.forEach { conflict ->
                put(JSONObject().apply {
                    put("path", conflict.path)
                    put("remoteValue", conflict.remoteValue)
                    put("localValue", conflict.localValue)
                })
            }
        })
        put("phoneReceivedStudySessionId", snapshot.phoneReceivedStudySessionId ?: JSONObject.NULL)
        put("pendingStudyReviewSessionId", snapshot.pendingStudyReviewSessionId ?: JSONObject.NULL)
        put("studyHistory", JSONArray().apply {
            snapshot.studyHistory.forEach { session ->
                put(JSONObject().apply {
                    put("id", session.id)
                    put("taskName", session.taskName)
                    put("durationMillis", session.durationMillis)
                    put("accessProfile", session.accessProfile.name)
                    put("profileId", session.profileId ?: JSONObject.NULL)
                    put("profileName", session.profileName)
                    session.allowedPackages?.let { put("allowedPackages", JSONArray(it.sorted())) }
                    put("startedAt", session.startedAt.toString())
                    put("endedAt", session.endedAt?.toString() ?: JSONObject.NULL)
                    put("actualMillis", session.actualMillis)
                    put("state", session.state.name)
                    put("completionStatus", session.completionStatus?.name ?: JSONObject.NULL)
                    put("note", session.note)
                    put("remaining", session.remaining)
                    put("breakStartedAt", session.breakStartedAt?.toString() ?: JSONObject.NULL)
                    put("breakDurationMillis", session.breakDurationMillis)
                    put("nextTask", session.nextTask)
                })
            }
        })
    }

    fun decode(snapshot: JSONObject): SyncSnapshot {
        require(snapshot.optInt("version", -1) == 1) { "The server sent an unsupported plan version." }
        val settingsJson = snapshot.getJSONObject("settings")
        val settings = FocusSettings(
            requiredBlockCount = settingsJson.getInt("requiredBlockCount"),
            eveningCutoff = LocalTime.parse(settingsJson.getString("eveningCutoff")),
        )
        val domainsJson = snapshot.getJSONArray("desktopBlockedDomains")
        val domains = buildList {
            for (index in 0 until domainsJson.length()) add(domainsJson.getString(index))
        }
        val blocksJson = snapshot.getJSONArray("blocks")
        val blocks = buildList {
            for (index in 0 until blocksJson.length()) {
                val block = blocksJson.getJSONObject(index)
                add(
                    PlannedBlock(
                        id = block.getString("id"),
                        date = LocalDate.parse(block.getString("date")),
                        name = block.getString("name"),
                        startTime = LocalTime.parse(block.getString("startTime")),
                        endTime = LocalTime.parse(block.getString("endTime")),
                        completionStatus = CompletionStatus.valueOf(block.getString("completionStatus")),
                        note = if (block.has("note") && !block.isNull("note")) block.getString("note") else null,
                    ),
                )
            }
        }
        val sessionJson = snapshot.optJSONObject("activeStudySession")
        val session = sessionJson?.let {
            SharedStudySession(
                id = it.getString("id"),
                taskName = it.getString("taskName"),
                startedAt = Instant.parse(it.getString("startedAt")),
                endsAt = Instant.parse(it.getString("endsAt")),
                accessProfile = AccessProfile.valueOf(it.getString("accessProfile")),
                profileId = it.optString("profileId").takeIf { id -> id.isNotBlank() && id != "null" },
                profileName = it.optString("profileName", "Essentials only"),
                allowedPackages = it.optJSONArray("allowedPackages")?.let { packages -> (0 until packages.length()).map(packages::getString).toSet() },
            )
        }
        val candidatesJson = snapshot.optJSONArray("sessionStartCandidates") ?: JSONArray()
        val candidates = buildList {
            for (index in 0 until candidatesJson.length()) {
                val item = candidatesJson.getJSONObject(index)
                add(SharedStudySession(
                    id = item.getString("id"),
                    taskName = item.getString("taskName"),
                    startedAt = Instant.parse(item.getString("startedAt")),
                    endsAt = Instant.parse(item.getString("endsAt")),
                    accessProfile = AccessProfile.valueOf(item.getString("accessProfile")),
                    profileId = item.optString("profileId").takeIf { id -> id.isNotBlank() && id != "null" },
                    profileName = item.optString("profileName", "Essentials only"),
                    allowedPackages = item.optJSONArray("allowedPackages")?.let { packages -> (0 until packages.length()).map(packages::getString).toSet() },
                ))
            }
        }
        val fieldConflictJson = snapshot.optJSONArray("fieldConflicts") ?: JSONArray()
        val fieldConflicts = buildList {
            for (index in 0 until fieldConflictJson.length()) {
                val item = fieldConflictJson.getJSONObject(index)
                add(com.jp.freedom.domain.SyncFieldConflict(
                    path = item.getString("path"),
                    remoteValue = item.getString("remoteValue"),
                    localValue = item.getString("localValue"),
                ))
            }
        }
        val receivedSessionId = snapshot.optString("phoneReceivedStudySessionId", "")
            .takeIf { it.isNotEmpty() && it != "null" }
        val pendingReviewId = snapshot.optString("pendingStudyReviewSessionId", "")
            .takeIf { it.isNotEmpty() && it != "null" }
        val historyJson = snapshot.optJSONArray("studyHistory") ?: JSONArray()
        val history = buildList {
            for (index in 0 until historyJson.length()) {
                val entry = historyJson.getJSONObject(index)
                add(StudyTimerSession(
                    id = entry.getString("id"),
                    taskName = entry.optString("taskName"),
                    durationMillis = entry.optLong("durationMillis"),
                    accessProfile = AccessProfile.valueOf(entry.getString("accessProfile")),
                    profileId = entry.optString("profileId").takeIf { id -> id.isNotBlank() && id != "null" },
                    profileName = entry.optString("profileName", "Essentials only"),
                    allowedPackages = entry.optJSONArray("allowedPackages")?.let { packages -> (0 until packages.length()).map(packages::getString).toSet() },
                    startedAt = Instant.parse(entry.getString("startedAt")),
                    endedAt = entry.optString("endedAt").takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse),
                    actualMillis = entry.optLong("actualMillis"),
                    state = StudySessionState.valueOf(entry.getString("state")),
                    completionStatus = entry.optString("completionStatus").takeIf { it.isNotBlank() && it != "null" }?.let(CompletionStatus::valueOf),
                    note = entry.optString("note"),
                    remaining = entry.optString("remaining"),
                    breakStartedAt = entry.optString("breakStartedAt").takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse),
                    breakDurationMillis = entry.optLong("breakDurationMillis"),
                    nextTask = entry.optString("nextTask"),
                ))
            }
        }
        return SyncSnapshot(
            settings = settings,
            desktopBlockedDomains = domains,
            blocks = blocks,
            activeStudySession = session,
            phoneReceivedStudySessionId = receivedSessionId,
            pendingStudyReviewSessionId = pendingReviewId,
            studyHistory = history,
            sessionStartCandidates = candidates,
            fieldConflicts = fieldConflicts,
        )
    }

    private fun LocalTime.apiTime() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}
