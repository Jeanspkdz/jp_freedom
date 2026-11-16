package com.jp.freedom.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockScreenLaunchGateTest {
    private val allowedPackages = setOf("com.jp.freedom", "com.android.settings")

    @Test
    fun aDifferentBlockedAppIsAlsoRedirectedWithoutAnAllowedAppBetween() {
        val gate = BlockScreenLaunchGate()

        assertTrue(gate.shouldLaunch("com.google.android.youtube", allowedPackages, 1_000L))
        assertTrue(gate.shouldLaunch("com.android.chrome", allowedPackages, 1_100L))
    }

    @Test
    fun duplicateEventsForTheSameBlockedAppDoNotLaunchTheBlockScreenAgain() {
        val gate = BlockScreenLaunchGate()

        assertTrue(gate.shouldLaunch("com.google.android.youtube", allowedPackages, 1_000L))
        assertFalse(gate.shouldLaunch("com.google.android.youtube", allowedPackages, 1_100L))
        assertTrue(gate.shouldLaunch("com.google.android.youtube", allowedPackages, 1_800L))
    }

    @Test
    fun anAllowedAppResetsTheGateForTheNextBlockedApp() {
        val gate = BlockScreenLaunchGate()

        assertTrue(gate.shouldLaunch("com.google.android.youtube", allowedPackages, 1_000L))
        assertFalse(gate.shouldLaunch("com.android.settings", allowedPackages, 1_100L))
        assertTrue(gate.shouldLaunch("com.android.chrome", allowedPackages, 1_200L))
    }
}
