package com.jp.freedom.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant

interface StudySessionStore {
    fun activeStudySession(): StudyTimerSession?
    fun pendingStudyReview(): StudyTimerSession?
    fun saveStudySession(session: StudyTimerSession)
    fun startStudySession(session: StudyTimerSession): Boolean
    fun finishStudySession(session: StudyTimerSession): Boolean
}

enum class StudyTimerDestination { PLANNER, TIMER, REVIEW }

/** App-flow boundary shared by the planner UI and a lightweight in-memory test store. */
class StudyTimerController(
    private val store: StudySessionStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun destination(openReviewRequest: Boolean = false): StudyTimerDestination {
        val pending = store.pendingStudyReview()
        if (pending != null) return StudyTimerDestination.REVIEW
        if (openReviewRequest) return StudyTimerDestination.PLANNER
        return if (store.activeStudySession() != null) StudyTimerDestination.TIMER else StudyTimerDestination.PLANNER
    }

    fun canStartAnother(): Boolean =
        StudyTimerPolicy.canStartAnother(store.activeStudySession() ?: store.pendingStudyReview())

    fun start(duration: Duration, profile: AccessProfile): StudyTimerSession? {
        if (!canStartAnother()) return null
        return StudyTimerSession.start("", duration, profile, Instant.now(clock)).takeIf(store::startStudySession)
    }

    fun stop(): StudyTimerSession? = store.activeStudySession()?.let {
        StudyTimerPolicy.finishIfDue(it, Instant.now(clock)) ?: StudyTimerPolicy.stop(it, Instant.now(clock))
    }?.takeIf(store::finishStudySession)

    fun finishIfDue(): StudyTimerSession? = store.activeStudySession()?.let {
        StudyTimerPolicy.finishIfDue(it, Instant.now(clock))
    }?.takeIf(store::finishStudySession)

    fun review(taskName: String, status: CompletionStatus, note: String, remaining: String = ""): StudyTimerSession? =
        store.pendingStudyReview()?.let { StudyTimerPolicy.review(it, taskName, status, note, remaining).also(store::saveStudySession) }

    fun dismissReview(): StudyTimerDestination = StudyTimerDestination.PLANNER
}
