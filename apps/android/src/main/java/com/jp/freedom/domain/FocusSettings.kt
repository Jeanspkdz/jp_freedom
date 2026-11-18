package com.jp.freedom.domain

import java.time.LocalTime

const val DEFAULT_REQUIRED_BLOCK_COUNT = 3
val DEFAULT_EVENING_CUTOFF: LocalTime = LocalTime.of(21, 0)
const val DEFAULT_STUDY_HOURS_TARGET_MINUTES = 180
const val DEFAULT_BREAK_MINUTES = 15
val SUPPORTED_EVENING_CUTOFFS: List<LocalTime> = listOf(
    LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0),
)

data class FocusSettings(
    // Version 1 sync fields retained until every stored snapshot is migrated.
    val requiredBlockCount: Int = DEFAULT_REQUIRED_BLOCK_COUNT,
    val eveningCutoff: LocalTime = DEFAULT_EVENING_CUTOFF,
    val studyHoursTargetMinutes: Int = DEFAULT_STUDY_HOURS_TARGET_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
) {
    init {
        require(requiredBlockCount > 0)
        require(eveningCutoff in SUPPORTED_EVENING_CUTOFFS)
        require(studyHoursTargetMinutes in 30..720 && studyHoursTargetMinutes % 30 == 0)
        require(breakMinutes in 1..60)
    }
}
