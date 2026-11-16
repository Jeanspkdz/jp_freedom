package com.jp.freedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
    studyHistoryCount: Int,
    onDailyReviewReminderTimeChange: (String) -> Unit,
    onClearStudyHistory: () -> Unit,
    onBack: () -> Unit,
    onSettingsChange: (FocusSettings) -> Unit,
    onDesktopDomainsChange: (List<String>) -> Unit,
    onConfigureSync: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestSiteFilter: () -> Unit,
) {
    var endpoint by rememberSaveable(syncConfiguration.endpoint) { mutableStateOf(syncConfiguration.endpoint) }
    var writeToken by remember { mutableStateOf("") }
    var newDomain by rememberSaveable { mutableStateOf("") }
    var reminderDraft by rememberSaveable(dailyReviewReminderTime) { mutableStateOf(dailyReviewReminderTime) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmHistoryClear by remember { mutableStateOf(false) }
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
        if (confirmHistoryClear) {
            AlertDialog(
                onDismissRequest = { confirmHistoryClear = false },
                title = { Text("Clear study history?") },
                text = { Text("This removes synced study sessions and reviews from this phone and the shared record. Plans and blocked websites stay unchanged.") },
                confirmButton = {
                    TextButton(onClick = { confirmHistoryClear = false; onClearStudyHistory() }) { Text("Clear history") }
                },
                dismissButton = { TextButton(onClick = { confirmHistoryClear = false }) { Text("Cancel") } },
            )
        }
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
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Daily study-hours target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(settings.studyHoursTargetMinutes.hoursLabel(), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSettingsChange(settings.copy(studyHoursTargetMinutes = settings.studyHoursTargetMinutes - 30)) }, enabled = settings.studyHoursTargetMinutes > 30) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Decrease study-hours target")
                            }
                            IconButton(onClick = { onSettingsChange(settings.copy(studyHoursTargetMinutes = settings.studyHoursTargetMinutes + 30)) }, enabled = settings.studyHoursTargetMinutes < 720) {
                                Icon(Icons.Outlined.Add, contentDescription = "Increase study-hours target")
                            }
                        }
                        Text("Planned study time for the next required day must reach this target to lift the evening restriction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Break after each session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${settings.breakMinutes} min", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSettingsChange(settings.copy(breakMinutes = settings.breakMinutes - 1)) }, enabled = settings.breakMinutes > 1) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Shorten break")
                            }
                            IconButton(onClick = { onSettingsChange(settings.copy(breakMinutes = settings.breakMinutes + 1)) }, enabled = settings.breakMinutes < 60) {
                                Icon(Icons.Outlined.Add, contentDescription = "Lengthen break")
                            }
                        }
                        Text("Phone restrictions pause after you save the required session review.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Required blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${settings.requiredBlockCount}", style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount - 1)) }, enabled = settings.requiredBlockCount > 1) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Decrease minimum")
                            }
                            IconButton(onClick = { onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount + 1)) }, enabled = settings.requiredBlockCount < Int.MAX_VALUE) {
                                Icon(Icons.Outlined.Add, contentDescription = "Increase minimum")
                            }
                        }
                        Text("Minimum number of blocks in the next required day plan before Chrome sends planning reminders.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("02", "Study history")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("$studyHistoryCount reviewed sessions on this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Study history stays available in reviews until you clear it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { confirmHistoryClear = true }, enabled = studyHistoryCount > 0, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear study history")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("03", "Phone protection")
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
                        "JP Freedom can keep allowed apps available and return other apps to your plan."
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
                SectionHeading("04", "Daily review reminder")
                Text("Choose when the phone reminds you to review today's timer sessions. Default: 9:00 PM.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = reminderDraft,
                    onValueChange = { input ->
                        reminderDraft = input.filter { it.isDigit() || it == ':' }.take(5)
                        if (reminderDraft.matches(Regex("\\d{2}:\\d{2}")) && runCatching { LocalTime.parse(reminderDraft) }.isSuccess) onDailyReviewReminderTimeChange(reminderDraft)
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Reminder time (24-hour)") },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("05", "Sync with Chrome")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sync saved changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (syncConfiguration.hasWriteToken) {
                                "Sync the latest changes now. This uses the private key already saved on this phone; you do not need to enter it again."
                            } else {
                                "Save your private key once above. After that, you can sync changes here without entering it again."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when (syncStatus) {
                                SyncStatus.NOT_CONFIGURED -> "Set up your sync key to get started."
                                SyncStatus.PENDING -> "Changes pending; they'll sync when connected."
                                SyncStatus.SYNCING -> "Syncing…"
                                SyncStatus.UP_TO_DATE -> "Plan synced with Chrome."
                                SyncStatus.CONFLICT_RESOLVED -> "Plan synced after merging changes."
                                SyncStatus.ERROR -> syncMessage ?: "Couldn't sync."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (syncStatus == SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onSyncNow, enabled = syncConfiguration.hasWriteToken && syncStatus != SyncStatus.SYNCING, modifier = Modifier.fillMaxWidth()) {
                            Text("Sync changes now")
                        }
                        if (syncRevision >= 0) Text("Shared revision: $syncRevision", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("Blocked websites in Chrome", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("You can also add websites during a block. Selected websites stay blocked until it ends.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                desktopBlockedDomains.forEach { domain ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(domain, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onDesktopDomainsChange(desktopBlockedDomains - domain) }, enabled = !activeFocusBlock) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it; formError = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Add domain") },
                    placeholder = { Text("example.com") },
                )
                OutlinedButton(onClick = {
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

@Composable
private fun SectionHeading(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
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
