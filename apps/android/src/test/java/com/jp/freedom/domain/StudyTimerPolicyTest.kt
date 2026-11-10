package com.jp.freedom.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StudyTimerPolicyTest {
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
