package com.jp.freedom.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PolicyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        completeExpiredStudySession(context)
        PolicyAlarmScheduler.scheduleNext(context)
        PolicyAlarmScheduler.broadcastRecheck(context)
        DailyReviewReminder.schedule(context)
    }
}

class PolicyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        completeExpiredStudySession(context)
        PolicyAlarmScheduler.scheduleNext(context)
        PolicyAlarmScheduler.broadcastRecheck(context)
        DailyReviewReminder.schedule(context)
    }
}

private fun completeExpiredStudySession(context: Context) {
    val repository = com.jp.freedom.data.FocusRepository(context)
    repository.activeStudySession()?.let { session ->
        com.jp.freedom.domain.StudyTimerPolicy.finishIfDue(session, java.time.Instant.now())?.let { ended ->
            if (repository.finishStudySession(ended)) StudySessionNotifier.show(context, ended)
        }
    }
}
