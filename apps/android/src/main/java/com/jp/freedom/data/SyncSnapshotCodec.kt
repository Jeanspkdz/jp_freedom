package com.jp.freedom.data

import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
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
        return SyncSnapshot(settings, domains, blocks)
    }

    private fun LocalTime.apiTime() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}
