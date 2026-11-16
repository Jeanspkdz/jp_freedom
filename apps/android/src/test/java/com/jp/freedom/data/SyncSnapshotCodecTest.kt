package com.jp.freedom.data

import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.SyncSnapshot
import com.jp.freedom.domain.SharedStudySession
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.CompletionStatus
import java.time.Instant
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

    @Test
    fun androidStartedSessionKeepsItsOriginalAbsoluteTimesAcrossTheSyncContract() {
        val snapshot = SyncSnapshot(
            settings = FocusSettings(),
            desktopBlockedDomains = listOf("reddit.com"),
            blocks = emptyList(),
            activeStudySession = SharedStudySession(
                id = "phone-session-1",
                taskName = "Read chapter four",
                startedAt = Instant.parse("2026-11-05T10:15:00Z"),
                endsAt = Instant.parse("2026-11-05T11:15:00Z"),
                accessProfile = AccessProfile.ESSENTIALS_ONLY,
            ),
        )

        assertEquals(snapshot, SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun phoneReceiptForAChromeStartedSessionSurvivesTheSyncContract() {
        val snapshot = SyncSnapshot(
            settings = FocusSettings(),
            desktopBlockedDomains = listOf("reddit.com"),
            blocks = emptyList(),
            activeStudySession = SharedStudySession(
                id = "chrome-session-1",
                taskName = "Study session",
                startedAt = Instant.parse("2026-11-05T10:00:00Z"),
                endsAt = Instant.parse("2026-11-05T11:00:00Z"),
                accessProfile = AccessProfile.ESSENTIALS_ONLY,
            ),
            phoneReceivedStudySessionId = "chrome-session-1",
            pendingStudyReviewSessionId = "phone-session-review",
        )
        assertEquals(snapshot, SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun concurrentOfflineStartsSurviveTheSyncContract() {
        val candidates = listOf(
            SharedStudySession("chrome-start", "Write", Instant.parse("2026-11-05T10:00:00Z"), Instant.parse("2026-11-05T11:00:00Z"), AccessProfile.ESSENTIALS_ONLY),
            SharedStudySession("phone-start", "Read", Instant.parse("2026-11-05T10:01:00Z"), Instant.parse("2026-11-05T11:01:00Z"), AccessProfile.ESSENTIALS_ONLY),
        )
        val snapshot = SyncSnapshot(FocusSettings(), listOf("reddit.com"), emptyList(), sessionStartCandidates = candidates)

        assertEquals(candidates, SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)).sessionStartCandidates)
    }

    @Test
    fun sameFieldConflictAlternativesSurviveTheSyncContract() {
        val conflict = com.jp.freedom.domain.SyncFieldConflict("settings.requiredBlockCount", "3", "4")
        val snapshot = SyncSnapshot(FocusSettings(), listOf("reddit.com"), emptyList(), fieldConflicts = listOf(conflict))

        assertEquals(listOf(conflict), SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)).fieldConflicts)
    }

    @Test
    fun reviewedStudyHistoryAndReviewFieldsSurviveTheSyncContract() {
        val session = StudyTimerSession(
            id = "history-1", taskName = "Read chapter four", durationMillis = 3_600_000,
            accessProfile = AccessProfile.ESSENTIALS_ONLY,
            startedAt = Instant.parse("2026-11-05T10:00:00Z"),
            endedAt = Instant.parse("2026-11-05T11:00:00Z"), actualMillis = 3_600_000,
            state = StudySessionState.REVIEWED, completionStatus = CompletionStatus.FINISHED,
            note = "Good progress", remaining = "Exercise five", nextTask = "Read chapter five",
        )
        val snapshot = SyncSnapshot(FocusSettings(), listOf("reddit.com"), emptyList(), studyHistory = listOf(session))

        assertEquals(snapshot, SyncSnapshotCodec.decode(SyncSnapshotCodec.encode(snapshot)))
    }
}
