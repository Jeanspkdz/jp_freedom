package com.jp.freedom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.enforcement.BlockedReason
import com.jp.freedom.enforcement.PolicyAlarmScheduler
import com.jp.freedom.enforcement.SiteFilterVpnService
import com.jp.freedom.sync.SyncConfigurationStore
import com.jp.freedom.sync.SyncScheduler
import com.jp.freedom.ui.FocusApp
import com.jp.freedom.ui.FocusTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: FocusRepository
    private var resumeVersion by mutableIntStateOf(0)
    private var blockedReason by mutableStateOf<BlockedReason?>(null)
    private var blockedRequestVersion by mutableIntStateOf(0)
    private var openSessionReviewVersion by mutableIntStateOf(0)
    private var openSessionTimerVersion by mutableIntStateOf(0)
    private var openDailyReviewVersion by mutableIntStateOf(0)
    private var requestedDailyReviewDate by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        repository = FocusRepository(applicationContext)
        PolicyAlarmScheduler.scheduleNext(this)
        com.jp.freedom.enforcement.DailyReviewReminder.schedule(this)
        blockedReason = BlockedReason.fromIntentId(intent.getStringExtra(EXTRA_BLOCKED_REASON))
        if (intent.getBooleanExtra(com.jp.freedom.enforcement.StudySessionNotifier.EXTRA_OPEN_SESSION_REVIEW, false)) openSessionReviewVersion++
        if (intent.getBooleanExtra(com.jp.freedom.enforcement.StudySessionNotifier.EXTRA_OPEN_SESSION_TIMER, false)) openSessionTimerVersion++
        requestedDailyReviewDate = intent.getStringExtra(com.jp.freedom.enforcement.DailyReviewReminder.EXTRA_DATE)
        if (requestedDailyReviewDate != null) openDailyReviewVersion++
        setContent {
            FocusTheme {
                FocusApp(
                    repository = repository,
                    activityResumeVersion = resumeVersion,
                    initialBlockedReason = blockedReason,
                    blockedRequestVersion = blockedRequestVersion,
                    openSessionReviewVersion = openSessionReviewVersion,
                    openSessionTimerVersion = openSessionTimerVersion,
                    openDailyReviewVersion = openDailyReviewVersion,
                    requestedDailyReviewDate = requestedDailyReviewDate,
                    showLaunchSplash = savedInstanceState == null && intent.action == android.content.Intent.ACTION_MAIN,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized && SyncConfigurationStore(applicationContext).configuration().hasWriteToken) {
            SyncScheduler.schedule(this)
        }
        SiteFilterVpnService.startIfAuthorized(this)
        resumeVersion++
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedReason = BlockedReason.fromIntentId(intent.getStringExtra(EXTRA_BLOCKED_REASON))
        if (blockedReason != null) blockedRequestVersion++
        if (intent.getBooleanExtra(com.jp.freedom.enforcement.StudySessionNotifier.EXTRA_OPEN_SESSION_REVIEW, false)) openSessionReviewVersion++
        if (intent.getBooleanExtra(com.jp.freedom.enforcement.StudySessionNotifier.EXTRA_OPEN_SESSION_TIMER, false)) openSessionTimerVersion++
        requestedDailyReviewDate = intent.getStringExtra(com.jp.freedom.enforcement.DailyReviewReminder.EXTRA_DATE)
        if (requestedDailyReviewDate != null) openDailyReviewVersion++
    }

    companion object {
        const val EXTRA_BLOCKED_REASON = "com.jp.freedom.extra.BLOCKED_REASON"
    }
}

