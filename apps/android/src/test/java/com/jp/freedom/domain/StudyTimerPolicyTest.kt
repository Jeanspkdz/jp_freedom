package com.jp.freedom.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StudyTimerPolicyTest {
    @Test
    fun aDelayedChromeSessionStartsWithOnlyItsOriginalRemainingDurationAndEssentialsProfile() {
        val startedAt = Instant.parse("2026-11-05T10:00:00Z")
        val endsAt = Instant.parse("2026-11-05T11:00:00Z")
        val shared = SharedStudySession(
            id = "chrome-session",
            taskName = "Study session",
            startedAt = startedAt,
            endsAt = endsAt,
            accessProfile = AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH,
        )

        val received = StudyTimerPolicy.receiveSharedSession(shared, Instant.parse("2026-11-05T10:45:00Z"))

        assertEquals(startedAt, received.startedAt)
        assertEquals(Duration.ofHours(1).toMillis(), received.durationMillis)
        assertEquals(Duration.ofMinutes(15), StudyTimerPolicy.remaining(received, Instant.parse("2026-11-05T10:45:00Z")))
        assertEquals(AccessProfile.ESSENTIALS_ONLY, received.accessProfile)
        assertEquals(StudySessionState.ACTIVE, received.state)
    }

    @Test fun dailyReviewIncludesOnlySessionsStartedOnTheRequestedLocalDay() {
        val sessions = listOf(
            reviewed("today", Instant.parse("2026-11-10T05:00:00Z"), 90, "Math", CompletionStatus.FINISHED, "note", ""),
            reviewed("yesterday", Instant.parse("2026-11-10T04:59:59Z"), 60, "History", CompletionStatus.PARTLY_DONE, "", "chapter 2"),
        )
        val summary = StudyTimerPolicy.dailyReview(LocalDate.of(2026, 11, 10), java.time.ZoneId.of("America/Lima"), 180, sessions, emptyList())
        assertEquals(90, summary.actualMinutes)
        assertEquals(180, summary.targetMinutes)
        assertEquals(1, summary.sessions.size)
        assertEquals("Math", summary.sessions.single().taskName)
    }

    @Test fun unresolvedImportedHistoryConflictCountsOnlyOneCompetingSession() {
        val startedAt = Instant.parse("2026-11-10T12:00:00Z")
        val sessions = listOf(
            reviewed("same-id", startedAt, 60, "Server version", CompletionStatus.FINISHED, "", ""),
            reviewed("same-id-phone-copy", startedAt, 60, "Phone version", CompletionStatus.FINISHED, "", ""),
        )

        val summary = StudyTimerPolicy.dailyReview(LocalDate.of(2026, 11, 10), ZoneId.of("UTC"), 180, sessions, emptyList())

        assertEquals(60, summary.actualMinutes)
        assertEquals(2, summary.sessions.size)
    }

    private fun reviewed(id: String, startedAt: Instant, actualMinutes: Long, task: String, status: CompletionStatus, note: String, remaining: String) =
        StudyTimerSession(id, task, 3_600_000, AccessProfile.ESSENTIALS_ONLY, startedAt, startedAt.plusSeconds(3600), actualMinutes * 60_000,
            StudySessionState.REVIEWED, status, note, remaining)

    @Test fun dailyReminderUsesTheNextOccurrenceOfItsConfiguredLocalTime() {
        val zone = ZoneId.of("America/Lima")
        val atEight = ZonedDateTime.of(2026, 11, 10, 20, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 21, 0, 0, 0, zone), StudyTimerPolicy.nextReminder(atEight, LocalTime.of(21, 0)))
        val atNine = atEight.withHour(21)
        assertEquals(ZonedDateTime.of(2026, 11, 11, 21, 0, 0, 0, zone), StudyTimerPolicy.nextReminder(atNine, LocalTime.of(21, 0)))
    }
    private val start = Instant.parse("2026-11-10T12:00:00Z")

    @Test fun expiryRecordsPlannedDurationAndRequiresReview() {
        val session = StudyTimerSession.start("Math", Duration.ofHours(1), AccessProfile.ESSENTIALS_ONLY, start)
        val ended = StudyTimerPolicy.finishIfDue(session, start.plus(Duration.ofHours(1)))!!
        assertEquals(60, ended.actualMillis / 60_000)
        assertEquals(StudySessionState.PENDING_REVIEW, ended.state)
        assertFalse(StudyTimerPolicy.canStartAnother(ended))
    }

    @Test fun earlyStopRecordsElapsedTimeAndReviewUnlocksNextSession() {
        val session = StudyTimerSession.start("Reading", Duration.ofHours(1), AccessProfile.ESSENTIALS_WITH_CHATGPT, start)
        val ended = StudyTimerPolicy.stop(session, start.plusSeconds(20 * 60L))
        assertEquals(20, ended.actualMillis / 60_000)
        assertTrue(StudyTimerPolicy.canStartAnother(StudyTimerPolicy.review(ended, "Reading", CompletionStatus.PARTLY_DONE, "")))
    }

    @Test fun remainingTimeNeverGoesNegativeAndPresetsIncludeNinetyMinutes() {
        val session = StudyTimerSession.start("", Duration.ofMinutes(90), AccessProfile.ESSENTIALS_ONLY, start)
        assertEquals(Duration.ofMinutes(90), StudyTimerPolicy.remaining(session, start))
        assertEquals(Duration.ZERO, StudyTimerPolicy.remaining(session, start.plus(Duration.ofMinutes(91))))
        assertTrue(StudyTimerPolicy.PRESETS.contains(Duration.ofMinutes(90)))
    }
}
