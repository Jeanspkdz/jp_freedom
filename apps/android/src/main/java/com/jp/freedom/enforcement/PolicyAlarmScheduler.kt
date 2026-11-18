package com.jp.freedom.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jp.freedom.data.FocusRepository
import java.time.LocalDateTime
import java.time.ZoneId

object PolicyAlarmScheduler {
    const val ACTION_POLICY_ALARM = "com.jp.freedom.action.POLICY_ALARM"
    const val ACTION_POLICY_RECHECK = "com.jp.freedom.action.POLICY_RECHECK"

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        val now = LocalDateTime.now()
        val repository = FocusRepository(context)
        val upcoming = mutableListOf<LocalDateTime>()
        repository.activeStudySession()?.let { session ->
            val ending = if (session.state == com.jp.freedom.domain.StudySessionState.BREAKING) {
                session.breakStartedAt?.plusMillis(session.breakDurationMillis)
            } else session.startedAt.plusMillis(session.durationMillis)
            ending?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()).takeIf { end -> end.isAfter(now) } ?: now.plusSeconds(1) }?.let(upcoming::add)
        }

        val next = upcoming.minOrNull()
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun broadcastRecheck(context: Context) {
        context.sendBroadcast(Intent(ACTION_POLICY_RECHECK).setPackage(context.packageName))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PolicyAlarmReceiver::class.java).setAction(ACTION_POLICY_ALARM)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
