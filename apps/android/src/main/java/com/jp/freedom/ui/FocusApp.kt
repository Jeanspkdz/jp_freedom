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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.jp.freedom.data.FocusRepository
import com.jp.freedom.data.AllowedAppsCatalog
import com.jp.freedom.data.PendingSyncState
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.domain.BlockValidation
import com.jp.freedom.domain.AllowedAppsList
import com.jp.freedom.domain.AllowedAppsListPolicy
import com.jp.freedom.domain.AllowedAppsListValidation
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.FocusArea
import com.jp.freedom.domain.InstalledApp
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.StudyTimerSession
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
    HOME,
    SETTINGS,
    ALLOWED_APPS_LISTS,
    ALLOWED_APPS_LIST_EDITOR,
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
    openSessionTimerVersion: Int,
    openDailyReviewVersion: Int,
    requestedDailyReviewDate: String?,
    showLaunchSplash: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncConfigurationStore = remember(context) { SyncConfigurationStore(context) }
    var page by rememberSaveable {
        mutableStateOf(if (initialBlockedReason == null) AppPage.HOME else AppPage.BLOCKED)
    }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
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
    var focusAreas by remember { mutableStateOf(emptyList<FocusArea>()) }
    var allowedAppsLists by remember { mutableStateOf(emptyList<AllowedAppsList>()) }
    var installedApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selectedAllowedAppsList by remember { mutableStateOf(AllowedAppsList.ESSENTIALS) }
    var allowedAppsListsReturnPage by remember { mutableStateOf(AppPage.SETTINGS) }
    var editingAllowedAppsList by remember { mutableStateOf<AllowedAppsList?>(null) }
    var dailyReviewDate by remember { mutableStateOf(requestedDailyReviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var dailyReviewReminderTime by remember { mutableStateOf(com.jp.freedom.enforcement.DailyReviewReminder.time(context).toString()) }
    val studyTimerController = remember(repository) { StudyTimerController(repository) }
    var queuedTimerStart by remember { mutableStateOf<Triple<Duration, AllowedAppsList, String?>?>(null) }
    fun persistTimerStart(duration: Duration, allowedAppsList: AllowedAppsList, focusAreaId: String?) {
        scope.launch {
            val started = withContext(Dispatchers.IO) { studyTimerController.start(duration, allowedAppsList, focusAreaId) }
            if (started != null) {
                com.jp.freedom.enforcement.StudySessionNotifier.showActive(context, started)
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
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionDenied = !granted
        if (granted) queuedTimerStart?.let { (duration, list, focusAreaId) -> persistTimerStart(duration, list, focusAreaId) }
        queuedTimerStart = null
    }
    val startTimer: (Duration, AllowedAppsList, String?) -> Unit = { duration, allowedAppsList, focusAreaId ->
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            queuedTimerStart = Triple(duration, allowedAppsList, focusAreaId)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else persistTimerStart(duration, allowedAppsList, focusAreaId)
    }
    val notificationsAllowed = (Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    // Keep the shared timer current while the Android app stays open.
    LaunchedEffect(Unit) {
        while (isActive) {
            if (com.jp.freedom.sync.SyncConfigurationStore(context).configuration().hasWriteToken) {
                com.jp.freedom.sync.SyncScheduler.schedule(context)
            }
            delay(60_000)
        }
    }

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
                val areas = repository.focusAreas()
                val policy = FocusPolicy.evaluate(
                    now = LocalDateTime.now(),
                    blocks = blocks,
                    settings = settings,
                    studySession = repository.activeStudySession(),
                )
                val restricted = policy.blockedReason == BlockedReason.EVENING_RESTRICTION
                val policyBlocked = policy.blockedReason != null
            PlannerSnapshot(blocks, settings, domains, sync, restricted, policyBlocked, timer, review, sessions, areas)
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
            focusAreas = snapshot.focusAreas
            stateLoaded = true
            delay(if (studySession != null || page == AppPage.STUDY_TIMER) 1_000 else if (page == AppPage.SETTINGS) 1_500 else 60_000)
        }
    }

    LaunchedEffect(activityResumeVersion, page) {
        if (page in setOf(AppPage.SETTINGS, AppPage.STUDY_TIMER, AppPage.ALLOWED_APPS_LISTS, AppPage.ALLOWED_APPS_LIST_EDITOR)) {
            val loaded = withContext(Dispatchers.IO) {
                val apps = AllowedAppsCatalog.installedLaunchableApps(context)
                repository.removeUninstalledAllowedApps(apps.mapTo(mutableSetOf(), InstalledApp::packageName))
                apps to repository.allowedAppsLists()
            }
            installedApps = loaded.first
            allowedAppsLists = loaded.second
            if (selectedAllowedAppsList.id != AllowedAppsList.ESSENTIALS_ID &&
                loaded.second.none { it.id == selectedAllowedAppsList.id }
            ) selectedAllowedAppsList = AllowedAppsList.ESSENTIALS
        }
    }

    LaunchedEffect(blockedRequestVersion, initialBlockedReason) {
        if (initialBlockedReason != null) page = AppPage.BLOCKED
    }

    LaunchedEffect(openSessionReviewVersion, stateLoaded) {
        if (openSessionReviewVersion > 0 && stateLoaded) {
            val destination = withContext(Dispatchers.IO) { studyTimerController.destination(openReviewRequest = true) }
            page = if (destination == StudyTimerDestination.REVIEW) AppPage.SESSION_REVIEW else AppPage.HOME
        }
    }

    LaunchedEffect(openSessionTimerVersion, stateLoaded) {
        if (openSessionTimerVersion > 0 && stateLoaded) {
            page = when {
                pendingReview != null -> AppPage.SESSION_REVIEW
                studySession != null -> AppPage.STUDY_TIMER
                else -> AppPage.HOME
            }
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
        if (stateLoaded && page == AppPage.BLOCKED && !currentPolicyBlocked) page = AppPage.HOME
    }

    BackHandler(enabled = page != AppPage.HOME) {
        page = when (page) {
            AppPage.SETTINGS, AppPage.BLOCKED, AppPage.STUDY_TIMER, AppPage.SESSION_REVIEW, AppPage.DAILY_REVIEW -> AppPage.HOME
            AppPage.ALLOWED_APPS_LISTS -> allowedAppsListsReturnPage
            AppPage.ALLOWED_APPS_LIST_EDITOR -> AppPage.ALLOWED_APPS_LISTS
            AppPage.HOME -> AppPage.HOME
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

    TallySplashHost(ready = stateLoaded, enabled = showLaunchSplash) {
    when (page) {
        AppPage.HOME -> HomeScreen(
            date = LocalDate.now(),
            studySessions = studySessions,
            activeStudySession = studySession,
            pendingSessionReview = pendingReview,
            settings = focusSettings,
            accessibilityEnabled = accessibilityEnabled,
            onOpenSettings = { page = AppPage.SETTINGS },
            onOpenStudyTimer = {
                if (studySession == null && pendingReview == null) selectedAllowedAppsList = AllowedAppsList.ESSENTIALS
                page = if (pendingReview != null) AppPage.SESSION_REVIEW else AppPage.STUDY_TIMER
            },
            onOpenDailyReview = { page = AppPage.DAILY_REVIEW },
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
            focusAreas = focusAreas,
            notificationsAllowed = notificationsAllowed,
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
            onFocusAreasChange = { updated ->
                focusAreas = updated
                scope.launch {
                    withContext(Dispatchers.IO) { repository.saveFocusAreas(updated) }
                    refreshVersion++
                }
            },
            onRequestNotificationPermission = requestNotificationPermission,
            onBack = { page = AppPage.HOME },
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
            onResolveReviewConflict = { keepPhone ->
                scope.launch {
                    syncState = syncState.copy(status = SyncStatus.SYNCING, message = null)
                    val result = withContext(Dispatchers.IO) {
                        runCatching { AndroidSyncJobService.resolveReviewConflict(context, keepPhone) }
                    }
                    if (result.isFailure) withContext(Dispatchers.IO) {
                        repository.markSyncError(result.exceptionOrNull()?.message ?: "Couldn't resolve the review conflict.")
                    }
                    refreshVersion++
                }
            },
            onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
            onOpenAllowedAppsLists = {
                allowedAppsListsReturnPage = AppPage.SETTINGS
                page = AppPage.ALLOWED_APPS_LISTS
            },
            onRequestExactAlarmAccess = { openExactAlarmSettings(context) },
            onRequestSiteFilter = {
                SiteFilterVpnService.requestPermission(context) { intent ->
                    siteFilterPermissionLauncher.launch(intent)
                }
            },
        )

        AppPage.ALLOWED_APPS_LISTS -> AllowedAppsListsScreen(
            lists = allowedAppsLists,
            installedApps = installedApps,
            selectedListId = selectedAllowedAppsList.id,
            selectionMode = allowedAppsListsReturnPage == AppPage.STUDY_TIMER,
            lockedListId = studySession?.allowedAppsListId ?: pendingReview?.allowedAppsListId,
            onBack = { page = allowedAppsListsReturnPage },
            onSelect = { list ->
                if (allowedAppsListsReturnPage == AppPage.STUDY_TIMER) {
                    selectedAllowedAppsList = list
                    page = AppPage.STUDY_TIMER
                }
            },
            onCreate = {
                editingAllowedAppsList = null
                page = AppPage.ALLOWED_APPS_LIST_EDITOR
            },
            onEdit = { list ->
                editingAllowedAppsList = list
                page = AppPage.ALLOWED_APPS_LIST_EDITOR
            },
            onDelete = { list ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { repository.deleteAllowedAppsList(list.id) }
                    if (result == AllowedAppsListValidation.Valid) {
                        allowedAppsLists = withContext(Dispatchers.IO) { repository.allowedAppsLists() }
                        if (selectedAllowedAppsList.id == list.id) selectedAllowedAppsList = AllowedAppsList.ESSENTIALS
                    }
                }
            },
        )

        AppPage.ALLOWED_APPS_LIST_EDITOR -> AllowedAppsListEditorScreen(
            existingList = editingAllowedAppsList,
            lists = allowedAppsLists,
            installedApps = installedApps,
            locked = AllowedAppsListPolicy.isEditorLocked(
                editorListId = editingAllowedAppsList?.id,
                activeSessionListId = studySession?.allowedAppsListId ?: pendingReview?.allowedAppsListId,
            ),
            onBack = { page = AppPage.ALLOWED_APPS_LISTS },
            onSave = { list ->
                val result = withContext(Dispatchers.IO) { repository.saveAllowedAppsList(list) }
                if (result == AllowedAppsListValidation.Valid) {
                    allowedAppsLists = withContext(Dispatchers.IO) { repository.allowedAppsLists() }
                }
                result
            },
        )

        AppPage.STUDY_TIMER, AppPage.SESSION_REVIEW -> StudyTimerScreen(
            session = studySession,
            pendingReview = pendingReview,
            focusAreas = focusAreas,
            notificationPermissionDenied = notificationPermissionDenied,
            selectedAllowedAppsList = selectedAllowedAppsList,
            onBack = { page = AppPage.HOME },
            onStart = startTimer,
            onOpenAllowedAppsLists = {
                allowedAppsListsReturnPage = AppPage.STUDY_TIMER
                page = AppPage.ALLOWED_APPS_LISTS
            },
            onOpenFocusAreaSettings = { page = AppPage.SETTINGS },
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
            onResumeSession = {
                scope.launch {
                    val resumed = withContext(Dispatchers.IO) { studyTimerController.resumePendingReview() }
                    if (resumed != null) {
                        studySession = resumed
                        pendingReview = null
                        com.jp.freedom.enforcement.StudySessionNotifier.showActive(context, resumed)
                        page = AppPage.STUDY_TIMER
                        PolicyAlarmScheduler.scheduleNext(context)
                        PolicyAlarmScheduler.broadcastRecheck(context)
                        refreshVersion++
                    }
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
                        page = AppPage.HOME
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
            onBack = { page = AppPage.HOME },
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
            onOpenPlanner = { page = AppPage.HOME },
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

    syncState.fieldConflicts.firstOrNull()?.let { conflict ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Choose which value to keep") },
            text = { Text("${conflict.path}\n\nPhone: ${conflict.localValue}\nSynced: ${conflict.remoteValue}") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.resolveFieldConflict(conflict.path, keepRemoteValue = false) }
                        refreshVersion++
                    }
                }) { Text("Keep phone value") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.resolveFieldConflict(conflict.path, keepRemoteValue = true) }
                        refreshVersion++
                    }
                }) { Text("Keep synced value") }
            },
        )
    }
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
    val focusAreas: List<FocusArea>,
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

