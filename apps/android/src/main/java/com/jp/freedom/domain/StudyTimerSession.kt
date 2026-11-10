package com.jp.freedom.domain

import java.time.Duration
import java.time.Instant

enum class StudySessionState { ACTIVE, PENDING_REVIEW, BREAKING, REVIEWED }

data class StudyTimerSession(
    val id: String,
    val taskName: String,
    val durationMillis: Long,
    val accessProfile: AccessProfile,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val actualMillis: Long = 0,
    val state: StudySessionState = StudySessionState.ACTIVE,
    val completionStatus: CompletionStatus? = null,
    val note: String = "",
    val remaining: String = "",
    val breakStartedAt: Instant? = null,
    val breakDurationMillis: Long = 0,
    val nextTask: String = "",
) {
    companion object {
        fun start(taskName: String, duration: Duration, accessProfile: AccessProfile, now: Instant) =
            StudyTimerSession(java.util.UUID.randomUUID().toString(), taskName, duration.toMillis(), accessProfile, now)
    }
}

object StudyTimerPolicy {
    val PRESETS = listOf(Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(90), Duration.ofMinutes(120))

    fun remaining(session: StudyTimerSession, now: Instant): Duration =
        Duration.ofMillis((session.durationMillis - Duration.between(session.startedAt, now).toMillis()).coerceAtLeast(0))

    fun finishIfDue(session: StudyTimerSession, now: Instant): StudyTimerSession? =
        if (session.state == StudySessionState.ACTIVE && !now.isBefore(session.startedAt.plusMillis(session.durationMillis))) finish(session, now) else null

    fun stop(session: StudyTimerSession, now: Instant): StudyTimerSession = finish(session, now)

    fun breakRemaining(session: StudyTimerSession, now: Instant): Duration {
        val startedAt = session.breakStartedAt ?: return Duration.ZERO
        return Duration.ofMillis((session.breakDurationMillis - Duration.between(startedAt, now).toMillis()).coerceAtLeast(0))
    }

    fun finishBreakIfDue(session: StudyTimerSession, now: Instant): StudyTimerSession? =
        if (session.state == StudySessionState.BREAKING && breakRemaining(session, now).isZero) session.copy(state = StudySessionState.REVIEWED) else null

    fun review(session: StudyTimerSession, taskName: String, status: CompletionStatus, note: String, remaining: String = "") =
        session.copy(taskName = taskName.trim(), completionStatus = status, note = note, remaining = remaining, state = StudySessionState.REVIEWED)

    fun canStartAnother(session: StudyTimerSession?): Boolean = session == null || session.state == StudySessionState.REVIEWED

    private fun finish(session: StudyTimerSession, now: Instant) = session.copy(
        endedAt = now,
        actualMillis = Duration.between(session.startedAt, now).toMillis().coerceAtLeast(0).coerceAtMost(session.durationMillis),
        state = StudySessionState.PENDING_REVIEW,
    )
}
