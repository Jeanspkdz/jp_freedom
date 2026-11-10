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
        assertTrue(reviewController.canStartAnother())
        assertEquals(StudyTimerDestination.PLANNER, reviewController.destination())
        assertNotNull(reviewController.start(Duration.ofMinutes(60), AccessProfile.ESSENTIALS_ONLY))
    }

    private fun controller(store: MemorySessionStore, now: String) = StudyTimerController(
        store,
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
    )

    private class MemorySessionStore : StudySessionStore {
        private val sessions = mutableListOf<StudyTimerSession>()
        override fun activeStudySession() = sessions.lastOrNull { it.state == StudySessionState.ACTIVE }
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
