package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onBack: () -> Unit, onStart: (Duration, AccessProfile) -> Unit, onStop: () -> Unit,
    onReview: (String, CompletionStatus, String, String) -> Unit,
    onEndBreak: (String) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(60) }
    var customMinutes by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(AccessProfile.ESSENTIALS_ONLY) }
    var task by remember(pendingReview?.id) { mutableStateOf(pendingReview?.taskName.orEmpty()) }
    var note by remember(pendingReview?.id) { mutableStateOf(pendingReview?.note.orEmpty()) }
    var remaining by remember(pendingReview?.id) { mutableStateOf(pendingReview?.remaining.orEmpty()) }
    var status by remember(pendingReview?.id) { mutableStateOf<CompletionStatus?>(null) }
    var nextTask by remember(session?.id) { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(if (pendingReview != null) "Review session" else "Study timer") }) }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                Text("${session.durationMillis / 60_000} minute session · ${session.accessProfile.title}")
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop and review") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to planner") }
            } else {
                Text("Choose a session length", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyTimerPolicy.PRESETS.forEach { preset ->
                        val value = preset.toMinutes().toInt()
                        OutlinedButton(onClick = { minutes = value; customMinutes = "" }) {
                            Text(when (value) { 30 -> "30m"; 90 -> "1h 30m"; else -> "${value / 60}h" })
                        }
                    }
                }
                OutlinedTextField(customMinutes, { input -> customMinutes = input.filter(Char::isDigit) }, label = { Text("Custom minutes") }, modifier = Modifier.fillMaxWidth())
                Text("Choose phone access", style = MaterialTheme.typography.titleLarge)
                AccessProfile.values().forEach { option ->
                    OutlinedButton(onClick = { profile = option }, modifier = Modifier.fillMaxWidth()) { Text((if (profile == option) "✓ " else "") + option.title) }
                }
                Button(onClick = { onStart(Duration.ofMinutes((customMinutes.toIntOrNull() ?: minutes).coerceIn(1, 720).toLong()), profile) }, modifier = Modifier.fillMaxWidth()) { Text("Start timer") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
