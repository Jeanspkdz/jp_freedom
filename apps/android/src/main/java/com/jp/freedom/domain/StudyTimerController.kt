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

enum class StudyTimerDestination { PLANNER, TIMER, REVIEW, BREAK }

/** App-flow boundary shared by the planner UI and a lightweight in-memory test store. */
class StudyTimerController(
    private val store: StudySessionStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun destination(openReviewRequest: Boolean = false): StudyTimerDestination {
        val pending = store.pendingStudyReview()
        if (pending != null) return StudyTimerDestination.REVIEW
        if (openReviewRequest) return StudyTimerDestination.PLANNER
        val active = store.activeStudySession()
        return when (active?.state) {
            StudySessionState.BREAKING -> StudyTimerDestination.BREAK
            StudySessionState.ACTIVE -> StudyTimerDestination.TIMER
            else -> StudyTimerDestination.PLANNER
        }
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

    fun review(taskName: String, status: CompletionStatus, note: String, remaining: String = "", breakDuration: Duration = Duration.ofMinutes(15)): StudyTimerSession? =
        store.pendingStudyReview()?.let {
            StudyTimerPolicy.review(it, taskName, status, note, remaining).copy(
                state = StudySessionState.BREAKING,
                breakStartedAt = Instant.now(clock),
                breakDurationMillis = breakDuration.toMillis(),
            ).also(store::saveStudySession)
        }

    fun finishBreakIfDue(): StudyTimerSession? = store.activeStudySession()?.let {
        StudyTimerPolicy.finishBreakIfDue(it, Instant.now(clock))
    }?.also(store::saveStudySession)

    fun endBreak(nextTask: String): StudyTimerSession? {
        if (nextTask.isBlank()) return null
        return store.activeStudySession()?.takeIf { it.state == StudySessionState.BREAKING }?.copy(
            state = StudySessionState.REVIEWED,
            nextTask = nextTask.trim(),
        )?.also(store::saveStudySession)
    }

    fun dismissReview(): StudyTimerDestination = StudyTimerDestination.PLANNER
}
