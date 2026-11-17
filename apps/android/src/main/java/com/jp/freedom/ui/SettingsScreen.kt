package com.jp.freedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.FocusArea
import com.jp.freedom.domain.BlocklistValidation
import com.jp.freedom.domain.DesktopSiteBlocklist
import com.jp.freedom.domain.SUPPORTED_EVENING_CUTOFFS
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.sync.EndpointValidation
import com.jp.freedom.sync.SyncConfiguration
import com.jp.freedom.sync.SyncEndpoint
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: FocusSettings,
    desktopBlockedDomains: List<String>,
    activeFocusBlock: Boolean,
    syncConfiguration: SyncConfiguration,
    syncStatus: SyncStatus,
    syncRevision: Long,
    syncMessage: String?,
    accessibilityEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    siteFilterEnabled: Boolean,
    dailyReviewReminderTime: String,
    focusAreas: List<FocusArea>,
    notificationsAllowed: Boolean,
    onDailyReviewReminderTimeChange: (String) -> Unit,
    onFocusAreasChange: (List<FocusArea>) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    onSettingsChange: (FocusSettings) -> Unit,
    onDesktopDomainsChange: (List<String>) -> Unit,
    onConfigureSync: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onResolveReviewConflict: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestSiteFilter: () -> Unit,
) {
    var endpoint by rememberSaveable(syncConfiguration.endpoint) { mutableStateOf(syncConfiguration.endpoint) }
    var writeToken by remember { mutableStateOf("") }
    var newDomain by rememberSaveable { mutableStateOf("") }
    var newFocusArea by rememberSaveable { mutableStateOf("") }
    var reminderDraft by rememberSaveable(dailyReviewReminderTime) { mutableStateOf(dailyReviewReminderTime) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your study routine", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(
                    "Plan tomorrow and choose when the evening restriction starts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("01", "Planning")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsStepper(
                            title = "Daily study-hours target",
                            description = "Total hours to study each day.",
                            value = settings.studyHoursTargetMinutes.hoursLabel(),
                            decreaseLabel = "Decrease study-hours target",
                            increaseLabel = "Increase study-hours target",
                            decreaseEnabled = settings.studyHoursTargetMinutes > 30,
                            increaseEnabled = settings.studyHoursTargetMinutes < 720,
                            onDecrease = { onSettingsChange(settings.copy(studyHoursTargetMinutes = settings.studyHoursTargetMinutes - 30)) },
                            onIncrease = { onSettingsChange(settings.copy(studyHoursTargetMinutes = settings.studyHoursTargetMinutes + 30)) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsStepper(
                            title = "Break after each session",
                            description = "Take a break after this much study time.",
                            value = "${settings.breakMinutes} min",
                            decreaseLabel = "Shorten break",
                            increaseLabel = "Lengthen break",
                            decreaseEnabled = settings.breakMinutes > 1,
                            increaseEnabled = settings.breakMinutes < 60,
                            onDecrease = { onSettingsChange(settings.copy(breakMinutes = settings.breakMinutes - 1)) },
                            onIncrease = { onSettingsChange(settings.copy(breakMinutes = settings.breakMinutes + 1)) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsStepper(
                            title = "Required blocks",
                            description = "Number of study blocks to complete each day.",
                            value = settings.requiredBlockCount.toString(),
                            decreaseLabel = "Decrease minimum",
                            increaseLabel = "Increase minimum",
                            decreaseEnabled = settings.requiredBlockCount > 1,
                            increaseEnabled = settings.requiredBlockCount < Int.MAX_VALUE,
                            onDecrease = { onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount - 1)) },
                            onIncrease = { onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount + 1)) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("02", "Phone protection")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cutoff time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("If the plan is still incomplete, the evening restriction begins.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SUPPORTED_EVENING_CUTOFFS.forEachIndexed { index, cutoff ->
                                SegmentedButton(
                                    selected = settings.eveningCutoff == cutoff,
                                    onClick = { onSettingsChange(settings.copy(eveningCutoff = cutoff)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SUPPORTED_EVENING_CUTOFFS.size),
                                ) { Text(cutoff.label()) }
                            }
                        }
                    }
                }
                PermissionCard(
                    icon = Icons.Outlined.AppBlocking,
                    title = "Website filter in Chrome",
                    enabled = siteFilterEnabled,
                    status = if (siteFilterEnabled) "On" else "Off",
                    description = if (siteFilterEnabled) {
                        "The filter applies your list to DNS queries sent through this VPN. It has not been verified to block every HTTPS connection to a site."
                    } else {
                        "Filters DNS queries through a local VPN during blocks. It has not been verified to block every HTTPS connection to a site. Android will ask for permission to set up the VPN."
                    },
                    actionLabel = if (siteFilterEnabled) "Manage in Settings" else "Turn on website filter",
                    onAction = onRequestSiteFilter,
                )
                PermissionCard(
                    icon = Icons.Outlined.AppBlocking,
                    title = "App blocking",
                    enabled = accessibilityEnabled,
                    status = if (accessibilityEnabled) "On" else "Off",
                    description = if (accessibilityEnabled) {
                        "Dimky can keep allowed apps available and return other apps to your plan."
                    } else {
                        "Turn on the accessibility service to restrict other apps during focus blocks or the evening restriction."
                    },
                    actionLabel = if (accessibilityEnabled) "Manage in Settings" else "Turn on in Settings",
                    onAction = onOpenAccessibilitySettings,
                )
                PermissionCard(
                    icon = Icons.Outlined.Alarm,
                    title = "On-time block start",
                    enabled = exactAlarmsAllowed,
                    status = if (exactAlarmsAllowed) "Available" else "Permission needed",
                    description = if (exactAlarmsAllowed) {
                        "Android can start blocks and check the cutoff time on schedule."
                    } else {
                        "Without this permission, Android may delay a block's start or the cutoff check."
                    },
                    actionLabel = if (exactAlarmsAllowed) "View permission" else "Allow exact alarms",
                    onAction = onRequestExactAlarmAccess,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Your plans and settings are saved on this phone and stay available offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("03", "Daily review reminder")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Choose when the phone reminds you to review today's timer sessions. Default: 9:00 PM.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            onClick = { showReminderTimePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "Reminder time (24-hour)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(reminderDraft, style = MaterialTheme.typography.bodyLarge)
                                }
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = "Choose reminder time",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (!notificationsAllowed) {
                    PermissionCard(
                        icon = Icons.Outlined.Alarm,
                        title = "Review notifications",
                        enabled = false,
                        status = "Permission needed",
                        description = "Allow notifications so the daily reminder can open today's review.",
                        actionLabel = "Allow notifications",
                        onAction = onRequestNotificationPermission,
                    )
                }
                if (showReminderTimePicker) {
                    TimeWheelPickerDialog(
                        title = "Daily review reminder",
                        time = runCatching { LocalTime.parse(reminderDraft) }.getOrDefault(LocalTime.of(21, 0)),
                        onDismiss = { showReminderTimePicker = false },
                        onConfirm = { selected ->
                            reminderDraft = String.format(java.util.Locale.ROOT, "%02d:%02d", selected.hour, selected.minute)
                            onDailyReviewReminderTimeChange(reminderDraft)
                            showReminderTimePicker = false
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("04", "Focus areas")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create up to two areas and assign each study session to one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (focusAreas.isEmpty()) {
                            Text("No focus areas yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        focusAreas.forEachIndexed { index, area ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(area.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { onFocusAreasChange(focusAreas - area) }) {
                                    Icon(Icons.Outlined.Remove, contentDescription = "Remove ${area.name}")
                                }
                            }
                        }
                        if (focusAreas.size < 2) {
                            if (focusAreas.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            OutlinedTextField(
                                value = newFocusArea,
                                onValueChange = { newFocusArea = it.take(40) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("New focus area") },
                            )
                            OutlinedButton(
                                onClick = {
                                    val name = newFocusArea.trim()
                                    if (name.isNotEmpty() && focusAreas.none { it.name.equals(name, ignoreCase = true) }) {
                                        onFocusAreasChange(focusAreas + FocusArea(java.util.UUID.randomUUID().toString(), name))
                                        newFocusArea = ""
                                    }
                                },
                                enabled = newFocusArea.isNotBlank() && focusAreas.none { it.name.equals(newFocusArea.trim(), ignoreCase = true) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Add focus area") }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("05", "Sync with Chrome")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Your plan is saved on this phone first, then shared with Chrome through Neon.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it; formError = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Neon endpoint") },
                        )
                        OutlinedTextField(
                            value = writeToken,
                            onValueChange = { writeToken = it; formError = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Private sync key") },
                            placeholder = { Text(if (syncConfiguration.hasWriteToken) "Saved on this phone" else "Paste your key") },
                        )
                        Button(onClick = {
                            when (val result = SyncEndpoint.validate(endpoint)) {
                                is EndpointValidation.Invalid -> formError = result.explanation
                                is EndpointValidation.Valid -> if (writeToken.isBlank() && !syncConfiguration.hasWriteToken) {
                                    formError = "Enter the private key to connect this phone."
                                } else {
                                    onConfigureSync(result.endpoint, writeToken.trim())
                                    endpoint = result.endpoint
                                    writeToken = ""
                                    formError = null
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save and sync") }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Sync saved changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (syncStatus) {
                                        SyncStatus.NOT_CONFIGURED -> "Set up your sync key to get started."
                                        SyncStatus.PENDING -> "Changes pending; they'll sync when connected."
                                        SyncStatus.SYNCING -> "Syncing…"
                                        SyncStatus.UP_TO_DATE -> "Plan synced with Chrome."
                                        SyncStatus.CONFLICT_RESOLVED -> "Plan synced after merging changes."
                                        SyncStatus.ERROR -> syncMessage ?: "Couldn't sync."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = onSyncNow,
                                enabled = syncConfiguration.hasWriteToken && syncStatus != SyncStatus.SYNCING,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("Sync now", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                        if (syncRevision >= 0) Text("Shared revision: $syncRevision", style = MaterialTheme.typography.bodySmall)
                        if (syncStatus == SyncStatus.ERROR && syncMessage?.startsWith("The review for ") == true) {
                            Text("Choose which review to keep for this session.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onResolveReviewConflict(true) }) { Text("Keep phone review") }
                                OutlinedButton(onClick = { onResolveReviewConflict(false) }) { Text("Use synced review") }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Blocked websites in Chrome", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("You can also add websites during a block. Selected websites stay blocked until it ends.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        desktopBlockedDomains.forEachIndexed { index, domain ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(domain, modifier = Modifier.weight(1f))
                                Button(
                                    onClick = { onDesktopDomainsChange(desktopBlockedDomains - domain) },
                                    enabled = !activeFocusBlock,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                    ),
                                ) { Text("Remove") }
                            }
                        }
                        if (desktopBlockedDomains.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedTextField(
                            value = newDomain,
                            onValueChange = { newDomain = it; formError = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Add domain") },
                            placeholder = { Text("example.com") },
                        )
                        Button(onClick = {
                            val domain = DesktopSiteBlocklist.normalize(newDomain)
                            when (DesktopSiteBlocklist.validate(desktopBlockedDomains + domain)) {
                                BlocklistValidation.Valid -> { onDesktopDomainsChange(desktopBlockedDomains + domain); newDomain = "" }
                                BlocklistValidation.ActiveBlockLocked -> formError = "Selected websites can't be removed during a block."
                                BlocklistValidation.DuplicateDomain -> formError = "That domain is already selected."
                                BlocklistValidation.TooManyDomains -> formError = "The list supports up to 100 domains."
                                is BlocklistValidation.InvalidDomain -> formError = "Enter a domain only, such as example.com."
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add website") }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsStepper(
    title: String,
    description: String,
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, enabled = decreaseEnabled) {
                    Icon(Icons.Outlined.Remove, contentDescription = decreaseLabel)
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                IconButton(onClick = onIncrease, enabled = increaseEnabled) {
                    Icon(Icons.Outlined.Add, contentDescription = increaseLabel)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    status: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = colors.primary)
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (enabled) {
                    Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = colors.primary)
                }
            }
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) colors.primary else colors.onSurfaceVariant,
            )
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun LocalTime.label(): String = when (hour) {
    21 -> "9 PM"
    22 -> "10 PM"
    else -> "11 PM"
}

private fun Int.hoursLabel(): String = if (this % 60 == 0) "${this / 60} h" else "${this / 60}.${(this % 60) / 30 * 5} h"
