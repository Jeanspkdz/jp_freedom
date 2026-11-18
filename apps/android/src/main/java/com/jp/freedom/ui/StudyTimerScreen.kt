package com.jp.freedom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.AllowedAppsList
import com.jp.freedom.domain.FocusArea
import com.jp.freedom.domain.SessionOutcome
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.domain.StudyTimerPolicy
import com.jp.freedom.domain.StudyTimerSession
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTimerScreen(
    session: StudyTimerSession?, pendingReview: StudyTimerSession?,
    focusAreas: List<FocusArea>,
    notificationPermissionDenied: Boolean,
    selectedAllowedAppsList: AllowedAppsList,
    onBack: () -> Unit, onStart: (Duration, AllowedAppsList, String?) -> Unit,
    onOpenAllowedAppsLists: () -> Unit, onStop: () -> Unit,
    onResumeSession: () -> Unit,
    onOpenFocusAreaSettings: () -> Unit,
    onReview: (String, SessionOutcome, String) -> Unit,
    onEndBreak: (String) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(60) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showStartConfirmation by remember { mutableStateOf(false) }
    var focusAreaId by remember(pendingReview?.id) { mutableStateOf<String?>(pendingReview?.focusAreaId) }
    var task by remember(pendingReview?.id) { mutableStateOf(pendingReview?.taskName.orEmpty()) }
    var note by remember(pendingReview?.id) { mutableStateOf(pendingReview?.note.orEmpty()) }
    var status by remember(pendingReview?.id) { mutableStateOf<SessionOutcome?>(null) }
    var nextTask by remember(session?.id) { mutableStateOf("") }
    var clockTick by remember { mutableIntStateOf(0) }
    val selectedMinutes = minutes
    val validDuration = selectedMinutes in 1..720

    LaunchedEffect(session?.id, session?.state, pendingReview?.id) {
        while (session != null || pendingReview != null) {
            delay(1_000)
            clockTick++
        }
    }

    val title = when {
        pendingReview != null -> "Session review"
        session?.state == StudySessionState.BREAKING -> "Break"
        session != null -> "Study session"
        else -> "Timer"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                pendingReview != null -> {
                    val now = remember(clockTick) { Instant.now() }
                    RitmyMascot(modifier = Modifier.align(Alignment.CenterHorizontally), size = 100.dp, cheerful = true)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Session complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("You studied ${pendingReview.actualMillis / 60_000} min. Save how it went to close the session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(task, { task = it }, label = { Text("Activity") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("How did it go?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    listOf(SessionOutcome.FINISHED, SessionOutcome.NOT_FINISHED).forEach { option ->
                        val selected = status == option
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { status = option },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (selected) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (option == SessionOutcome.FINISHED) "Finished" else "Not finished", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = { status?.let { onReview(task.trim(), it, note) } }, enabled = task.isNotBlank() && status != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Save review") }
                    if (!StudyTimerPolicy.remaining(pendingReview, now).isZero) {
                        OutlinedButton(onClick = onResumeSession, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Continue session")
                        }
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Later") }
                }

                session?.state == StudySessionState.BREAKING -> {
                    val now = remember(clockTick) { Instant.now() }
                    val remainingTime = StudyTimerPolicy.breakRemaining(session, now)
                    StatusHero(icon = { Icon(Icons.Filled.PauseCircle, contentDescription = null) }, heading = "Take a break", detail = "Restrictions are paused during your break.")
                    TimerReadout("${remainingTime.toMinutes().toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}", "Break remaining")
                    OutlinedTextField(nextTask, { nextTask = it }, label = { Text("What will you work on next?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onEndBreak(nextTask.trim()) }, enabled = nextTask.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("End break") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to plan") }
                }

                session != null -> {
                    val now = remember(clockTick) { Instant.now() }
                    val remainingTime = StudyTimerPolicy.remaining(session, now)
                    val elapsedTime = Duration.ofMillis((session.durationMillis - remainingTime.toMillis()).coerceAtLeast(0))
                    StatusHero(icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }, heading = session.taskName.ifBlank { "Session in progress" }, detail = "Keep going: your session keeps counting even when you leave the app.")
                    TimerReadout(
                        "${remainingTime.toHours().toString().padStart(2, '0')}:${(remainingTime.toMinutes() % 60).toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}",
                        "Time remaining",
                    )
                    LinearProgressIndicator(
                        progress = { (elapsedTime.toMillis().toFloat() / session.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Elapsed ${formatClock(elapsedTime)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Total ${formatClock(Duration.ofMillis(session.durationMillis))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Protected access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
                                    Text(
                                        session.allowedAppsListName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("End and review") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to plan") }
                }

                else -> {
                    RitmyMascot(modifier = Modifier.align(Alignment.CenterHorizontally), size = 116.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("A block for you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Choose how much time you want to spend and start when you're ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Choose a preset or set a custom duration.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    formatDuration(selectedMinutes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            StudyTimerPolicy.PRESETS.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { preset ->
                                        val value = preset.toMinutes().toInt()
                                        val selected = minutes == value
                                        FilterChip(
                                            selected = selected,
                                            onClick = { minutes = value },
                                            label = { Text(formatDuration(value), maxLines = 1, softWrap = false) },
                                            leadingIcon = if (selected) ({ Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
                                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            OutlinedButton(onClick = { showDurationPicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Choose another duration")
                            }
                            if (!validDuration) {
                                Text("Choose a duration between 1 min and 12 hr.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Text("Allowed apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selectedAllowedAppsList.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (selectedAllowedAppsList.id == AllowedAppsList.ESSENTIALS_ID) {
                                    "Only essential apps; all other apps are blocked."
                                } else {
                                    "${selectedAllowedAppsList.packageNames.size} apps allowed; all other apps are blocked."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onOpenAllowedAppsLists, modifier = Modifier.fillMaxWidth()) {
                                Text("Choose list")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Focus area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Manage", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onOpenFocusAreaSettings).padding(8.dp))
                    }
                    if (focusAreas.isEmpty()) {
                        Text("Add up to two areas in Settings to track what you focus on.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            focusAreas.forEach { area ->
                                FilterChip(
                                    selected = focusAreaId == area.id,
                                    onClick = { focusAreaId = if (focusAreaId == area.id) null else area.id },
                                    label = { Text(area.name) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (notificationPermissionDenied) {
                        Text("Turn on notifications to keep track of your session outside the app.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { showStartConfirmation = true }, enabled = validDuration, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Start session")
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to plan") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showDurationPicker) {
        TimeWheelPickerDialog(
            title = "Custom duration",
            time = LocalTime.of(selectedMinutes / 60, selectedMinutes % 60),
            maxHour = 12,
            onDismiss = { showDurationPicker = false },
            onConfirm = { selected ->
                minutes = selected.hour * 60 + selected.minute
                showDurationPicker = false
            },
        )
    }
    if (showStartConfirmation) {
        AlertDialog(
            onDismissRequest = { showStartConfirmation = false },
            title = { Text("Confirm allowed apps") },
            text = {
                Text("${selectedAllowedAppsList.name} will stay available during the session. All other apps will be blocked, and this list can't be changed until the session ends.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirmation = false
                    onStart(Duration.ofMinutes(selectedMinutes.toLong()), selectedAllowedAppsList, focusAreaId)
                }) { Text("Start session") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirmation = false }) { Text("Back") }
            },
        )
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatClock(duration: Duration): String =
    "${duration.toHours().toString().padStart(2, '0')}:${(duration.toMinutes() % 60).toString().padStart(2, '0')}"

@Composable
private fun StatusHero(icon: @Composable () -> Unit, heading: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimerReadout(value: String, caption: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
