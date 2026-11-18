package com.jp.freedom.enforcement

import com.jp.freedom.domain.PlannedBlock
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDomainPolicyTest {
    private val activeBlock = PlannedBlock(
        id = "focus",
        date = LocalDate.of(2026, 11, 3),
        name = "Estudio",
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
    )

    @Test
    fun unrestrictedTimeAllowsAllDomains() {
        assertTrue(SiteDomainPolicy.isAllowed("example.com", FocusPolicyState(null, null)))
    }

    @Test
    fun essentialsModeBlocksEverySite() {
        assertFalse(SiteDomainPolicy.isAllowed("chatgpt.com", activeState(emptySet())))
        assertFalse(SiteDomainPolicy.isAllowed("www.google.com", activeState(emptySet())))
    }

    @Test
    fun chatGptModeAllowsChatGptDomainsAndBlocksSearch() {
        val state = activeState(setOf("com.openai.chatgpt"))
        assertTrue(SiteDomainPolicy.isAllowed("chatgpt.com", state))
        assertTrue(SiteDomainPolicy.isAllowed("chat.openai.com", state))
        assertTrue(SiteDomainPolicy.isAllowed("cdn.oaistatic.com", state))
        assertTrue(SiteDomainPolicy.isAllowed("auth.openai.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("www.google.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("platform.openai.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("api.openai.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("openai.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("example.com", state))
    }

    @Test
    fun searchIsAllowedOnlyInTheThirdMode() {
        val state = activeState(setOf("com.openai.chatgpt", "com.google.android.googlequicksearchbox"))
        assertTrue(SiteDomainPolicy.isAllowed("www.google.com", state))
        assertTrue(SiteDomainPolicy.isAllowed("encrypted-tbn0.gstatic.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("mail.google.com", state))
        assertFalse(SiteDomainPolicy.isAllowed("example.com", state))
    }

    @Test
    fun eveningRestrictionBlocksWebsitesEvenWhenBlockWouldAllowThem() {
        assertFalse(
            SiteDomainPolicy.isAllowed(
                "chatgpt.com",
                FocusPolicyState(BlockedReason.EVENING_RESTRICTION, activeBlock, setOf("com.openai.chatgpt")),
            ),
        )
    }

    private fun activeState(packages: Set<String>) =
        FocusPolicyState(BlockedReason.FOCUS_BLOCK, activeBlock, packages)
}
