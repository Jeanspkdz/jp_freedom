package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.SavedAccessProfile
import com.jp.freedom.data.LaunchableApp
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.StudyTimerPolicy
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTimerScreen(
    session: StudyTimerSession?, pendingReview: StudyTimerSession?,
    profiles: List<SavedAccessProfile>, installedApps: List<LaunchableApp>,
    onCreateProfile: (String, Set<String>) -> Unit,
    onBack: () -> Unit, onStart: (Duration, SavedAccessProfile?) -> Unit, onStop: () -> Unit,
    onReview: (String, CompletionStatus, String, String) -> Unit,
    onEndBreak: (String) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(60) }
    var customMinutes by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var task by remember(pendingReview?.id) { mutableStateOf(pendingReview?.taskName.orEmpty()) }
    var note by remember(pendingReview?.id) { mutableStateOf(pendingReview?.note.orEmpty()) }
    var remaining by remember(pendingReview?.id) { mutableStateOf(pendingReview?.remaining.orEmpty()) }
    var status by remember(pendingReview?.id) { mutableStateOf<CompletionStatus?>(null) }
    var nextTask by remember(session?.id) { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (pendingReview != null) "Review session" else "Study timer") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (pendingReview != null) {
                Text("You studied ${pendingReview.actualMillis / 60_000} minutes", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(task, { task = it }, label = { Text("Task name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompletionStatus.values().filter { it != CompletionStatus.NOT_STARTED }.forEach { option ->
                        OutlinedButton(onClick = { status = option }) { Text(if (status == option) "✓ ${option.label()}" else option.label()) }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(remaining, { remaining = it }, label = { Text("Remaining work (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { status?.let { onReview(task.trim(), it, note, remaining) } }, enabled = task.isNotBlank() && status != null, modifier = Modifier.fillMaxWidth()) { Text("Save review") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Later") }
            } else if (session?.state == StudySessionState.BREAKING) {
                val remainingTime = StudyTimerPolicy.breakRemaining(session, Instant.now())
                Text("Break", style = MaterialTheme.typography.headlineSmall)
                Text("${remainingTime.toMinutes().toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
                Text("Phone restrictions are paused during your break.")
                OutlinedTextField(nextTask, { nextTask = it }, label = { Text("What will you work on next?") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onEndBreak(nextTask.trim()) }, enabled = nextTask.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("End break and continue") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to planner") }
            } else if (session != null) {
                val remainingTime = StudyTimerPolicy.remaining(session, Instant.now())
                Text("${remainingTime.toMinutes().toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
                Text("${session.durationMillis / 60_000} minute session · ${session.profileName}")
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop and review") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to planner") }
            } else {
                Text("Choose a session length", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyTimerPolicy.PRESETS.forEach { preset ->
                        val value = preset.toMinutes().toInt()
                        FilterChip(
                            selected = minutes == value && customMinutes.isEmpty(),
                            onClick = { minutes = value; customMinutes = "" },
                            label = { Text(when (value) { 30 -> "30m"; 90 -> "1h 30m"; else -> "${value / 60}h" }) },
                        )
                    }
                }
                OutlinedTextField(customMinutes, { input ->
                    customMinutes = input.filter(Char::isDigit)
                }, label = { Text("Custom minutes") }, modifier = Modifier.fillMaxWidth())
                Text("Choose phone access", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { selectedProfileId = null }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (selectedProfileId == null) "✓ " else "") + "Essentials only")
                }
                profiles.forEach { option ->
                    OutlinedButton(onClick = { selectedProfileId = option.id }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selectedProfileId == option.id) "✓ " else "") + option.name)
                    }
                }
                OutlinedButton(onClick = { creatingProfile = !creatingProfile }, modifier = Modifier.fillMaxWidth()) { Text("Create access profile") }
                if (creatingProfile) {
                    OutlinedTextField(profileName, { profileName = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth())
                    installedApps.forEach { app ->
                        FilterChip(
                            selected = app.packageName in selectedPackages,
                            onClick = { selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName else selectedPackages + app.packageName },
                            label = { Text(app.label) },
                        )
                    }
                    Button(onClick = {
                        onCreateProfile(profileName.trim(), selectedPackages)
                        profileName = ""
                        selectedPackages = emptySet()
                        creatingProfile = false
                    }, enabled = profileName.isNotBlank()) { Text("Save profile") }
                }
                Text("You cannot change your access profile during this session.")
                if (profiles.firstOrNull { it.id == selectedProfileId }?.packages?.contains("com.android.chrome") == true) {
                    Text("This profile allows Chrome and its websites during your session.")
                }
                Button(onClick = { onStart(Duration.ofMinutes((customMinutes.toIntOrNull() ?: minutes).coerceIn(1, 720).toLong()), profiles.firstOrNull { it.id == selectedProfileId }) }, modifier = Modifier.fillMaxWidth()) { Text("Start timer") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
