package com.jp.freedom.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jp.freedom.domain.DailyReview
import com.jp.freedom.domain.PlannedBlock
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReviewWithoutBlocksTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reviewShowsRecordedSessionsWithoutPlannedBlocks() {
        val date = LocalDate.of(2026, 11, 18)
        compose.setContent {
            FocusTheme {
                DailyReviewScreen(
                    review = DailyReview(
                        date = date,
                        sessions = emptyList(),
                        actualMinutes = 0,
                        targetMinutes = 180,
                        plannedBlocks = listOf(PlannedBlock("old", date, "Old planned block", LocalTime.NOON, LocalTime.of(13, 0))),
                        focusAreaMinutes = emptyList(),
                    ),
                    onBack = {},
                    onResolveImportConflict = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("Timer sessions").assertExists()
        compose.onNodeWithText("Planned blocks").assertDoesNotExist()
        compose.onNodeWithText("Old planned block", substring = true).assertDoesNotExist()
    }
}
