package com.jp.freedom.enforcement

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jp.freedom.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object DailyReviewReminder {
    const val EXTRA_DATE = "com.jp.freedom.extra.DAILY_REVIEW_DATE"
    private const val CHANNEL = "daily_study_review"
    private const val PREFS = "daily_review_reminder"
    private const val TIME = "time"

    fun time(context: Context): LocalTime = runCatching { LocalTime.parse(context.getSharedPreferences(PREFS, 0).getString(TIME, "21:00")) }.getOrDefault(LocalTime.of(21, 0))
    fun saveTime(context: Context, value: LocalTime) { context.getSharedPreferences(PREFS, 0).edit().putString(TIME, value.toString()).apply(); schedule(context) }

    fun schedule(context: Context) {
        val trigger = com.jp.freedom.domain.StudyTimerPolicy.nextReminder(ZonedDateTime.now(), time(context)).toInstant().toEpochMilli()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(context, 84, Intent(context, DailyReviewReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) else alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, pending)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) else alarm.set(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun show(context: Context, date: LocalDate) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily study review", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_DATE, date.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 85, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(85, Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Review your study day").setContentText("See your timer sessions and progress for today.").setContentIntent(open).setAutoCancel(true).build())
    }
}

class DailyReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailyReviewReminder.show(context, LocalDate.now(ZoneId.systemDefault()))
        DailyReviewReminder.schedule(context)
    }
}
