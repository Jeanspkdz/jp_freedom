package com.jp.freedom.enforcement

import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {
    private val date = LocalDate.of(2026, 11, 3)
    private val block = PlannedBlock(
        id = "focus",
        date = date,
        name = "Estudio",
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
    )

    @Test
    fun blockIsActiveFromItsStartUntilItsEnd() {
        assertEquals(BlockedReason.FOCUS_BLOCK, evaluate(LocalTime.of(10, 0)).blockedReason)
        assertEquals(BlockedReason.FOCUS_BLOCK, evaluate(LocalTime.of(10, 59)).blockedReason)
        assertNull(evaluate(LocalTime.of(11, 0)).blockedReason)
    }

    @Test
    fun finishingAnActiveBlockEndsItImmediately() {
        val finished = block.copy(completionStatus = CompletionStatus.FINISHED)
        val state = FocusPolicy.evaluate(date.atTime(10, 30), listOf(finished), settings())
        assertNull(state.blockedReason)
        assertNull(state.activeBlock)
    }

    @Test
    fun otherCompletionStatusesDoNotEndAnActiveBlockEarly() {
        listOf(CompletionStatus.PARTLY_DONE, CompletionStatus.NOT_DONE).forEach { status ->
            assertEquals(BlockedReason.FOCUS_BLOCK, evaluate(LocalTime.of(10, 30), block.copy(completionStatus = status)).blockedReason)
        }
    }

    @Test
    fun eveningRestrictionOverridesTheBlockMode() {
        val nextDay = date.plusDays(1)
        val state = FocusPolicy.evaluate(
            now = date.atTime(21, 0),
            blocks = listOf(block.copy(date = nextDay, startTime = LocalTime.NOON, endTime = LocalTime.of(13, 0))),
            settings = settings(requiredCount = 2),
        )
        assertEquals(BlockedReason.EVENING_RESTRICTION, state.blockedReason)
        assertNull(state.activeBlock)
    }

    @Test
    fun allowedAppsFollowTheChosenProfile() {
        val essentials = activeState(AccessProfile.ESSENTIALS_ONLY)
        val withChatGpt = activeState(AccessProfile.ESSENTIALS_WITH_CHATGPT)
        val withSearch = activeState(AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH)
        val essentialPackages = setOf("phone", "messages")

        assertEquals(essentialPackages + "jp", FocusPolicy.allowedPackages(essentials, "jp", essentialPackages))
        assertEquals(
            essentialPackages + setOf("jp", "com.openai.chatgpt"),
            FocusPolicy.allowedPackages(withChatGpt, "jp", essentialPackages),
        )
        assertTrue("com.google.android.googlequicksearchbox" in FocusPolicy.allowedPackages(withSearch, "jp", essentialPackages))
    }

    @Test
    fun chromeIsAvailableForAllowedSitesOnlyWhenTheDnsFilterIsRunning() {
        val essentials = activeState(AccessProfile.ESSENTIALS_ONLY)
        val withChatGpt = activeState(AccessProfile.ESSENTIALS_WITH_CHATGPT)
        val withSearch = activeState(AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH)
        val essentialPackages = setOf("phone", "messages")

        assertFalse(
            "Chrome must stay blocked when the selected profile allows no websites",
            "com.android.chrome" in FocusPolicy.allowedPackages(
                essentials,
                "jp",
                essentialPackages,
                siteFilterRunning = true,
            ),
        )
        assertFalse(
            "Chrome must stay blocked if allowed domains cannot be enforced",
            "com.android.chrome" in FocusPolicy.allowedPackages(
                withChatGpt,
                "jp",
                essentialPackages,
                siteFilterRunning = false,
            ),
        )
        assertTrue(
            "Chrome must be available when ChatGPT sites can be filtered",
            "com.android.chrome" in FocusPolicy.allowedPackages(
                withChatGpt,
                "jp",
                essentialPackages,
                siteFilterRunning = true,
            ),
        )
        assertTrue(
            "Chrome must be available when ChatGPT and Search sites can be filtered",
            "com.android.chrome" in FocusPolicy.allowedPackages(
                withSearch,
                "jp",
                essentialPackages,
                siteFilterRunning = true,
            ),
        )
    }

    @Test
    fun selectedInputMethodRemainsAvailableForTextEntry() {
        val essentialPackages = setOf("phone", "messages")
        val inputMethod = "com.google.android.inputmethod.latin"
        val profiles = listOf(
            AccessProfile.ESSENTIALS_ONLY,
            AccessProfile.ESSENTIALS_WITH_CHATGPT,
            AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH,
        )

        profiles.forEach { profile ->
            assertTrue(
                "The selected keyboard must work in every focus profile",
                inputMethod in FocusPolicy.allowedPackages(
                    activeState(profile),
                    "jp",
                    essentialPackages,
                    defaultInputMethodPackage = inputMethod,
                ),
            )
        }
    }

    private fun evaluate(time: LocalTime, candidate: PlannedBlock = block) =
        FocusPolicy.evaluate(date.atTime(time), listOf(candidate), settings())

    private fun activeState(profile: AccessProfile) = FocusPolicy.evaluate(
        date.atTime(10, 30),
        listOf(block.copy(accessProfile = profile)),
        settings(),
    )

    private fun settings(requiredCount: Int = 3) = FocusSettings(requiredBlockCount = requiredCount)
}
