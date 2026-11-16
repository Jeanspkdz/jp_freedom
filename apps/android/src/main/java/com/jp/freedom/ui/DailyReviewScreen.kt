package com.jp.freedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.DailyReview
import com.jp.freedom.domain.SessionOutcome
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewScreen(review: DailyReview, onBack: () -> Unit) {
    val plannedMinutes = review.plannedBlocks.sumOf {
        Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0)
    }
    val scale = maxOf(review.targetMinutes.toLong(), plannedMinutes, review.actualMinutes, 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Avance del día · ${review.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE")))}")
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tiempo de estudio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${review.actualMinutes / 60} h ${review.actualMinutes % 60} min",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "de ${review.targetMinutes / 60} h ${review.targetMinutes % 60} min",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressBarRow("Estudiado", review.actualMinutes, scale, MaterialTheme.colorScheme.primary)
                    ProgressBarRow("Planificado", plannedMinutes, scale, MaterialTheme.colorScheme.secondary)
                    Text(
                        "Cada barra representa horas del mismo día; la escala llega a ${scale / 60} h ${scale % 60} min.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { ReviewSectionHeading("Tiempo por área") }
            if (review.focusAreaMinutes.isEmpty()) {
                item { ReviewEmptyState("Asigna un área al iniciar una sesión para ver aquí el desglose.") }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            review.focusAreaMinutes.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(entry.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Text("${entry.minutes} min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                if (entry != review.focusAreaMinutes.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            item { ReviewSectionHeading("Sesiones registradas") }
            if (review.sessions.isEmpty()) {
                item { ReviewEmptyState("Aún no hay sesiones registradas.") }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            review.sessions.forEachIndexed { index, session ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            session.taskName.ifBlank { "Sesión sin nombre" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${session.outcome?.label() ?: "Revisión pendiente"} · ${session.actualMillis / 60_000} min",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (session.note.isNotBlank()) {
                                            Text("Nota: ${session.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    ReviewSessionOutcomeChip(session.outcome)
                                }
                                if (index != review.sessions.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            item { ReviewSectionHeading("Bloques planificados") }
            if (review.plannedBlocks.isEmpty()) {
                item { ReviewEmptyState("No hay bloques planificados.") }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            review.plannedBlocks.forEachIndexed { index, block ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${block.startTime}–${block.endTime} · ${block.name}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ReviewStatusChip(block.completionStatus)
                                }
                                if (index != review.plannedBlocks.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Volver al plan")
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun ReviewEmptyState(message: String) {
    Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewStatusChip(status: CompletionStatus?) {
    val colors = MaterialTheme.colorScheme
    val background = when (status) {
        CompletionStatus.FINISHED -> colors.primary
        CompletionStatus.PARTLY_DONE -> colors.primaryContainer
        CompletionStatus.NOT_DONE -> colors.surfaceVariant
        else -> colors.surfaceVariant
    }
    val foreground = when (status) {
        CompletionStatus.FINISHED -> colors.onPrimary
        CompletionStatus.PARTLY_DONE -> colors.onPrimaryContainer
        CompletionStatus.NOT_DONE -> colors.onSurfaceVariant
        else -> colors.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(
            status?.label() ?: "Pendiente",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ReviewSessionOutcomeChip(outcome: SessionOutcome?) {
    val colors = MaterialTheme.colorScheme
    val isFinished = outcome == SessionOutcome.FINISHED
    Surface(shape = RoundedCornerShape(50), color = if (isFinished) colors.primary else colors.surfaceVariant) {
        Text(
            outcome?.label() ?: "Revisión pendiente",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isFinished) colors.onPrimary else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ProgressBarRow(label: String, minutes: Long, scale: Long, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${minutes / 60} h ${minutes % 60} min", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (minutes.toFloat() / scale).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
