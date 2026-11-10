package com.jp.freedom.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.DayPlanPolicy
import java.time.LocalDate
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
        val settings = repository.settings()
        val blocks = repository.allBlocks()
        val upcoming = mutableListOf<LocalDateTime>()
        repository.activeStudySession()?.let { session ->
            upcoming += LocalDateTime.ofInstant(session.startedAt.plusMillis(session.durationMillis), ZoneId.systemDefault()).takeIf { it.isAfter(now) } ?: now.plusSeconds(1)
        }

        for (offset in 0L..1L) {
            val date = LocalDate.now().plusDays(offset)
            blocks.asSequence()
                .filter { it.date == date }
                .flatMap { sequenceOf(date.atTime(it.startTime), date.atTime(it.endTime)) }
                .filter { it.isAfter(now) }
                .forEach(upcoming::add)

            if (DayPlanPolicy.requiredPlanDateForEvening(date) != null) {
                date.atTime(settings.eveningCutoff).takeIf { it.isAfter(now) }?.let(upcoming::add)
            }
        }
        LocalDate.now().plusDays(1).atStartOfDay().takeIf { it.isAfter(now) }?.let(upcoming::add)

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
