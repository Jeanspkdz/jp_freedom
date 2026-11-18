package com.jp.freedom.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.enforcement.DailyReviewReminder
import com.jp.freedom.sync.SyncConfiguration
import java.time.LocalTime
import java.time.ZonedDateTime
import android.app.NotificationManager
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReviewReminderSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private lateinit var originalReminderTime: LocalTime

    @Before
    fun rememberReminderTime() {
        originalReminderTime = DailyReviewReminder.time(context)
    }

    @After
    fun restoreReminderTime() {
        DailyReviewReminder.saveTime(context, originalReminderTime)
    }

    @Test
    fun dailyReviewReminderOpensPickerAndPersistsConfirmedTime() {
        val initialTime = LocalTime.of(12, 34)
        DailyReviewReminder.saveTime(context, initialTime)
        var displayedTime = initialTime.toString()

        compose.setContent {
            FocusTheme {
                SettingsScreen(
                    settings = FocusSettings(),
                    desktopBlockedDomains = emptyList(),
                    activeFocusBlock = false,
                    syncConfiguration = SyncConfiguration("https://example.test", false),
                    syncStatus = SyncStatus.NOT_CONFIGURED,
                    syncRevision = 0,
                    syncMessage = null,
                    accessibilityEnabled = true,
                    exactAlarmsAllowed = true,
                    siteFilterEnabled = false,
                    dailyReviewReminderTime = displayedTime,
                    focusAreas = emptyList(),
                    studyHistoryCount = 0,
                    notificationsAllowed = true,
                    onDailyReviewReminderTimeChange = { value ->
                        displayedTime = value
                        DailyReviewReminder.saveTime(context, LocalTime.parse(value))
                    },
                    onFocusAreasChange = {},
                    onClearStudyHistory = {},
                    onRequestNotificationPermission = {},
                    onBack = {},
                    onSettingsChange = {},
                    onDesktopDomainsChange = {},
                    onConfigureSync = { _, _ -> },
                    onSyncNow = {},
                    onResolveReviewConflict = {},
                    onOpenAccessibilitySettings = {},
                    onOpenAllowedAppsLists = {},
                    onRequestExactAlarmAccess = {},
                    onRequestSiteFilter = {},
                )
            }
        }

        compose.onNodeWithText("Required blocks").assertDoesNotExist()
        compose.onNodeWithText("Cutoff time").assertDoesNotExist()
        compose.onNodeWithText(initialTime.toString()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm").assertIsDisplayed()
        compose.onNodeWithTag("daily-review-hour-picker").performTouchInput { swipeUp() }
        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()

        assertNotEquals(initialTime.toString(), displayedTime)
        assertEquals(LocalTime.parse(displayedTime), DailyReviewReminder.time(context))
        compose.onNodeWithText(displayedTime).assertIsDisplayed()
    }

    @Test
    fun savedReminderTimeDeliversARealNotification() {
        grantReminderPermissions()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(85)
        val deliveryAt = ZonedDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
        DailyReviewReminder.saveTime(context, deliveryAt.toLocalTime())

        val timeout = deliveryAt.plusSeconds(60).toInstant()
        var reminder: android.service.notification.StatusBarNotification? = null
        while (java.time.Instant.now().isBefore(timeout) && reminder == null) {
            reminder = manager.activeNotifications.singleOrNull { it.id == 85 }
            if (reminder == null) Thread.sleep(500)
        }

        assertNotNull("The scheduled reminder should arrive without calling its receiver directly", reminder)
        assertEquals(
            "Review your study day",
            reminder!!.notification.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        assertNotNull("The real notification should open the review", reminder.notification.contentIntent)
        assertEquals(
            deliveryAt.toLocalTime(),
            DailyReviewReminder.time(context),
        )

    }

    private fun grantReminderPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow",
        ).close()
    }

}
