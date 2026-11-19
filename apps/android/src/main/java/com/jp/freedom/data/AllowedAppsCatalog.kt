package com.jp.freedom.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.jp.freedom.domain.InstalledApp
import com.jp.freedom.enforcement.FocusAccessibilityService
import java.util.Locale

object AllowedAppsCatalog {
    fun installedLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val permanentlyAvailable = FocusAccessibilityService.essentialPackages(context).toMutableSet().apply {
            add(context.packageName)
            ComponentName.unflattenFromString(
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
            )?.packageName?.let(::add)
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName
                if (packageName in permanentlyAvailable || !appInfo.enabled) return@mapNotNull null
                InstalledApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString().trim().ifBlank { packageName },
                )
            }
            .distinctBy(InstalledApp::packageName)
            .sortedWith(compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName.lowercase(Locale.ROOT) })
            .toList()
    }
}
