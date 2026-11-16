package com.jp.freedom.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.jp.freedom.MainActivity
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.domain.StudyTimerPolicy
import java.time.LocalDateTime

class FocusAccessibilityService : AccessibilityService() {
    private val repository by lazy { FocusRepository(applicationContext) }
    private var foregroundPackage: String? = null
    private var blockScreenIsOpen = false

    private val policyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = enforceCurrentPolicy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(PolicyAlarmScheduler.ACTION_POLICY_RECHECK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(policyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(policyReceiver, filter)
        }
        PolicyAlarmScheduler.scheduleNext(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        foregroundPackage = packageName
        if (packageName == this.packageName) {
            blockScreenIsOpen = false
        }
        enforceCurrentPolicy()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(policyReceiver) }
        super.onDestroy()
    }

    private fun enforceCurrentPolicy() {
        val packageName = foregroundPackage ?: return
        repository.activeStudySession()?.let { session ->
            StudyTimerPolicy.finishIfDue(session, java.time.Instant.now())?.let { ended ->
                if (repository.finishStudySession(ended)) {
                    StudySessionNotifier.show(this, ended)
                    PolicyAlarmScheduler.scheduleNext(this)
                }
            }
        }
        val policy = FocusPolicy.evaluate(
            now = LocalDateTime.now(),
            blocks = repository.allBlocks(),
            settings = repository.settings(),
            studySession = repository.activeStudySession(),
        )
        val policyReason = policy.blockedReason
        if (policyReason == null) {
            blockScreenIsOpen = false
            return
        }

        val essentialPackages = essentialPackages()
        val activeBlock = policy.activeBlock
        val chromeDuringBlock = activeBlock != null && packageName == CHROME_PACKAGE
        val siteFilterRunning = chromeDuringBlock && SiteFilterVpnService.isRunning()
        val reason = policyReason
        val allowedPackages = FocusPolicy.allowedPackages(
            state = policy,
            ownPackage = this.packageName,
            essentialPackages = essentialPackages,
            siteFilterRunning = siteFilterRunning,
            defaultInputMethodPackage = defaultInputMethodPackage(),
        )
        if (packageName in allowedPackages || packageName == "com.android.systemui" || packageName == "android") {
            blockScreenIsOpen = false
            return
        }
        if (blockScreenIsOpen) return

        blockScreenIsOpen = true
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_BLOCKED_REASON, reason.toIntentId())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun essentialPackages(): Set<String> = essentialPackages(this)

    private fun defaultInputMethodPackage(): String? = ComponentName.unflattenFromString(
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
    )?.packageName

    companion object {
        fun essentialPackages(context: Context): Set<String> = buildSet {
            addAll(resolvePackages(context, Intent(Intent.ACTION_DIAL)))
            addAll(resolvePackages(context, Intent(Intent.ACTION_VIEW, Uri.parse("tel:911"))))
            addAll(resolvePackages(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))))
            addAll(resolvePackages(context, Intent("android.intent.action.EMERGENCY_ASSIST")))
            addAll(resolvePackages(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)))
            add("com.android.incallui")
            add("com.android.phone")
            add("com.android.vpndialogs")
            add("com.android.systemui")
            add("android")
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val serviceName = ComponentName(context, FocusAccessibilityService::class.java)
            return android.provider.Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1 && enabled.split(':').any { ComponentName.unflattenFromString(it) == serviceName }
        }

        private fun resolvePackages(context: Context, intent: Intent): Set<String> =
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()

        private const val CHROME_PACKAGE = "com.android.chrome"
    }
}
