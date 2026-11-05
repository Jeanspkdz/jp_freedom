package com.jp.freedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.PlannedBlock
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
    settings: FocusSettings,
    restrictionActive: Boolean,
    onSelectToday: () -> Unit,
    onSelectTomorrow: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val targetCount = allBlocks.count { it.date == targetDate }
    val isRestDayTomorrow = requiredPlanDate == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isTodaySelected) "Plan de hoy" else "Plan de mañana",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configuración")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            PlannerBottomBar(
                showNextDaySummary = isTodaySelected,
                targetCount = targetCount,
                requiredCount = settings.requiredBlockCount,
                isRestDayTomorrow = isRestDayTomorrow,
                onOpenTomorrowPlan = onOpenTomorrowPlan,
                onAddBlock = onAddBlock,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
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

            if (blocks.isNotEmpty()) {
                FocusHoursSummary(
                    blocks = blocks,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            if (restrictionActive) {
                RestrictionBanner(
                    remainingBlocks = (settings.requiredBlockCount - targetCount).coerceAtLeast(0),
                    onOpenPlan = onOpenTomorrowPlan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (blocks.isEmpty()) {
                EmptyAgenda(
                    date = date,
                    isTodaySelected = isTodaySelected,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                        val isCurrent = block.id == activeBlock?.id
                        val isNext = block.id == nextBlock?.id
                        TimelineBlock(
                            block = block,
                            isCurrent = isCurrent,
                            isNext = isNext,
                            isFirst = index == 0,
                            isLast = index == blocks.lastIndex,
                            onClick = { onEditBlock(block) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusHoursSummary(
    blocks: List<PlannedBlock>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val durations = blocks.map { block ->
        Duration.between(block.startTime, block.endTime).toMinutes().coerceAtLeast(0)
    }
    val totalMinutes = durations.sum()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationLabel = when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timelapse,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        "$durationLabel de enfoque",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        if (blocks.size == 1) "1 sesión planificada" else "${blocks.size} sesiones planificadas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                durations.forEachIndexed { index, duration ->
                    Box(
                        modifier = Modifier
                            .weight(duration.coerceAtLeast(1).toFloat())
                            .height(9.dp)
                            .background(
                                color = androidx.compose.ui.graphics.Color(0xFFA65608),
                                shape = RoundedCornerShape(50),
                            ),
                    )
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
private fun TimelineBlock(
    block: PlannedBlock,
    isCurrent: Boolean,
    isNext: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(156.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(
            modifier = Modifier
                .width(20.dp)
                .height(156.dp),
        ) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, 20.dp.toPx())
            val timelineColor = androidx.compose.ui.graphics.Color(0xFFA65608)
            drawLine(
                color = timelineColor,
                start = androidx.compose.ui.geometry.Offset(center.x, if (isFirst) center.y else 0f),
                end = androidx.compose.ui.geometry.Offset(center.x, if (isLast) center.y else size.height),
                strokeWidth = if (isCurrent) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = if (isCurrent) timelineColor else colors.background,
                radius = 7.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = timelineColor,
                radius = 7.dp.toPx(),
                center = center,
                style = Stroke(width = if (isCurrent) 0.dp.toPx() else 1.5.dp.toPx()),
            )
        }
        BlockSurface(
            block = block,
            isCurrent = isCurrent,
            isNext = isNext,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun BlockSurface(
    block: PlannedBlock,
    isCurrent: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 144.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) colors.primaryContainer.copy(alpha = 0.64f) else colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrent) colors.primary.copy(alpha = 0.36f) else colors.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${block.startTime.clockText()}–${block.endTime.clockText()}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent || isNext) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                when {
                    isCurrent -> StateLabel("En curso", emphasized = true)
                    isNext -> StateLabel("Siguiente", emphasized = false)
                    block.completionStatus == CompletionStatus.NOT_STARTED -> StateLabel("Pendiente", emphasized = false)
                    block.completionStatus == CompletionStatus.FINISHED -> StateLabel(block.completionStatus.label(), emphasized = false)
                    block.completionStatus == CompletionStatus.PARTLY_DONE -> StateLabel(block.completionStatus.label(), emphasized = false)
                    block.completionStatus == CompletionStatus.NOT_DONE -> StateLabel(block.completionStatus.label(), emphasized = false)
                }
            }
        }
    }
}

@Composable
private fun StateLabel(text: String, emphasized: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (emphasized) colors.primary else colors.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyAgenda(
    date: LocalDate,
    isTodaySelected: Boolean,
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
            text = if (isTodaySelected) "Tu agenda de hoy está vacía" else "Tu agenda de mañana está vacía",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Añade un bloque para organizar tu tiempo de enfoque.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RestrictionBanner(
    remainingBlocks: Int,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.error,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Restricción nocturna activa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onErrorContainer,
                )
                Text(
                    if (remainingBlocks == 1) "Añade 1 bloque para liberarla" else "Añade $remainingBlocks bloques para liberarla",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onErrorContainer,
                )
            }
            TextButton(onClick = onOpenPlan) { Text("Planificar") }
        }
    }
}

@Composable
private fun PlannerBottomBar(
    showNextDaySummary: Boolean,
    targetCount: Int,
    requiredCount: Int,
    isRestDayTomorrow: Boolean,
    onOpenTomorrowPlan: () -> Unit,
    onAddBlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showNextDaySummary) {
            Surface(
                onClick = onOpenTomorrowPlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isRestDayTomorrow) "Mañana · día libre" else "Mañana",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isRestDayTomorrow) {
                            Text(
                                text = "Puedes añadir bloques si quieres",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "$targetCount de $requiredCount bloques",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Abrir plan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Button(
            onClick = onAddBlock,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Añadir bloque", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun dateLabel(date: LocalDate, isTodaySelected: Boolean): String {
    val prefix = if (isTodaySelected) "Hoy" else "Mañana"
    val formatted = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", spanishLocale()))
    return "$prefix, $formatted"
}

private fun LocalTime.clockText(): String = format(DateTimeFormatter.ofPattern("HH:mm"))

private fun spanishLocale() = Locale("es", "PE")
