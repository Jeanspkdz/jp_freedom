package com.jp.freedom.data

import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSnapshotCodecTest {
    @Test
    fun optionalBlockNoteSurvivesSnapshotRoundTrip() {
        val snapshot = SyncSnapshot(
            settings = FocusSettings(),
            desktopBlockedDomains = listOf("facebook.com"),
            blocks = listOf(
                PlannedBlock(
                    id = "study-1",
                    date = LocalDate.of(2026, 11, 6),
                    name = "Study",
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(10, 0),
                    note = "Review exercise four",
                ),
            ),
        )

        assertEquals(snapshot, SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun olderSnapshotsWithoutANoteDecodeAsNull() {
        val snapshot = SyncSnapshot(
            settings = FocusSettings(),
            desktopBlockedDomains = listOf("facebook.com"),
            blocks = listOf(
                PlannedBlock(
                    id = "study-1",
                    date = LocalDate.of(2026, 11, 6),
                    name = "Study",
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(10, 0),
                ),
            ),
        )
        val payload = SyncSnapshotCodec.encode(snapshot)

        assertNull(SyncSnapshotCodec.decode(payload).blocks.single().note)
        assertEquals(false, payload.getJSONArray("blocks").getJSONObject(0).has("note"))
    }
}
