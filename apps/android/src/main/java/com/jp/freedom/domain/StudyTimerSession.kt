package com.jp.freedom.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.time.ZonedDateTime

enum class StudySessionState { ACTIVE, PENDING_REVIEW, BREAKING, REVIEWED }
enum class SessionOutcome { FINISHED, NOT_FINISHED }

data class StudyTimerSession(
    val id: String,
    val taskName: String,
    val durationMillis: Long,
    val accessProfile: AccessProfile,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val actualMillis: Long = 0,
    val state: StudySessionState = StudySessionState.ACTIVE,
    val focusAreaId: String? = null,
    val outcome: SessionOutcome? = null,
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

    fun review(session: StudyTimerSession, taskName: String, outcome: SessionOutcome, note: String) =
        session.copy(taskName = taskName.trim(), outcome = outcome, note = note, remaining = "", state = StudySessionState.REVIEWED)

    fun canStartAnother(session: StudyTimerSession?): Boolean = session == null || session.state == StudySessionState.REVIEWED

    fun dailyReview(
        date: LocalDate,
        zone: ZoneId,
        targetMinutes: Int,
        sessions: List<StudyTimerSession>,
        blocks: List<PlannedBlock>,
        now: Instant = Instant.now(),
        focusAreas: List<FocusArea> = emptyList(),
    ): DailyReview {
        val todaysSessions = sessions.filter { it.startedAt.atZone(zone).toLocalDate() == date }.sortedBy { it.startedAt }
        val areaNames = focusAreas.associate { it.id to it.name }
        val focusAreaMinutes = todaysSessions
            .groupBy { session -> session.focusAreaId?.takeIf(areaNames::containsKey) }
            .mapNotNull { (areaId, groupedSessions) ->
                val minutes = groupedSessions.sumOf { it.actualStudyMinutes(now) }
                if (minutes == 0L) return@mapNotNull null
                FocusAreaMinutes(areaId, areaId?.let(areaNames::get) ?: "Unassigned", minutes)
            }
            .sortedWith(compareBy<FocusAreaMinutes> { it.focusAreaId == null }.thenBy { it.name })
        return DailyReview(
            date = date,
            sessions = todaysSessions,
            actualMinutes = todaysSessions.sumOf { it.actualStudyMinutes(now) },
            targetMinutes = targetMinutes,
            plannedBlocks = blocks.filter { it.date == date }.sortedBy { it.startTime },
            focusAreaMinutes = focusAreaMinutes,
        )
    }

    fun nextReminder(now: ZonedDateTime, reminderTime: LocalTime): ZonedDateTime =
        now.toLocalDate().atTime(reminderTime).atZone(now.zone).let { if (it.isAfter(now)) it else it.plusDays(1) }

    private fun finish(session: StudyTimerSession, now: Instant) = session.copy(
        endedAt = now,
        actualMillis = Duration.between(session.startedAt, now).toMillis().coerceAtLeast(0).coerceAtMost(session.durationMillis),
        state = StudySessionState.PENDING_REVIEW,
    )

    private fun StudyTimerSession.actualStudyMinutes(now: Instant): Long =
        (if (state == StudySessionState.ACTIVE) {
            Duration.between(startedAt, now).toMillis().coerceIn(0, durationMillis)
        } else actualMillis) / 60_000
}

data class FocusArea(val id: String, val name: String)

object FocusAreaPolicy {
    const val MAX_AREAS = 2

    fun prepare(areas: List<FocusArea>): List<FocusArea> = areas.asSequence()
        .map { it.copy(name = it.name.trim()) }
        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        .distinctBy { it.name.lowercase() }
        .take(MAX_AREAS)
        .toList()
}

data class FocusAreaMinutes(val focusAreaId: String?, val name: String, val minutes: Long)

data class DailyReview(
    val date: LocalDate,
    val sessions: List<StudyTimerSession>,
    val actualMinutes: Long,
    val targetMinutes: Int,
    val plannedBlocks: List<PlannedBlock>,
    val focusAreaMinutes: List<FocusAreaMinutes>,
)
