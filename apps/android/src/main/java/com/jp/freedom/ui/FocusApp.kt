package com.jp.freedom.ui

import android.app.Activity
import android.Manifest
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
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.StudyTimerController
import com.jp.freedom.domain.StudyTimerDestination
import com.jp.freedom.enforcement.BlockedReason
import com.jp.freedom.enforcement.FocusAccessibilityService
import com.jp.freedom.enforcement.FocusPolicy
import com.jp.freedom.enforcement.PolicyAlarmScheduler
import com.jp.freedom.enforcement.SiteFilterVpnService
import com.jp.freedom.domain.BlocklistValidation
import com.jp.freedom.sync.AndroidSyncJobService
import com.jp.freedom.sync.SyncConfiguration
import com.jp.freedom.sync.SyncConfigurationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.Instant

private enum class AppPage {
    PLANNER,
    BLOCK_EDITOR,
    SETTINGS,
    BLOCKED,
    STUDY_TIMER,
    SESSION_REVIEW,
    DAILY_REVIEW,
}

@Composable
fun FocusApp(
    repository: FocusRepository,
    activityResumeVersion: Int,
    initialBlockedReason: BlockedReason?,
    blockedRequestVersion: Int,
    openSessionReviewVersion: Int,
    openDailyReviewVersion: Int,
    requestedDailyReviewDate: String?,
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
    var studySession by remember { mutableStateOf<StudyTimerSession?>(null) }
    var pendingReview by remember { mutableStateOf<StudyTimerSession?>(null) }
    var studySessions by remember { mutableStateOf(emptyList<StudyTimerSession>()) }
    var dailyReviewDate by remember { mutableStateOf(requestedDailyReviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var dailyReviewReminderTime by remember { mutableStateOf(com.jp.freedom.enforcement.DailyReviewReminder.time(context).toString()) }
    val studyTimerController = remember(repository) { StudyTimerController(repository) }
    var queuedTimerStart by remember { mutableStateOf<Pair<Duration, AccessProfile>?>(null) }
    fun persistTimerStart(duration: Duration, profile: AccessProfile) {
        scope.launch {
            val started = withContext(Dispatchers.IO) { studyTimerController.start(duration, profile) }
            if (started != null) {
                PolicyAlarmScheduler.scheduleNext(context)
                PolicyAlarmScheduler.broadcastRecheck(context)
                page = AppPage.STUDY_TIMER
                refreshVersion++
            } else {
                pendingReview = withContext(Dispatchers.IO) { repository.pendingStudyReview() }
                page = AppPage.SESSION_REVIEW
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        queuedTimerStart?.let { (duration, profile) -> persistTimerStart(duration, profile) }
        queuedTimerStart = null
    }
    val startTimer: (Duration, AccessProfile) -> Unit = { duration, profile ->
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            queuedTimerStart = duration to profile
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else persistTimerStart(duration, profile)
    }
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
                val ended = studyTimerController.finishIfDue()
                if (ended != null) {
                    com.jp.freedom.enforcement.StudySessionNotifier.show(context, ended)
                    PolicyAlarmScheduler.scheduleNext(context)
                    PolicyAlarmScheduler.broadcastRecheck(context)
                }
                if (studyTimerController.finishBreakIfDue() != null) {
                    PolicyAlarmScheduler.scheduleNext(context)
                    PolicyAlarmScheduler.broadcastRecheck(context)
                }
                val timer = repository.activeStudySession()
                val review = repository.pendingStudyReview()
                val sessions = repository.studySessions()
                val policy = FocusPolicy.evaluate(
                    now = LocalDateTime.now(),
                    blocks = blocks,
                    settings = settings,
                    studySession = repository.activeStudySession(),
                )
                val restricted = policy.blockedReason == BlockedReason.EVENING_RESTRICTION
                val policyBlocked = policy.blockedReason != null
            PlannerSnapshot(blocks, settings, domains, sync, restricted, policyBlocked, timer, review, sessions)
            }
            allBlocks = snapshot.blocks
            focusSettings = snapshot.settings
            desktopBlockedDomains = snapshot.desktopBlockedDomains
            syncState = snapshot.syncState
            restrictionActive = snapshot.restricted
            currentPolicyBlocked = snapshot.policyBlocked
            studySession = snapshot.studySession
            pendingReview = snapshot.pendingReview
            studySessions = snapshot.studySessions
            stateLoaded = true
            delay(if (studySession != null || page == AppPage.STUDY_TIMER) 1_000 else if (page == AppPage.SETTINGS) 1_500 else 60_000)
        }
    }

    LaunchedEffect(blockedRequestVersion, initialBlockedReason) {
        if (initialBlockedReason != null) page = AppPage.BLOCKED
    }

    LaunchedEffect(openSessionReviewVersion, stateLoaded) {
        if (openSessionReviewVersion > 0 && stateLoaded) {
            val destination = withContext(Dispatchers.IO) { studyTimerController.destination(openReviewRequest = true) }
            page = if (destination == StudyTimerDestination.REVIEW) AppPage.SESSION_REVIEW else AppPage.PLANNER
        }
    }

    LaunchedEffect(openDailyReviewVersion, stateLoaded, requestedDailyReviewDate) {
        if (openDailyReviewVersion > 0 && stateLoaded) {
            dailyReviewDate = requestedDailyReviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            page = AppPage.DAILY_REVIEW
        }
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
            AppPage.BLOCK_EDITOR, AppPage.SETTINGS, AppPage.BLOCKED, AppPage.STUDY_TIMER, AppPage.SESSION_REVIEW, AppPage.DAILY_REVIEW -> AppPage.PLANNER
            AppPage.PLANNER -> AppPage.PLANNER
        }
    }

    val accessibilityEnabled = remember(activityResumeVersion) {
        isAccessibilityServiceEnabled(context)
    }
    val exactAlarmsAllowed = remember(activityResumeVersion) {
        canScheduleExactAlarms(context)
    }
    val syncNow: () -> Unit = {
        scope.launch {
            syncState = syncState.copy(status = SyncStatus.SYNCING, message = null)
            val result = withContext(Dispatchers.IO) {
                runCatching { AndroidSyncJobService.syncNow(context) }
            }
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                withContext(Dispatchers.IO) {
                    repository.markSyncError(error?.message ?: "Couldn't sync. Try again.")
                }
            }
            refreshVersion++
        }
    }

    when (page) {
        AppPage.PLANNER -> PlannerScreen(
            date = selectedDate,
            isTodaySelected = dayOffset == 0,
            blocks = allBlocks.filter { it.date == selectedDate },
            allBlocks = allBlocks,
            studySessions = studySessions,
            settings = focusSettings,
            restrictionActive = restrictionActive,
            hasSyncKey = syncConfiguration.hasWriteToken,
            syncStatus = syncState.status,
            accessibilityEnabled = accessibilityEnabled,
            exactAlarmsAllowed = exactAlarmsAllowed,
            onSelectToday = { dayOffset = 0 },
            onSelectTomorrow = { dayOffset = 1 },
            onOpenSettings = { page = AppPage.SETTINGS },
            onOpenStudyTimer = { page = if (pendingReview != null) AppPage.SESSION_REVIEW else AppPage.STUDY_TIMER },
            onOpenDailyReview = { page = AppPage.DAILY_REVIEW },
            onSyncNow = syncNow,
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
            dailyReviewReminderTime = dailyReviewReminderTime,
            studyHistoryCount = studySessions.count { it.state == com.jp.freedom.domain.StudySessionState.REVIEWED },
            onDailyReviewReminderTimeChange = { value ->
                dailyReviewReminderTime = value
                runCatching { com.jp.freedom.enforcement.DailyReviewReminder.saveTime(context, java.time.LocalTime.parse(value)) }
            },
            onClearStudyHistory = {
                scope.launch {
                    withContext(Dispatchers.IO) { repository.clearStudyHistory() }
                    refreshVersion++
                }
            },
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
                                ?: "Couldn't save the sync settings.",
                        )
                    }
                }
            },
            onSyncNow = syncNow,
            onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
            onRequestExactAlarmAccess = { openExactAlarmSettings(context) },
            onRequestSiteFilter = {
                SiteFilterVpnService.requestPermission(context) { intent ->
                    siteFilterPermissionLauncher.launch(intent)
                }
            },
        )

        AppPage.STUDY_TIMER, AppPage.SESSION_REVIEW -> StudyTimerScreen(
            session = studySession,
            pendingReview = pendingReview,
            onBack = { page = AppPage.PLANNER },
            onStart = startTimer,
            onStop = {
                scope.launch {
                    val stopped = withContext(Dispatchers.IO) {
                        studyTimerController.stop()
                    }
                    studySession = null
                    pendingReview = stopped
                    PolicyAlarmScheduler.scheduleNext(context)
                    PolicyAlarmScheduler.broadcastRecheck(context)
                    page = AppPage.SESSION_REVIEW
                }
            },
            onReview = { task, status, note, remaining ->
                scope.launch {
                    val reviewed = withContext(Dispatchers.IO) {
                        studyTimerController.review(task, status, note, remaining, Duration.ofMinutes(focusSettings.breakMinutes.toLong()))
                    }
                    com.jp.freedom.enforcement.StudySessionNotifier.dismiss(context)
                    pendingReview = null
                    studySession = reviewed
                    page = AppPage.STUDY_TIMER
                    PolicyAlarmScheduler.scheduleNext(context)
                    PolicyAlarmScheduler.broadcastRecheck(context)
                    refreshVersion++
                }
            },
            onEndBreak = { nextTask ->
                scope.launch {
                    val ended = withContext(Dispatchers.IO) { studyTimerController.endBreak(nextTask) }
                    if (ended != null) {
                        studySession = null
                        PolicyAlarmScheduler.scheduleNext(context)
                        PolicyAlarmScheduler.broadcastRecheck(context)
                        page = AppPage.PLANNER
                        refreshVersion++
                    }
                }
            },
        )

        AppPage.DAILY_REVIEW -> DailyReviewScreen(
            review = com.jp.freedom.domain.StudyTimerPolicy.dailyReview(
                dailyReviewDate, java.time.ZoneId.systemDefault(), focusSettings.studyHoursTargetMinutes,
                studySessions, allBlocks,
            ),
            onBack = { page = AppPage.PLANNER },
            onResolveImportConflict = { phoneCopyId, keepPhoneVersion ->
                val serverId = phoneCopyId.removeSuffix("-phone-copy")
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteStudyHistoryEntry(if (keepPhoneVersion) serverId else phoneCopyId)
                    }
                    refreshVersion++
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
    val studySession: StudyTimerSession?,
    val pendingReview: StudyTimerSession?,
    val studySessions: List<StudyTimerSession>,
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

