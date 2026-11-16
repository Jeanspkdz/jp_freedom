package com.jp.freedom.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimerControllerTest {
    @Test fun startingStoppingNotificationReviewAndStartingAgainFollowTheSessionFlow() {
        val store = MemorySessionStore()
        val started = controller(store, "2026-11-10T12:00:00Z").start(Duration.ofMinutes(90), AccessProfile.ESSENTIALS_ONLY)
        assertNotNull(started)
        assertEquals(StudyTimerDestination.TIMER, controller(store, "2026-11-10T12:05:00Z").destination())

        val reviewController = controller(store, "2026-11-10T12:20:00Z")
        val ended = reviewController.stop()
        assertEquals(20 * 60_000L, ended?.actualMillis)
        assertEquals(StudyTimerDestination.REVIEW, reviewController.destination(openReviewRequest = true))
        assertEquals(StudyTimerDestination.PLANNER, reviewController.dismissReview())
        assertFalse(reviewController.canStartAnother())
        assertNull(reviewController.start(Duration.ofMinutes(60), AccessProfile.ESSENTIALS_ONLY))

        assertNotNull(reviewController.review("Read chapter 4", CompletionStatus.PARTLY_DONE, "Good progress", "Exercises"))
        assertFalse(reviewController.canStartAnother())
        assertNotNull(reviewController.endBreak("Review exercises"))
        assertTrue(reviewController.canStartAnother())
        assertEquals(StudyTimerDestination.PLANNER, reviewController.destination())
        assertNotNull(reviewController.start(Duration.ofMinutes(60), AccessProfile.ESSENTIALS_ONLY))
    }

    @Test fun reviewingStartsAnUnrestrictedBreakThatCanOnlyEndAfterNamingTheNextTask() {
        val store = MemorySessionStore()
        val controller = controller(store, "2026-11-10T12:00:00Z")
        controller.start(Duration.ofMinutes(45), AccessProfile.ESSENTIALS_ONLY)
        controller.stop()
        val reviewed = controller.review("Math", CompletionStatus.FINISHED, "", "", Duration.ofMinutes(15))

        assertEquals(StudySessionState.BREAKING, reviewed?.state)
        assertFalse(controller.canStartAnother())
        assertNull(controller.endBreak(""))
        assertEquals(StudyTimerDestination.BREAK, controller.destination())
        assertEquals(StudySessionState.REVIEWED, controller.endBreak("Read biology" )?.state)
        assertTrue(controller.canStartAnother())
    }

    @Test fun aSharedStopReleasesTheMatchingPhoneSessionAndLeavesItAwaitingReview() {
        val active = StudyTimerSession(
            id = "phone-session-1",
            taskName = "Read chapter four",
            durationMillis = Duration.ofMinutes(60).toMillis(),
            accessProfile = AccessProfile.ESSENTIALS_ONLY,
            startedAt = Instant.parse("2026-11-10T12:00:00Z"),
        )

        val stopped = StudyTimerPolicy.stopForSharedReview(
            active,
            pendingStudyReviewSessionId = "phone-session-1",
            now = Instant.parse("2026-11-10T12:20:00Z"),
        )

        assertEquals(StudySessionState.PENDING_REVIEW, stopped?.state)
        assertEquals(20 * 60_000L, stopped?.actualMillis)
        assertEquals(Instant.parse("2026-11-10T12:20:00Z"), stopped?.endedAt)
        assertFalse(StudyTimerPolicy.canStartAnother(stopped))
        assertNull(StudyTimerPolicy.stopForSharedReview(active, "another-session", Instant.parse("2026-11-10T12:20:00Z")))
    }
    private fun controller(store: MemorySessionStore, now: String) = StudyTimerController(
        store,
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
    )

    private class MemorySessionStore : StudySessionStore {
        private val sessions = mutableListOf<StudyTimerSession>()
        override fun activeStudySession() = sessions.lastOrNull { it.state == StudySessionState.ACTIVE || it.state == StudySessionState.BREAKING }
        override fun pendingStudyReview() = sessions.lastOrNull { it.state == StudySessionState.PENDING_REVIEW }
        override fun saveStudySession(session: StudyTimerSession) {
            sessions.removeAll { it.id == session.id }
            sessions += session
        }
        override fun finishStudySession(session: StudyTimerSession): Boolean {
            if (activeStudySession()?.id != session.id) return false
            saveStudySession(session)
            return true
        }
        override fun startStudySession(session: StudyTimerSession): Boolean {
            if (activeStudySession() != null || pendingStudyReview() != null) return false
            sessions += session
            return true
        }
    }
}
