package com.jp.freedom.data

import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
import com.jp.freedom.domain.SharedStudySession
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
                        put("accessProfile", block.accessProfile.name)
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
            }
        } ?: JSONObject.NULL)
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
                        accessProfile = AccessProfile.valueOf(block.getString("accessProfile")),
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
            )
        }
        return SyncSnapshot(settings, domains, blocks, session)
    }

    private fun LocalTime.apiTime() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}
