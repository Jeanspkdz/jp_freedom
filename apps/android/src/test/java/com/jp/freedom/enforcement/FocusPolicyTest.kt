package com.jp.freedom.enforcement

import com.jp.freedom.domain.AllowedAppsList
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    fun plannedBlocksDoNotStartPhoneRestrictions() {
        assertNull(evaluate(LocalTime.of(10, 0)).blockedReason)
        assertNull(evaluate(LocalTime.of(10, 59)).blockedReason)
        assertNull(evaluate(LocalTime.of(11, 0)).blockedReason)
        assertTrue(evaluate(LocalTime.of(10, 0)).activeBlock != null)
        assertNull(evaluate(LocalTime.of(11, 0)).activeBlock)
    }

    @Test
    fun finishingAnActiveBlockEndsItImmediately() {
        val finished = block.copy(completionStatus = CompletionStatus.FINISHED)
        val state = FocusPolicy.evaluate(date.atTime(10, 30), listOf(finished), settings())
        assertNull(state.blockedReason)
        assertNull(state.activeBlock)
    }

    @Test
    fun otherCompletionStatusesKeepThePlannedBlockForWebsiteFiltering() {
        listOf(CompletionStatus.PARTLY_DONE, CompletionStatus.NOT_DONE).forEach { status ->
            val state = evaluate(LocalTime.of(10, 30), block.copy(completionStatus = status))
            assertNull(state.blockedReason)
            assertTrue(state.activeBlock != null)
        }
    }

    @Test
    fun eveningRestrictionOverridesTheBlockMode() {
        val nextDay = date.plusDays(1)
        val state = FocusPolicy.evaluate(
            now = date.atTime(21, 0),
            blocks = listOf(block.copy(date = nextDay, startTime = LocalTime.NOON, endTime = LocalTime.of(13, 0))),
            settings = settings(targetMinutes = 120),
        )
        assertEquals(BlockedReason.EVENING_RESTRICTION, state.blockedReason)
        assertNull(state.activeBlock)
    }

    @Test
    fun eveningRestrictionClearsAsSoonAsTheNextDayPlanMeetsItsStudyTarget() {
        val nextDay = date.plusDays(1)
        val twoHours = block.copy(date = nextDay, startTime = LocalTime.NOON, endTime = LocalTime.of(14, 0))
        val threeHours = twoHours.copy(id = "second", startTime = LocalTime.of(14, 0), endTime = LocalTime.of(15, 0))
        val now = date.atTime(21, 0)

        assertEquals(
            BlockedReason.EVENING_RESTRICTION,
            FocusPolicy.evaluate(now, listOf(twoHours), FocusSettings(studyHoursTargetMinutes = 180)).blockedReason,
        )
        assertNull(
            FocusPolicy.evaluate(now, listOf(twoHours, threeHours), FocusSettings(studyHoursTargetMinutes = 180)).blockedReason,
        )
    }

    @Test
    fun allowedAppsFollowTheChosenList() {
        val essentials = activeState(emptySet())
        val withChatGpt = activeState(setOf("com.openai.chatgpt"))
        val withSearch = activeState(setOf("com.openai.chatgpt", "com.google.android.googlequicksearchbox"))
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
        val essentials = activeState(emptySet())
        val withChatGpt = activeState(setOf("com.openai.chatgpt", "com.android.chrome"))
        val withSearch = activeState(setOf("com.openai.chatgpt", "com.google.android.googlequicksearchbox", "com.android.chrome"))
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
        val appLists = listOf(emptySet(), setOf("com.openai.chatgpt"), setOf("com.google.android.googlequicksearchbox"))

        appLists.forEach { packages ->
            assertTrue(
                "The selected keyboard must work in every focus profile",
                inputMethod in FocusPolicy.allowedPackages(
                    activeState(packages),
                    "jp",
                    essentialPackages,
                    defaultInputMethodPackage = inputMethod,
                ),
            )
        }
    }

    @Test
    fun activeStudyTimerAppliesItsAllowedAppsListWithoutCreatingPlannedBlock() {
        val session = StudyTimerSession.start(
            "Reading", Duration.ofMinutes(60), AllowedAppsList.fromLegacyAccessProfile("ESSENTIALS_WITH_CHATGPT"),
            LocalDateTime.of(2026, 11, 3, 10, 0).atZone(ZoneId.systemDefault()).toInstant(),
        )
        val state = FocusPolicy.evaluate(
            now = LocalDateTime.of(2026, 11, 3, 10, 30), blocks = emptyList(), settings = settings(), studySession = session,
        )
        assertEquals(BlockedReason.FOCUS_BLOCK, state.blockedReason)
        assertNull(state.activeBlock)
        val allowed = FocusPolicy.allowedPackages(state, "jp", setOf("phone", "messages"))
        assertTrue("com.openai.chatgpt" in allowed)
        assertFalse("social.app" in allowed)
    }

    @Test
    fun breakPausesPhoneRestrictionsAndRechecksEveningRestrictionWhenTimeExpires() {
        val start = LocalDateTime.of(2026, 11, 3, 21, 0).atZone(ZoneId.systemDefault()).toInstant()
        val breaking = StudyTimerSession.start("Reading", Duration.ofMinutes(30), AllowedAppsList.ESSENTIALS, start).copy(
            state = StudySessionState.BREAKING,
            breakStartedAt = start,
            breakDurationMillis = Duration.ofMinutes(15).toMillis(),
        )
        val settings = FocusSettings(studyHoursTargetMinutes = 180)
        val now = LocalDateTime.of(2026, 11, 3, 21, 10)
        val shortPlan = listOf(block.copy(date = date.plusDays(1)))

        assertNull(FocusPolicy.evaluate(now, shortPlan, settings, breaking).blockedReason)
        assertEquals(
            BlockedReason.EVENING_RESTRICTION,
            FocusPolicy.evaluate(now.plusMinutes(10), shortPlan, settings, breaking.copy(breakDurationMillis = Duration.ofMinutes(5).toMillis())).blockedReason,
        )
    }

    private fun evaluate(time: LocalTime, candidate: PlannedBlock = block) =
        FocusPolicy.evaluate(date.atTime(time), listOf(candidate), settings())

    private fun activeState(packages: Set<String>) = FocusPolicyState(BlockedReason.FOCUS_BLOCK, null, packages)

    private fun settings(targetMinutes: Int = 180) = FocusSettings(studyHoursTargetMinutes = targetMinutes)
}
