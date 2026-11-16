package com.jp.freedom.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AccessProfilePackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_REMOVED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val removed = intent.data?.schemeSpecificPart ?: return
        FocusRepository(context).removePackageFromProfiles(removed)
    }
}
