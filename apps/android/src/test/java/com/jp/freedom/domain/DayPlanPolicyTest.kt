package com.jp.freedom.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanPolicyTest {
    @Test
    fun adjacentBlocksAreAllowed() {
        val day = LocalDate.of(2026, 11, 3)
        val first = plannedBlock("first", day, "Estudiar", 9, 0, 10, 0)
        val second = plannedBlock("second", day, "Practicar", 10, 0, 11, 0)

        assertEquals(BlockValidation.Valid, DayPlanPolicy.validateBlocks(listOf(first, second)))
    }

    @Test
    fun overlappingBlocksAreRejectedWithBothActivitiesIdentified() {
        val day = LocalDate.of(2026, 11, 3)
        val first = PlannedBlock(
            id = "first",
            date = day,
            name = "Estudiar matemáticas",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
        )
        val second = PlannedBlock(
            id = "second",
            date = day,
            name = "Practicar programación",
            startTime = LocalTime.of(9, 45),
            endTime = LocalTime.of(10, 30),
        )

        val result = DayPlanPolicy.validateBlocks(listOf(first, second))

        assertEquals(
            BlockValidation.Overlapping(first.name, second.name),
            result,
        )
    }

    @Test
    fun eveningRestrictionTracksTheRequiredCountAtAndAfterCutoff() {
        val today = LocalDate.of(2026, 11, 3)
        val planDate = today.plusDays(1)
        val twoBlocks = listOf(
            plannedBlock("first", planDate, "Estudiar", 9, 0, 10, 0),
            plannedBlock("second", planDate, "Practicar", 10, 0, 11, 0),
        )
        val threeBlocks = twoBlocks + plannedBlock("third", planDate, "Repasar", 11, 0, 12, 0)
        val requiredCount = 3
        val cutoff = LocalTime.of(21, 0)

        assertFalse(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(today, LocalTime.of(20, 59)),
                cutoff = cutoff,
                requiredBlockCount = requiredCount,
                blocks = twoBlocks,
            ),
        )
        assertTrue(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(today, cutoff),
                cutoff = cutoff,
                requiredBlockCount = requiredCount,
                blocks = twoBlocks,
            ),
        )
        assertFalse(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(today, cutoff),
                cutoff = cutoff,
                requiredBlockCount = requiredCount,
                blocks = threeBlocks,
            ),
        )
        assertTrue(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(today, cutoff),
                cutoff = cutoff,
                requiredBlockCount = requiredCount,
                blocks = twoBlocks,
            ),
        )
    }

    @Test
    fun sundayEveningRequiresMondayButSaturdayEveningLeavesSundayFree() {
        val saturday = LocalDate.of(2026, 11, 7)
        val sunday = saturday.plusDays(1)
        val monday = sunday.plusDays(1)
        val cutoff = LocalTime.of(21, 0)

        assertEquals(null, DayPlanPolicy.requiredPlanDateForEvening(saturday))
        assertEquals(monday, DayPlanPolicy.requiredPlanDateForEvening(sunday))
        assertFalse(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(saturday, cutoff),
                cutoff = cutoff,
                requiredBlockCount = 3,
                blocks = emptyList(),
            ),
        )
        assertTrue(
            DayPlanPolicy.shouldRestrictEvening(
                now = LocalDateTime.of(sunday, cutoff),
                cutoff = cutoff,
                requiredBlockCount = 3,
                blocks = emptyList(),
            ),
        )
    }

    @Test
    fun focusSettingsDefaultToThreeBlocksAtNinePmAndOfferTheSpecifiedCutoffs() {
        assertEquals(3, FocusSettings().requiredBlockCount)
        assertEquals(LocalTime.of(21, 0), FocusSettings().eveningCutoff)
        assertEquals(
            listOf(LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0)),
            SUPPORTED_EVENING_CUTOFFS,
        )
    }

    private fun plannedBlock(
        id: String,
        date: LocalDate,
        name: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ) = PlannedBlock(
        id = id,
        date = date,
        name = name,
        startTime = LocalTime.of(startHour, startMinute),
        endTime = LocalTime.of(endHour, endMinute),
    )
}
