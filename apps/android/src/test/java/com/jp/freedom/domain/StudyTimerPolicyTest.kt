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
    @Test fun dailyReviewCountsElapsedTimeOfAnActiveSessionAfterReturningToTheApp() {
        val startedAt = Instant.parse("2026-11-10T15:00:00Z")
        val active = StudyTimerSession.start("", Duration.ofMinutes(90), AllowedAppsList.ESSENTIALS, startedAt)
        val review = StudyTimerPolicy.dailyReview(
            LocalDate.of(2026, 11, 10), ZoneId.of("America/Lima"), 180,
            listOf(active), emptyList(), startedAt.plusSeconds(35 * 60),
        )
        assertEquals(35, review.actualMinutes)
    }

    @Test fun dailyReviewIncludesOnlySessionsStartedOnTheRequestedLocalDay() {
        val sessions = listOf(
            reviewed("today", Instant.parse("2026-11-10T05:00:00Z"), 90, "Math", SessionOutcome.FINISHED, "note", ""),
            reviewed("yesterday", Instant.parse("2026-11-10T04:59:59Z"), 60, "History", SessionOutcome.NOT_FINISHED, "", "chapter 2"),
        )
        val summary = StudyTimerPolicy.dailyReview(LocalDate.of(2026, 11, 10), java.time.ZoneId.of("America/Lima"), 180, sessions, emptyList())
        assertEquals(90, summary.actualMinutes)
        assertEquals(180, summary.targetMinutes)
        assertEquals(1, summary.sessions.size)
        assertEquals("Math", summary.sessions.single().taskName)
    }

    @Test fun dailyReviewGroupsTimerMinutesByFocusAreaAndKeepsUnassignedTimeVisible() {
        val areas = listOf(FocusArea("english", "English"), FocusArea("project", "App project"))
        val sessions = listOf(
            reviewed("english-1", Instant.parse("2026-11-10T15:00:00Z"), 60, "Vocabulary", SessionOutcome.FINISHED, "", "")
                .copy(focusAreaId = "english"),
            reviewed("english-2", Instant.parse("2026-11-10T16:00:00Z"), 15, "Reading", SessionOutcome.NOT_FINISHED, "", "")
                .copy(focusAreaId = "english"),
            reviewed("unassigned", Instant.parse("2026-11-10T17:00:00Z"), 20, "Break notes", SessionOutcome.FINISHED, "", ""),
        )

        val review = StudyTimerPolicy.dailyReview(
            LocalDate.of(2026, 11, 10), ZoneId.of("America/Lima"), 180, sessions, emptyList(), focusAreas = areas,
        )

        assertEquals(
            listOf(FocusAreaMinutes("english", "English", 75), FocusAreaMinutes(null, "Unassigned", 20)),
            review.focusAreaMinutes,
        )
    }

    @Test fun focusAreaCatalogTrimsNamesDeduplicatesAndKeepsAtMostTwoAreas() {
        val saved = FocusAreaPolicy.prepare(listOf(
            FocusArea("one", "  English  "),
            FocusArea("duplicate", "english"),
            FocusArea("two", "App project"),
            FocusArea("three", "Biology"),
            FocusArea("blank", "  "),
        ))

        assertEquals(listOf(FocusArea("one", "English"), FocusArea("two", "App project")), saved)
    }

    private fun reviewed(id: String, startedAt: Instant, actualMinutes: Long, task: String, outcome: SessionOutcome, note: String, remaining: String) =
        StudyTimerSession(
            id = id, taskName = task, durationMillis = 3_600_000,
            allowedAppsListId = AllowedAppsList.ESSENTIALS_ID, allowedAppsListName = "Essentials", allowedAppPackages = emptySet(),
            startedAt = startedAt, endedAt = startedAt.plusSeconds(3600), actualMillis = actualMinutes * 60_000,
            state = StudySessionState.REVIEWED, outcome = outcome, note = note, remaining = remaining,
        )

    @Test fun dailyReminderUsesTheNextOccurrenceOfItsConfiguredLocalTime() {
        val zone = ZoneId.of("America/Lima")
        val atEight = ZonedDateTime.of(2026, 11, 10, 20, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 21, 0, 0, 0, zone), StudyTimerPolicy.nextReminder(atEight, LocalTime.of(21, 0)))
        val atNine = atEight.withHour(21)
        assertEquals(ZonedDateTime.of(2026, 11, 11, 21, 0, 0, 0, zone), StudyTimerPolicy.nextReminder(atNine, LocalTime.of(21, 0)))
    }
    private val start = Instant.parse("2026-11-10T12:00:00Z")

    @Test fun expiryRecordsPlannedDurationAndRequiresReview() {
        val session = StudyTimerSession.start("Math", Duration.ofHours(1), AllowedAppsList.ESSENTIALS, start)
        val ended = StudyTimerPolicy.finishIfDue(session, start.plus(Duration.ofHours(1)))!!
        assertEquals(60, ended.actualMillis / 60_000)
        assertEquals(StudySessionState.PENDING_REVIEW, ended.state)
        assertFalse(StudyTimerPolicy.canStartAnother(ended))
    }

    @Test fun earlyStopRecordsElapsedTimeAndReviewUnlocksNextSession() {
        val session = StudyTimerSession.start("Reading", Duration.ofHours(1), AllowedAppsList.fromLegacyAccessProfile("ESSENTIALS_WITH_CHATGPT"), start)
        val ended = StudyTimerPolicy.stop(session, start.plusSeconds(20 * 60L))
        assertEquals(20, ended.actualMillis / 60_000)
        assertTrue(StudyTimerPolicy.canStartAnother(StudyTimerPolicy.review(ended, "Reading", SessionOutcome.NOT_FINISHED, "")))
    }

    @Test fun sessionReviewKeepsOneOfItsTwoOutcomesAndOptionalNotes() {
        val session = StudyTimerSession.start("Reading", Duration.ofHours(1), AllowedAppsList.ESSENTIALS, start)
        val ended = StudyTimerPolicy.stop(session, start.plusSeconds(20 * 60L))

        val reviewed = StudyTimerPolicy.review(ended, "Reading", SessionOutcome.NOT_FINISHED, "Finish chapter 2 tomorrow")

        assertEquals(SessionOutcome.NOT_FINISHED, reviewed.outcome)
        assertEquals("Finish chapter 2 tomorrow", reviewed.note)
        assertEquals(StudySessionState.REVIEWED, reviewed.state)
    }

    @Test fun remainingTimeNeverGoesNegativeAndPresetsIncludeNinetyMinutes() {
        val session = StudyTimerSession.start("", Duration.ofMinutes(90), AllowedAppsList.ESSENTIALS, start)
        assertEquals(Duration.ofMinutes(90), StudyTimerPolicy.remaining(session, start))
        assertEquals(Duration.ZERO, StudyTimerPolicy.remaining(session, start.plus(Duration.ofMinutes(91))))
        assertTrue(StudyTimerPolicy.PRESETS.contains(Duration.ofMinutes(90)))
    }
}
