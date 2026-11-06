package com.jp.freedom

import android.os.Bundle
import androidx.activity.ComponentActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = FocusRepository(applicationContext)
        PolicyAlarmScheduler.scheduleNext(this)
        blockedReason = BlockedReason.fromIntentId(intent.getStringExtra(EXTRA_BLOCKED_REASON))
        setContent {
            FocusTheme {
                FocusApp(
                    repository = repository,
                    activityResumeVersion = resumeVersion,
                    initialBlockedReason = blockedReason,
                    blockedRequestVersion = blockedRequestVersion,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            val syncConfiguration = SyncConfigurationStore(applicationContext).configuration()
            if (syncConfiguration.endpoint.isNotBlank() && syncConfiguration.hasWriteToken) {
                SyncScheduler.schedule(this)
            }
        }
        SiteFilterVpnService.startIfAuthorized(this)
        resumeVersion++
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedReason = BlockedReason.fromIntentId(intent.getStringExtra(EXTRA_BLOCKED_REASON))
        if (blockedReason != null) blockedRequestVersion++
    }

    companion object {
        const val EXTRA_BLOCKED_REASON = "com.jp.freedom.extra.BLOCKED_REASON"
    }
}

