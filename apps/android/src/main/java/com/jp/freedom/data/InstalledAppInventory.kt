package com.jp.freedom.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LaunchableApp(val label: String, val packageName: String)

object InstalledAppInventory {
    fun list(context: Context): List<LaunchableApp> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { entry ->
                entry.activityInfo?.packageName?.let { packageName ->
                    LaunchableApp(entry.loadLabel(manager).toString(), packageName)
                }
            }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
