package com.jp.freedom.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PolicyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PolicyAlarmScheduler.scheduleNext(context)
        PolicyAlarmScheduler.broadcastRecheck(context)
    }
}

class PolicyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PolicyAlarmScheduler.scheduleNext(context)
        PolicyAlarmScheduler.broadcastRecheck(context)
    }
}
