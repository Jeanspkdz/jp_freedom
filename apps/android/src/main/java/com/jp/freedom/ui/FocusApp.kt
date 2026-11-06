package com.jp.freedom.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.data.PendingSyncState
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.domain.BlockValidation
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.enforcement.BlockedReason
import com.jp.freedom.enforcement.FocusAccessibilityService
import com.jp.freedom.enforcement.FocusPolicy
import com.jp.freedom.enforcement.PolicyAlarmScheduler
import com.jp.freedom.enforcement.SiteFilterVpnService
import com.jp.freedom.domain.BlocklistValidation
import com.jp.freedom.sync.SyncConfiguration
import com.jp.freedom.sync.SyncConfigurationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

private enum class AppPage {
    PLANNER,
    BLOCK_EDITOR,
    SETTINGS,
    BLOCKED,
}

@Composable
fun FocusApp(
    repository: FocusRepository,
    activityResumeVersion: Int,
    initialBlockedReason: BlockedReason?,
    blockedRequestVersion: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncConfigurationStore = remember(context) { SyncConfigurationStore(context) }
    var page by rememberSaveable {
        mutableStateOf(if (initialBlockedReason == null) AppPage.PLANNER else AppPage.BLOCKED)
    }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    var editingBlock by remember { mutableStateOf<PlannedBlock?>(null) }
    var editorDate by remember { mutableStateOf(LocalDate.now()) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var allBlocks by remember { mutableStateOf<List<PlannedBlock>>(emptyList()) }
    var focusSettings by remember { mutableStateOf(FocusSettings()) }
    var desktopBlockedDomains by remember { mutableStateOf(com.jp.freedom.domain.DEFAULT_DESKTOP_BLOCKED_DOMAINS) }
    var syncState by remember { mutableStateOf(PendingSyncState(-1, 0, false, SyncStatus.NOT_CONFIGURED, null, emptySet())) }
    var syncConfiguration by remember { mutableStateOf(syncConfigurationStore.configuration()) }
    var restrictionActive by remember { mutableStateOf(false) }
    var currentPolicyBlocked by remember { mutableStateOf(false) }
    var stateLoaded by remember { mutableStateOf(false) }
    var siteFilterEnabled by remember { mutableStateOf(SiteFilterVpnService.isRunning()) }
    val siteFilterPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            SiteFilterVpnService.startIfAuthorized(context)
        }
    }
    val currentDate = LocalDate.now()
    val selectedDate = currentDate.plusDays(dayOffset.toLong())

    LaunchedEffect(dayOffset, activityResumeVersion, refreshVersion, page) {
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val blocks = repository.allBlocks()
                val settings = repository.settings()
                val domains = repository.desktopBlockedDomains()
                val sync = repository.syncState()
                val policy = FocusPolicy.evaluate(
                    now = LocalDateTime.now(),
                    blocks = blocks,
                    settings = settings,
                )
                val restricted = policy.blockedReason == BlockedReason.EVENING_RESTRICTION
                val policyBlocked = policy.blockedReason != null
                PlannerSnapshot(blocks, settings, domains, sync, restricted, policyBlocked)
            }
            allBlocks = snapshot.blocks
            focusSettings = snapshot.settings
            desktopBlockedDomains = snapshot.desktopBlockedDomains
            syncState = snapshot.syncState
            restrictionActive = snapshot.restricted
            currentPolicyBlocked = snapshot.policyBlocked
            stateLoaded = true
            delay(if (page == AppPage.SETTINGS) 1_500 else 60_000)
        }
    }

    LaunchedEffect(blockedRequestVersion, initialBlockedReason) {
        if (initialBlockedReason != null) page = AppPage.BLOCKED
    }

    LaunchedEffect(activityResumeVersion) {
        repeat(5) {
            delay(400)
            siteFilterEnabled = SiteFilterVpnService.isRunning()
            if (siteFilterEnabled) return@LaunchedEffect
        }
    }

    LaunchedEffect(page, currentPolicyBlocked, stateLoaded) {
        if (stateLoaded && page == AppPage.BLOCKED && !currentPolicyBlocked) page = AppPage.PLANNER
    }

    BackHandler(enabled = page != AppPage.PLANNER) {
        page = when (page) {
            AppPage.BLOCK_EDITOR, AppPage.SETTINGS, AppPage.BLOCKED -> AppPage.PLANNER
            AppPage.PLANNER -> AppPage.PLANNER
        }
    }

    val accessibilityEnabled = remember(activityResumeVersion) {
        isAccessibilityServiceEnabled(context)
    }
    val exactAlarmsAllowed = remember(activityResumeVersion) {
        canScheduleExactAlarms(context)
    }

    when (page) {
        AppPage.PLANNER -> PlannerScreen(
            date = selectedDate,
            isTodaySelected = dayOffset == 0,
            blocks = allBlocks.filter { it.date == selectedDate },
            allBlocks = allBlocks,
            settings = focusSettings,
            restrictionActive = restrictionActive,
            onSelectToday = { dayOffset = 0 },
            onSelectTomorrow = { dayOffset = 1 },
            onOpenSettings = { page = AppPage.SETTINGS },
            onOpenTomorrowPlan = {
                dayOffset = 1
                page = AppPage.PLANNER
            },
            onAddBlock = {
                editingBlock = null
                editorDate = selectedDate
                page = AppPage.BLOCK_EDITOR
            },
            onEditBlock = { block ->
                editingBlock = block
                editorDate = block.date
                page = AppPage.BLOCK_EDITOR
            },
        )

        AppPage.BLOCK_EDITOR -> BlockEditorScreen(
            date = editorDate,
            block = editingBlock,
            onBack = { page = AppPage.PLANNER },
            onSave = { block ->
                val result = withContext(Dispatchers.IO) { repository.saveBlock(block) }
                if (result == BlockValidation.Valid) {
                    PolicyAlarmScheduler.scheduleNext(context)
                    refreshVersion++
                }
                result
            },
            onDelete = { id ->
                withContext(Dispatchers.IO) { repository.deleteBlock(id) }
                PolicyAlarmScheduler.scheduleNext(context)
                refreshVersion++
                page = AppPage.PLANNER
            },
        )

        AppPage.SETTINGS -> SettingsScreen(
            settings = focusSettings,
            desktopBlockedDomains = desktopBlockedDomains,
            activeFocusBlock = allBlocks.any { block ->
                val now = LocalDateTime.now()
                block.date == now.toLocalDate() && block.completionStatus != com.jp.freedom.domain.CompletionStatus.FINISHED &&
                    !now.toLocalTime().isBefore(block.startTime) && now.toLocalTime().isBefore(block.endTime)
            },
            syncConfiguration = syncConfiguration,
            syncStatus = syncState.status,
            syncRevision = syncState.revision,
            syncMessage = syncState.message,
            accessibilityEnabled = accessibilityEnabled,
            exactAlarmsAllowed = exactAlarmsAllowed,
            siteFilterEnabled = siteFilterEnabled,
            onBack = { page = AppPage.PLANNER },
            onSettingsChange = { updated ->
                focusSettings = updated
                scope.launch {
                    withContext(Dispatchers.IO) { repository.saveSettings(updated) }
                    PolicyAlarmScheduler.scheduleNext(context)
                    refreshVersion++
                }
            },
            onDesktopDomainsChange = { updated ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { repository.saveDesktopBlockedDomains(updated) }
                    if (result == BlocklistValidation.Valid) {
                        desktopBlockedDomains = updated
                        refreshVersion++
                    }
                }
            },
            onConfigureSync = { endpoint, writeToken ->
                scope.launch {
                    val saved = runCatching {
                        withContext(Dispatchers.IO) {
                            val oldEndpoint = syncConfigurationStore.configuration().endpoint
                            syncConfigurationStore.saveEndpoint(endpoint)
                            if (writeToken.isNotBlank()) syncConfigurationStore.saveWriteToken(writeToken)
                            if (oldEndpoint.isNotBlank() && oldEndpoint != endpoint) {
                                repository.resetSyncForEndpointChange()
                            } else {
                                repository.requestSync()
                            }
                        }
                    }
                    if (saved.isSuccess) {
                        syncConfiguration = syncConfigurationStore.configuration()
                        refreshVersion++
                    } else {
                        syncState = syncState.copy(
                            status = SyncStatus.ERROR,
                            message = saved.exceptionOrNull()?.message
                                ?: "No se pudo guardar la configuración de sincronización.",
                        )
                    }
                }
            },
            onSyncNow = {
                scope.launch(Dispatchers.IO) {
                    runCatching { repository.requestSync() }.onFailure { error ->
                        syncState = syncState.copy(status = SyncStatus.ERROR, message = error.message)
                    }
                }
            },
            onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
            onRequestExactAlarmAccess = { openExactAlarmSettings(context) },
            onRequestSiteFilter = {
                SiteFilterVpnService.requestPermission(context) { intent ->
                    siteFilterPermissionLauncher.launch(intent)
                }
            },
        )

        AppPage.BLOCKED -> BlockedScreen(
            reason = initialBlockedReason,
            onOpenPlanner = { page = AppPage.PLANNER },
            onOpenSettings = { page = AppPage.SETTINGS },
            onBackToBlockedApp = {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                (context as? Activity)?.finish()
            },
        )
    }
}

private data class PlannerSnapshot(
    val blocks: List<PlannedBlock>,
    val settings: FocusSettings,
    val desktopBlockedDomains: List<String>,
    val syncState: PendingSyncState,
    val restricted: Boolean,
    val policyBlocked: Boolean,
)

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return FocusAccessibilityService.isEnabled(context)
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun openExactAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    context.startActivity(intent)
}

