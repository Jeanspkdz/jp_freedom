package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.StudyTimerPolicy
import com.jp.freedom.domain.StudyTimerSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    date: LocalDate,
    studySessions: List<StudyTimerSession>,
    activeStudySession: StudyTimerSession?,
    pendingSessionReview: StudyTimerSession?,
    settings: FocusSettings,
    accessibilityEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenStudyTimer: () -> Unit,
    onOpenDailyReview: () -> Unit,
) {
    val actualMinutes = StudyTimerPolicy.dailyReview(
        date, ZoneId.systemDefault(), settings.studyHoursTargetMinutes, studySessions, emptyList(),
    ).actualMinutes.toInt()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tally", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                onClick = onOpenStudyTimer,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.School, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        pendingSessionReview != null -> "Review session"
                        activeStudySession != null -> "View active session"
                        else -> "Start session"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Today · ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${actualMinutes / 60}:${(actualMinutes % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                    Text("STUDIED", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val target = settings.studyHoursTargetMinutes
                    Text("${target / 60}:${(target % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                    Text("DAILY GOAL", style = MaterialTheme.typography.labelSmall)
                }
            }
            LinearProgressIndicator(
                progress = { (actualMinutes.toFloat() / settings.studyHoursTargetMinutes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            TextButton(onClick = onOpenDailyReview, modifier = Modifier.align(Alignment.End)) { Text("Review day") }
            if (!accessibilityEnabled) {
                Text("Enable accessibility in Settings to block distracting apps during a session.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
