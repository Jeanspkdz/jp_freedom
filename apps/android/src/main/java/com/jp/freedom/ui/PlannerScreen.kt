package com.jp.freedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
import com.jp.freedom.domain.StudyTimerSession
import com.jp.freedom.domain.StudySessionState
import com.jp.freedom.data.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    date: LocalDate,
    isTodaySelected: Boolean,
    blocks: List<PlannedBlock>,
    allBlocks: List<PlannedBlock>,
    studySessions: List<StudyTimerSession>,
    activeStudySession: StudyTimerSession?,
    pendingSessionReview: StudyTimerSession?,
    settings: FocusSettings,
    restrictionActive: Boolean,
    hasSyncKey: Boolean,
    syncStatus: SyncStatus,
    accessibilityEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    onSelectToday: () -> Unit,
    onSelectTomorrow: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudyTimer: () -> Unit,
    onOpenDailyReview: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenTomorrowPlan: () -> Unit,
    onAddBlock: () -> Unit,
    onEditBlock: (PlannedBlock) -> Unit,
) {
    val now = LocalDateTime.now()
    val activeBlock = blocks.firstOrNull {
        it.date == now.toLocalDate() &&
            it.completionStatus != CompletionStatus.FINISHED &&
            !now.toLocalTime().isBefore(it.startTime) &&
            now.toLocalTime().isBefore(it.endTime)
    }
    val nextBlock = blocks.firstOrNull {
        it.date == now.toLocalDate() &&
            it.completionStatus == CompletionStatus.NOT_STARTED &&
            it.startTime.isAfter(now.toLocalTime())
    }
    val requiredPlanDate = com.jp.freedom.domain.DayPlanPolicy
        .requiredPlanDateForEvening(LocalDate.now())
    val targetDate = requiredPlanDate ?: LocalDate.now().plusDays(1)
    val targetMinutes = settings.studyHoursTargetMinutes
    val actualMinutes = com.jp.freedom.domain.StudyTimerPolicy.dailyReview(
        date, java.time.ZoneId.systemDefault(), targetMinutes, studySessions, blocks,
    ).actualMinutes.toInt()
    val targetCount = allBlocks.count { it.date == targetDate }
    val isRestDayTomorrow = requiredPlanDate == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Dimky",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            PlannerBottomBar(
                onOpenStudyTimer = onOpenStudyTimer,
                activeStudySession = activeStudySession,
                pendingSessionReview = pendingSessionReview,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = dateLabel(date, isTodaySelected),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }

            DaySelector(
                isTodaySelected = isTodaySelected,
                onSelectToday = onSelectToday,
                onSelectTomorrow = onSelectTomorrow,
            )

            StudyHoursLedger(actualMinutes, targetMinutes, blocks)

            TextButton(onClick = onOpenDailyReview, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) { Text("Revisar día") }

            if (!accessibilityEnabled || !exactAlarmsAllowed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            when {
                                !accessibilityEnabled && !exactAlarmsAllowed -> "Activa el bloqueo y las alarmas para aplicar tus sesiones a tiempo."
                                !accessibilityEnabled -> "Activa accesibilidad para bloquear distracciones."
                                else -> "Activa las alarmas exactas para iniciar los bloques a tiempo."
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                        )
                        TextButton(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondary),
                        ) { Text("Configurar") }
                    }
                }
            }

            if (restrictionActive) {
                RestrictionBanner(
                    remainingMinutes = (targetMinutes - com.jp.freedom.domain.DayPlanPolicy.plannedStudyMinutes(targetDate, allBlocks)).coerceAtLeast(0),
                    onOpenPlan = onOpenTomorrowPlan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (blocks.isEmpty()) {
                EmptyAgenda(
                    date = date,
                    isTodaySelected = isTodaySelected,
                    onAddBlock = onAddBlock,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isTodaySelected) "Plan de hoy" else "Plan de mañana", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onAddBlock) { Icon(Icons.Outlined.Add, contentDescription = "Añadir actividad") }
                    }
                    blocks.forEachIndexed { index, block ->
                        val isCurrent = block.id == activeBlock?.id
                        val isNext = block.id == nextBlock?.id
                        EditorialBlock(
                            block = block,
                            isCurrent = isCurrent,
                            isNext = isNext,
                            now = now.toLocalTime(),
                            onClick = { onEditBlock(block) },
                        )
                        if (index != blocks.lastIndex) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySelector(
    isTodaySelected: Boolean,
    onSelectToday: () -> Unit,
    onSelectTomorrow: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        SegmentedButton(
            selected = isTodaySelected,
            onClick = onSelectToday,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Hoy", style = MaterialTheme.typography.titleMedium)
        }
        SegmentedButton(
            selected = !isTodaySelected,
            onClick = onSelectTomorrow,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Mañana", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StudySummary(blocks: List<PlannedBlock>) {
    val colors = MaterialTheme.colorScheme
    val minutes = blocks.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) }
    val total = if (minutes % 60L == 0L) "${minutes / 60} hr" else "${minutes / 60} hr ${minutes % 60} min"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.School, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                Text("$total of study", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                blocks.take(3).forEach { block ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                        Spacer(Modifier.height(6.dp))
                        Text("${block.startTime.clockText()}–${block.endTime.clockText()}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
            if (blocks.size > 3) Text("+${blocks.size - 3} more blocks", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudyHoursLedger(actualMinutes: Int, targetMinutes: Int, blocks: List<PlannedBlock>) {
    val planned = blocks.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) }.toInt()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${actualMinutes / 60}:${(actualMinutes % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Text("ESTUDIADO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${targetMinutes / 60}:${(targetMinutes % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, color = colors.onSurface)
                Text("META DIARIA", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Estudiado", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { (actualMinutes.toFloat() / maxOf(targetMinutes, planned, actualMinutes, 1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = colors.primary, trackColor = colors.surfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text("Planificado · ${planned / 60} h ${planned % 60} min", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { (planned.toFloat() / maxOf(targetMinutes, planned, actualMinutes, 1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = colors.secondary, trackColor = colors.surfaceVariant)
        Text("Meta: ${targetMinutes / 60} h ${targetMinutes % 60} min", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun EditorialBlock(
    block: PlannedBlock,
    isCurrent: Boolean,
    isNext: Boolean,
    now: LocalTime,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val duration = Duration.between(block.startTime, block.endTime).toMillis().coerceAtLeast(1)
    val elapsed = Duration.between(block.startTime, now).toMillis()
    val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp).padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) colors.primaryContainer else Color.Transparent,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = "${block.startTime.clockText()}–${block.endTime.clockText()}",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    isCurrent -> StateLabel("In progress", emphasized = true)
                    isNext -> StateLabel("Next", emphasized = false)
                    block.completionStatus == CompletionStatus.NOT_STARTED -> StateLabel("Upcoming", emphasized = false)
                    block.completionStatus == CompletionStatus.FINISHED -> StateLabel("Finalizado", emphasized = true, amber = true)
                    block.completionStatus == CompletionStatus.PARTLY_DONE -> StateLabel("Parcial", emphasized = false)
                    block.completionStatus == CompletionStatus.NOT_DONE -> StateLabel("No realizado", emphasized = false)
                }
            }
            if (isCurrent) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = colors.primary,
                    trackColor = colors.surface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun StateLabel(text: String, emphasized: Boolean, amber: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = when {
            amber -> colors.primary
            emphasized -> colors.primary
            else -> colors.surfaceVariant
        },
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                amber -> colors.onPrimary
                emphasized -> colors.onPrimary
                else -> colors.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyAgenda(
    date: LocalDate,
    isTodaySelected: Boolean,
    onAddBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.EditCalendar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isTodaySelected) "Hoy no hay bloques" else "Mañana no hay bloques",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Añade una actividad para organizar tu tiempo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onAddBlock, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Añadir bloque")
        }
    }
}

@Composable
private fun RestrictionBanner(
    remainingMinutes: Int,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.onSecondaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Plan de mañana incompleto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSecondaryContainer,
                )
                Text(
                    "Faltan ${remainingMinutes / 60} h ${remainingMinutes % 60} min para tu meta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer,
                )
            }
            TextButton(
                onClick = onOpenPlan,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onSecondaryContainer),
            ) { Text("Abrir") }
        }
    }
}

@Composable
private fun PlannerBottomBar(
    onOpenStudyTimer: () -> Unit,
    activeStudySession: StudyTimerSession?,
    pendingSessionReview: StudyTimerSession?,
) {
    Button(
        onClick = onOpenStudyTimer,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Outlined.School, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                pendingSessionReview != null -> "Revisar sesión"
                activeStudySession != null -> "Ver sesión en curso"
                else -> "Iniciar sesión"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun dateLabel(date: LocalDate, isTodaySelected: Boolean): String {
    val prefix = if (isTodaySelected) "Hoy" else "Mañana"
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE")))
    return "$prefix · $formatted"
}

private fun LocalTime.clockText(): String = format(DateTimeFormatter.ofPattern("HH:mm"))

