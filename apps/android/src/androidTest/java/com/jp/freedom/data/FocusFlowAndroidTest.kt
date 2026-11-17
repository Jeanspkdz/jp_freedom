package com.jp.freedom.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.FocusArea
import com.jp.freedom.domain.SessionOutcome
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.enforcement.DailyReviewReminder
import com.jp.freedom.enforcement.DailyReviewReminderReceiver
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusFlowAndroidTest {
    @Test fun versionTwentyDatabaseWithoutFocusAreasRecoversOnUpgrade() {
        val context = isolatedContext()
        context.deleteDatabase("jp-freedom.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("jp-freedom.db"), null).use { db ->
                db.version = 20
            }
            assertEquals(emptyList<FocusArea>(), FocusRepository(context).focusAreas())
        } finally {
            context.deleteDatabase("jp-freedom.db")
        }
    }

    @Test fun legacyFocusAreaTableMigratesAndKeepsSessionAssignments() {
        val context = isolatedContext()
        context.deleteDatabase("jp-freedom.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("jp-freedom.db"), null).use { db ->
                db.execSQL("CREATE TABLE focus_areas (name TEXT PRIMARY KEY NOT NULL)")
                db.execSQL("INSERT INTO focus_areas (name) VALUES ('English')")
                db.execSQL("CREATE TABLE study_sessions (id TEXT PRIMARY KEY, focus_area TEXT, focus_area_id TEXT)")
                db.execSQL("INSERT INTO study_sessions (id, focus_area) VALUES ('session-1', 'English')")
                db.version = 19
            }

            val repository = FocusRepository(context)
            val area = repository.focusAreas().single()
            assertEquals("English", area.name)

            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("jp-freedom.db"), null).use { db ->
                assertEquals(21, db.version)
                db.query("focus_areas", arrayOf("id", "name", "position"), null, null, null, null, null).use { cursor ->
                    assertEquals(1, cursor.count)
                    assertEquals(setOf("id", "name", "position"), (0 until cursor.columnCount).map { cursor.getColumnName(it) }.toSet())
                }
                db.query("study_sessions", arrayOf("focus_area_id"), "id = ?", arrayOf("session-1"), null, null, null).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(area.id, cursor.getString(0))
                }
            }
        } finally {
            context.deleteDatabase("jp-freedom.db")
        }
    }

    @Test fun focusAreaAndSessionAssignmentPersistAcrossRepositoryInstances() {
        val context = isolatedContext()
        context.deleteDatabase("jp-freedom.db")
        try {
            val repository = FocusRepository(context)
            val area = FocusArea("english", "English")
            repository.saveFocusAreas(listOf(area))
            repository.saveStudySession(
                StudyTimerSession(
                    id = "session-1",
                    taskName = "Vocabulary",
                    durationMillis = Duration.ofMinutes(25).toMillis(),
                    accessProfile = AccessProfile.ESSENTIALS_ONLY,
                    startedAt = Instant.parse("2026-11-13T15:00:00Z"),
                    endedAt = Instant.parse("2026-11-13T15:25:00Z"),
                    actualMillis = Duration.ofMinutes(25).toMillis(),
                    state = StudySessionState.REVIEWED,
                    focusAreaId = area.id,
                    outcome = SessionOutcome.FINISHED,
                    note = "Reviewed words",
                ),
            )

            val reopened = FocusRepository(context)
            assertEquals(listOf(area), reopened.focusAreas())
            assertEquals(area.id, reopened.studySessions().single().focusAreaId)
            assertEquals(SessionOutcome.FINISHED, reopened.studySessions().single().outcome)
            assertEquals("Reviewed words", reopened.studySessions().single().note)
        } finally {
            context.deleteDatabase("jp-freedom.db")
        }
    }

    @Test fun reminderTimePersistsAndReceiverPostsNotificationForToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val originalTime = DailyReviewReminder.time(context)
        try {
            val testTime = LocalTime.of(12, 42)
            DailyReviewReminder.saveTime(context, testTime)
            assertEquals(testTime, DailyReviewReminder.time(context))

            DailyReviewReminderReceiver().onReceive(context, null)

            val reminder = manager.activeNotifications.singleOrNull { it.id == 85 }
            assertNotNull("Daily review reminder should be posted", reminder)
            assertEquals("Review your study day", reminder!!.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertNotNull("Reminder tap should open today's review", reminder.notification.contentIntent)
            assertEquals(LocalDate.now().toString(), DailyReviewReminder.openReviewIntent(context, LocalDate.now()).getStringExtra(DailyReviewReminder.EXTRA_DATE))
        } finally {
            manager.cancel(85)
            DailyReviewReminder.saveTime(context, originalTime)
        }
    }

    private fun isolatedContext(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "focus-flow-android-test").apply { mkdirs() }
        return object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun deleteDatabase(name: String): Boolean = getDatabasePath(name).delete()
        }
    }
}
