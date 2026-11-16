package com.jp.freedom.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.time.ZonedDateTime

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

    fun stopForSharedReview(
        session: StudyTimerSession,
        pendingStudyReviewSessionId: String?,
        now: Instant,
    ): StudyTimerSession? = if (session.state == StudySessionState.ACTIVE && session.id == pendingStudyReviewSessionId) {
        finish(session, now)
    } else {
        null
    }

    fun breakRemaining(session: StudyTimerSession, now: Instant): Duration {
        val startedAt = session.breakStartedAt ?: return Duration.ZERO
        return Duration.ofMillis((session.breakDurationMillis - Duration.between(startedAt, now).toMillis()).coerceAtLeast(0))
    }

    fun finishBreakIfDue(session: StudyTimerSession, now: Instant): StudyTimerSession? =
        if (session.state == StudySessionState.BREAKING && breakRemaining(session, now).isZero) session.copy(state = StudySessionState.REVIEWED) else null

    fun review(session: StudyTimerSession, taskName: String, status: CompletionStatus, note: String, remaining: String = "") =
        session.copy(taskName = taskName.trim(), completionStatus = status, note = note, remaining = remaining, state = StudySessionState.REVIEWED)

    fun canStartAnother(session: StudyTimerSession?): Boolean = session == null || session.state == StudySessionState.REVIEWED

    fun receiveSharedSession(session: SharedStudySession, now: Instant): StudyTimerSession {
        val durationMillis = Duration.between(session.startedAt, session.endsAt).toMillis()
        require(durationMillis > 0) { "A shared session must end after it starts." }
        return if (session.endsAt <= now) {
            StudyTimerSession(
                id = session.id,
                taskName = session.taskName,
                durationMillis = durationMillis,
                accessProfile = AccessProfile.ESSENTIALS_ONLY,
                startedAt = session.startedAt,
                endedAt = session.endsAt,
                actualMillis = durationMillis,
                state = StudySessionState.PENDING_REVIEW,
            )
        } else {
            StudyTimerSession(
                id = session.id,
                taskName = session.taskName,
                durationMillis = durationMillis,
                accessProfile = AccessProfile.ESSENTIALS_ONLY,
                startedAt = session.startedAt,
            )
        }
    }

    fun dailyReview(date: LocalDate, zone: ZoneId, targetMinutes: Int, sessions: List<StudyTimerSession>, blocks: List<PlannedBlock>): DailyReview =
        DailyReview(
            date = date,
            sessions = sessions.filter { it.startedAt.atZone(zone).toLocalDate() == date }.sortedBy { it.startedAt },
            actualMinutes = sessions.filter { it.startedAt.atZone(zone).toLocalDate() == date }
                .groupBy { it.id.removeSuffix("-phone-copy") }
                .values.sumOf { versions ->
                    (versions.firstOrNull { !it.id.endsWith("-phone-copy") } ?: versions.first()).actualMillis
                } / 60_000,
            targetMinutes = targetMinutes,
            plannedBlocks = blocks.filter { it.date == date }.sortedBy { it.startTime },
        )

    fun nextReminder(now: ZonedDateTime, reminderTime: LocalTime): ZonedDateTime =
        now.toLocalDate().atTime(reminderTime).atZone(now.zone).let { if (it.isAfter(now)) it else it.plusDays(1) }

    private fun finish(session: StudyTimerSession, now: Instant) = session.copy(
        endedAt = now,
        actualMillis = Duration.between(session.startedAt, now).toMillis().coerceAtLeast(0).coerceAtMost(session.durationMillis),
        state = StudySessionState.PENDING_REVIEW,
    )
}

data class DailyReview(
    val date: LocalDate,
    val sessions: List<StudyTimerSession>,
    val actualMinutes: Long,
    val targetMinutes: Int,
    val plannedBlocks: List<PlannedBlock>,
)
