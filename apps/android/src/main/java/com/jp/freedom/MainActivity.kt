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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        repository = FocusRepository(applicationContext)
        PolicyAlarmScheduler.scheduleNext(this)
        blockedReason = BlockedReason.fromIntentId(intent.getStringExtra(EXTRA_BLOCKED_REASON))
        if (intent.getBooleanExtra(com.jp.freedom.enforcement.StudySessionNotifier.EXTRA_OPEN_SESSION_REVIEW, false)) openSessionReviewVersion++
        setContent {
            FocusTheme {
                FocusApp(
                    repository = repository,
                    activityResumeVersion = resumeVersion,
                    initialBlockedReason = blockedReason,
                    blockedRequestVersion = blockedRequestVersion,
                    openSessionReviewVersion = openSessionReviewVersion,
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
    }

    companion object {
        const val EXTRA_BLOCKED_REASON = "com.jp.freedom.extra.BLOCKED_REASON"
    }
}

