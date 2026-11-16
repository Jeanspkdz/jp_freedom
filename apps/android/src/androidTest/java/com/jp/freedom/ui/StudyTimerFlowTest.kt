package com.jp.freedom.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.Composable
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.SessionOutcome
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.enforcement.StudySessionNotifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyTimerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun plannerButtonOpensTheActiveSessionInsteadOfSayingStart() {
        val active = session(StudySessionState.ACTIVE)
        var opened = false
        compose.setContent {
            FocusTheme {
                planner(activeSession = active, onOpenStudyTimer = { opened = true })
            }
        }

        compose.onNodeWithText("Ver sesión en curso").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun plannerButtonOffersPendingReviewWhenTheSessionHasEnded() {
        compose.setContent {
            FocusTheme {
                planner(pendingReview = session(StudySessionState.PENDING_REVIEW))
            }
        }

        compose.onNodeWithText("Revisar sesión").assertIsDisplayed()
    }

    @Test
    fun pendingReviewOffersAWorkingWayToContinueAnUnfinishedSession() {
        var continued = false
        compose.setContent {
            FocusTheme {
                StudyTimerScreen(
                    session = null,
                    pendingReview = session(StudySessionState.PENDING_REVIEW),
                    focusAreas = emptyList(),
                    notificationPermissionDenied = false,
                    onBack = {},
                    onStart = { _, _, _ -> },
                    onStop = {},
                    onResumeSession = { continued = true },
                    onOpenFocusAreaSettings = {},
                    onReview = { _, _, _ -> },
                    onEndBreak = {},
                )
            }
        }

        compose.onNodeWithText("Continuar sesión").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun expiredReviewDoesNotOfferToContinueTheSession() {
        val expired = session(StudySessionState.PENDING_REVIEW).copy(
            endedAt = Instant.now(),
            durationMillis = Duration.ofMinutes(10).toMillis(),
            actualMillis = Duration.ofMinutes(10).toMillis(),
        )
        compose.setContent {
            FocusTheme {
                StudyTimerScreen(
                    session = null,
                    pendingReview = expired,
                    focusAreas = emptyList(),
                    notificationPermissionDenied = false,
                    onBack = {},
                    onStart = { _, _, _ -> },
                    onStop = {},
                    onResumeSession = {},
                    onOpenFocusAreaSettings = {},
                    onReview = { _, _, _ -> },
                    onEndBreak = {},
                )
            }
        }

        compose.onNodeWithText("Continuar sesión").assertDoesNotExist()
    }

    @Test
    fun continueOptionDisappearsWhenTheRemainingTimeExpiresOnTheReviewScreen() {
        val startedAt = Instant.now()
        val aboutToExpire = session(StudySessionState.PENDING_REVIEW).copy(
            startedAt = startedAt,
            durationMillis = 3_000,
            endedAt = startedAt,
            actualMillis = 0,
        )
        compose.setContent {
            FocusTheme {
                StudyTimerScreen(
                    session = null,
                    pendingReview = aboutToExpire,
                    focusAreas = emptyList(),
                    notificationPermissionDenied = false,
                    onBack = {},
                    onStart = { _, _, _ -> },
                    onStop = {},
                    onResumeSession = {},
                    onOpenFocusAreaSettings = {},
                    onReview = { _, _, _ -> },
                    onEndBreak = {},
                )
            }
        }

        compose.onNodeWithText("Continuar sesión").assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Continuar sesión").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun activeTimerShowsElapsedAndTotalTimeAlongsideRemainingTime() {
        compose.setContent {
            FocusTheme {
                StudyTimerScreen(
                    session = session(StudySessionState.ACTIVE),
                    pendingReview = null,
                    focusAreas = emptyList(),
                    notificationPermissionDenied = false,
                    onBack = {},
                    onStart = { _, _, _ -> },
                    onStop = {},
                    onResumeSession = {},
                    onOpenFocusAreaSettings = {},
                    onReview = { _, _, _ -> },
                    onEndBreak = {},
                )
            }
        }

        compose.onNodeWithText("Tiempo restante").assertIsDisplayed()
        compose.onNodeWithText("Transcurrido 00:10").assertIsDisplayed()
        compose.onNodeWithText("Total 01:00").assertIsDisplayed()
        compose.onNodeWithText("Terminar y revisar").assertIsDisplayed()
    }

    @Test
    fun activeTimerPublishesAnOngoingCountdownNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val command = instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            FileInputStream(command.fileDescriptor).bufferedReader().use { it.readText() }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            org.junit.Assert.assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
        org.junit.Assert.assertTrue("App notifications should be enabled", manager.areNotificationsEnabled())
        StudySessionNotifier.dismiss(context)

        val active = session(StudySessionState.ACTIVE)
        StudySessionNotifier.showActive(context, active)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var activeNotifications = manager.activeNotifications
        var posted = activeNotifications.singleOrNull { it.id == 41 }
        while (posted == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100)
            activeNotifications = manager.activeNotifications
            posted = activeNotifications.singleOrNull { it.id == 41 }
        }

        org.junit.Assert.assertNotNull(
            "A running study session should publish a notification. permission=${context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)}, " +
                "enabled=${manager.areNotificationsEnabled()}, channel=${manager.getNotificationChannel("study_session_active_v2")?.importance}, " +
                "activeIds=${activeNotifications.map { it.id }}",
            posted,
        )
        val notification = posted!!.notification
        org.junit.Assert.assertTrue("The notification should remain ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        org.junit.Assert.assertTrue("The notification should show a live chronometer", notification.extras.getBoolean("android.showChronometer"))
        org.junit.Assert.assertTrue("The chronometer should count down", notification.extras.getBoolean("android.chronometerCountDown"))
        org.junit.Assert.assertEquals(active.startedAt.toEpochMilli() + active.durationMillis, notification.`when`)
        org.junit.Assert.assertEquals("Lectura", notification.extras.getString(Notification.EXTRA_TITLE))
        org.junit.Assert.assertEquals("Tiempo de estudio restante", notification.extras.getString(Notification.EXTRA_TEXT))
        org.junit.Assert.assertNotNull("The notification should reopen the session screen", notification.contentIntent)
        StudySessionNotifier.dismiss(context)
    }

    @Composable
    private fun planner(
        activeSession: StudyTimerSession? = null,
        pendingReview: StudyTimerSession? = null,
        onOpenStudyTimer: () -> Unit = {},
    ) {
        PlannerScreen(
            date = LocalDate.of(2026, 11, 16),
            isTodaySelected = true,
            blocks = emptyList(),
            allBlocks = emptyList(),
            studySessions = emptyList(),
            activeStudySession = activeSession,
            pendingSessionReview = pendingReview,
            settings = FocusSettings(),
            restrictionActive = false,
            hasSyncKey = false,
            syncStatus = SyncStatus.NOT_CONFIGURED,
            accessibilityEnabled = true,
            exactAlarmsAllowed = true,
            onSelectToday = {},
            onSelectTomorrow = {},
            onOpenSettings = {},
            onOpenStudyTimer = onOpenStudyTimer,
            onOpenDailyReview = {},
            onSyncNow = {},
            onOpenTomorrowPlan = {},
            onAddBlock = {},
            onEditBlock = {},
        )
    }

    private fun session(state: StudySessionState): StudyTimerSession {
        val startedAt = Instant.now().minus(Duration.ofMinutes(10))
        return StudyTimerSession.start("Lectura", Duration.ofMinutes(60), AccessProfile.ESSENTIALS_ONLY, startedAt)
            .copy(
                state = state,
                endedAt = if (state == StudySessionState.PENDING_REVIEW) startedAt.plus(Duration.ofMinutes(10)) else null,
                actualMillis = if (state == StudySessionState.PENDING_REVIEW) Duration.ofMinutes(10).toMillis() else 0,
                outcome = if (state == StudySessionState.PENDING_REVIEW) SessionOutcome.NOT_FINISHED else null,
            )
    }
}
