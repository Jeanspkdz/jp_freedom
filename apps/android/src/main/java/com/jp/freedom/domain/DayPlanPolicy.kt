package com.jp.freedom.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek

const val DEFAULT_REQUIRED_BLOCK_COUNT = 3
const val MAX_BLOCK_NOTE_LENGTH = 2_000
val DEFAULT_EVENING_CUTOFF: LocalTime = LocalTime.of(21, 0)
const val DEFAULT_STUDY_HOURS_TARGET_MINUTES = 180
const val DEFAULT_BREAK_MINUTES = 15
val SUPPORTED_EVENING_CUTOFFS: List<LocalTime> = listOf(
    LocalTime.of(21, 0),
    LocalTime.of(22, 0),
    LocalTime.of(23, 0),
)

data class FocusSettings(
    val requiredBlockCount: Int = DEFAULT_REQUIRED_BLOCK_COUNT,
    val eveningCutoff: LocalTime = DEFAULT_EVENING_CUTOFF,
    val studyHoursTargetMinutes: Int = DEFAULT_STUDY_HOURS_TARGET_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
) {
    init {
        require(requiredBlockCount > 0) { "The minimum number of blocks must be greater than zero." }
        require(eveningCutoff in SUPPORTED_EVENING_CUTOFFS) {
            "The cutoff time must be 9, 10, or 11 PM."
        }
        require(studyHoursTargetMinutes in 30..720 && studyHoursTargetMinutes % 30 == 0) {
            "The study-hours target must be between 30 minutes and 12 hours in 30-minute increments."
        }
        require(breakMinutes in 1..60) { "The break duration must be between 1 and 60 minutes." }
    }
}

enum class AccessProfile(val title: String) {
    ESSENTIALS_ONLY("Solo esenciales"),
    ESSENTIALS_WITH_CHATGPT("Esenciales y ChatGPT"),
    ESSENTIALS_WITH_CHATGPT_AND_SEARCH("Esenciales, ChatGPT y Google Search"),
}

enum class CompletionStatus {
    NOT_STARTED,
    FINISHED,
    PARTLY_DONE,
    NOT_DONE,
}

data class PlannedBlock(
    val id: String,
    val date: LocalDate,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val accessProfile: AccessProfile = AccessProfile.ESSENTIALS_ONLY,
    val completionStatus: CompletionStatus = CompletionStatus.NOT_STARTED,
    val note: String? = null,
)

sealed interface BlockValidation {
    data object Valid : BlockValidation

    data class MissingName(val blockId: String) : BlockValidation

    data class InvalidTimeRange(val blockId: String) : BlockValidation

    data class NoteTooLong(val blockId: String) : BlockValidation

    data class Overlapping(
        val firstBlockName: String,
        val secondBlockName: String,
    ) : BlockValidation
}

object DayPlanPolicy {
    fun requiredPlanDateForEvening(eveningDate: LocalDate): LocalDate? {
        val nextDate = eveningDate.plusDays(1)
        return when {
            eveningDate.dayOfWeek == DayOfWeek.SUNDAY -> nextDate
            nextDate.dayOfWeek == DayOfWeek.SUNDAY -> null
            else -> nextDate
        }
    }

    fun shouldRestrictEvening(
        now: LocalDateTime,
        cutoff: LocalTime,
        studyHoursTargetMinutes: Int,
        blocks: List<PlannedBlock>,
    ): Boolean {
        require(studyHoursTargetMinutes > 0) { "The study-hours target must be greater than zero." }
        if (now.toLocalTime().isBefore(cutoff)) return false

        val requiredDate = requiredPlanDateForEvening(now.toLocalDate()) ?: return false
        return plannedStudyMinutes(requiredDate, blocks) < studyHoursTargetMinutes
    }

    fun plannedStudyMinutes(date: LocalDate, blocks: List<PlannedBlock>): Int =
        blocks.asSequence()
            .filter { it.date == date }
            .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes().toInt() }

    fun validateBlocks(
        blocks: List<PlannedBlock>,
        updatingBlockId: String? = null,
    ): BlockValidation {
        val candidates = blocks.filterNot { it.id == updatingBlockId }

        candidates.firstOrNull { it.name.isBlank() }?.let {
            return BlockValidation.MissingName(it.id)
        }

        candidates.firstOrNull { !it.startTime.isBefore(it.endTime) }?.let {
            return BlockValidation.InvalidTimeRange(it.id)
        }

        candidates.firstOrNull { (it.note?.length ?: 0) > MAX_BLOCK_NOTE_LENGTH }?.let {
            return BlockValidation.NoteTooLong(it.id)
        }

        candidates
            .groupBy(PlannedBlock::date)
            .values
            .forEach { sameDayBlocks ->
                val orderedBlocks = sameDayBlocks.sortedBy(PlannedBlock::startTime)
                orderedBlocks.zipWithNext().forEach { (first, second) ->
                    if (second.startTime.isBefore(first.endTime)) {
                        return BlockValidation.Overlapping(
                            firstBlockName = first.name,
                            secondBlockName = second.name,
                        )
                    }
                }
            }

        return BlockValidation.Valid
    }
}
