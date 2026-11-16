package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.DailyReview
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewScreen(review: DailyReview, onBack: () -> Unit, onResolveImportConflict: (String, Boolean) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Study review · ${review.date.format(DateTimeFormatter.ofPattern("MMM d"))}") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${review.actualMinutes / 60}h ${review.actualMinutes % 60}m of ${review.targetMinutes / 60}h ${review.targetMinutes % 60}m target", style = MaterialTheme.typography.titleLarge)
                Text("Actual study time", style = MaterialTheme.typography.bodyMedium)
            }
            item { Text("Timer sessions", style = MaterialTheme.typography.titleMedium) }
            if (review.sessions.isEmpty()) item { Text("No timer sessions recorded.") }
            items(review.sessions, key = { it.id }) { session ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session.taskName.ifBlank { "Untitled task" }, style = MaterialTheme.typography.titleSmall)
                    Text("${session.completionStatus?.label() ?: "Review pending"} · ${session.actualMillis / 60_000} min")
                    if (session.note.isNotBlank()) Text("Note: ${session.note}")
                    if (session.remaining.isNotBlank()) Text("Remaining: ${session.remaining}")
                    if (session.id.endsWith("-phone-copy")) {
                        Text("This imported phone version conflicts with the server session.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onResolveImportConflict(session.id, true) }) { Text("Keep phone version") }
                            OutlinedButton(onClick = { onResolveImportConflict(session.id, false) }) { Text("Keep server version") }
                        }
                    }
                }
            }
            item { Text("Planned blocks", style = MaterialTheme.typography.titleMedium) }
            if (review.plannedBlocks.isEmpty()) item { Text("No planned blocks.") }
            items(review.plannedBlocks, key = { it.id }) { block ->
                Text("${block.startTime}–${block.endTime} · ${block.name} · ${block.completionStatus.label()}")
            }
            item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to planner") } }
        }
    }
}
