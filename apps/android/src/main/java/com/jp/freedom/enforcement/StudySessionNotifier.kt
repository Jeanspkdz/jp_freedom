package com.jp.freedom.enforcement

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.jp.freedom.MainActivity
import com.jp.freedom.domain.StudyTimerSession

object StudySessionNotifier {
    private const val CHANNEL = "study_session_review"
    const val EXTRA_OPEN_SESSION_REVIEW = "com.jp.freedom.extra.OPEN_SESSION_REVIEW"

    fun show(context: Context, session: StudyTimerSession) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Study timer", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_SESSION_REVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 41, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Study session complete")
            .setContentText("Review your task and record how it went.")
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(41, notification)
    }

    fun dismiss(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(41)
    }
}
