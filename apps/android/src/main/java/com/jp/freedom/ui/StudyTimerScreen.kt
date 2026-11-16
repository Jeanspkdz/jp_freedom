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
import com.jp.freedom.domain.SavedAccessProfile
import com.jp.freedom.data.LaunchableApp
import com.jp.freedom.domain.CompletionStatus
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
    profiles: List<SavedAccessProfile>, installedApps: List<LaunchableApp>, startError: String?,
    onCreateProfile: (String, Set<String>) -> Unit,
    onUpdateProfile: (SavedAccessProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBack: () -> Unit, onStart: (Duration, SavedAccessProfile?) -> Unit, onStop: () -> Unit,
    onResumeSession: () -> Unit,
    onReview: (String, CompletionStatus, String, String) -> Unit,
    onEndBreak: (String) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(60) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var creatingProfile by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var confirmingStart by remember { mutableStateOf(false) }
    var task by remember(pendingReview?.id) { mutableStateOf(pendingReview?.taskName.orEmpty()) }
    var note by remember(pendingReview?.id) { mutableStateOf(pendingReview?.note.orEmpty()) }
    var remaining by remember(pendingReview?.id) { mutableStateOf(pendingReview?.remaining.orEmpty()) }
    var status by remember(pendingReview?.id) { mutableStateOf<CompletionStatus?>(pendingReview?.completionStatus) }
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
        pendingReview != null -> "Revisar sesión"
        session?.state == StudySessionState.BREAKING -> "Descanso"
        session != null -> "Sesión de estudio"
        else -> "Temporizador"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
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
                        Text("Sesión completada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Estudiaste ${pendingReview.actualMillis / 60_000} min. Guarda cómo te fue para cerrar la sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(task, { task = it }, label = { Text("Actividad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("¿Cómo te fue?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    CompletionStatus.values().filter { it != CompletionStatus.NOT_STARTED }.forEach { option ->
                        val selected = status == option
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { status = option },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (selected) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                 Text(option.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (selected) Text("Seleccionado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(remaining, { remaining = it }, label = { Text("Trabajo pendiente (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = { status?.let { onReview(task.trim(), it, note, remaining) } }, enabled = task.isNotBlank() && status != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Guardar revisión") }
                    if (!StudyTimerPolicy.remaining(pendingReview, now).isZero) {
                        OutlinedButton(onClick = onResumeSession, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Continuar sesión")
                        }
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Más tarde") }
                }

                session?.state == StudySessionState.BREAKING -> {
                    val now = remember(clockTick) { Instant.now() }
                    val remainingTime = StudyTimerPolicy.breakRemaining(session, now)
                    StatusHero(icon = { Icon(Icons.Filled.PauseCircle, contentDescription = null) }, heading = "Tómate un respiro", detail = "Las restricciones están pausadas durante el descanso.")
                    TimerReadout("${remainingTime.toMinutes().toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}", "Descanso restante")
                    OutlinedTextField(nextTask, { nextTask = it }, label = { Text("¿En qué trabajarás después?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onEndBreak(nextTask.trim()) }, enabled = nextTask.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Terminar descanso") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Volver al plan") }
                }

                session != null -> {
                    val now = remember(clockTick) { Instant.now() }
                    val remainingTime = StudyTimerPolicy.remaining(session, now)
                    val elapsedTime = Duration.ofMillis((session.durationMillis - remainingTime.toMillis()).coerceAtLeast(0))
                    StatusHero(icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }, heading = session.taskName.ifBlank { "Sesión en curso" }, detail = "Mantén el ritmo: tu sesión sigue contando aunque salgas de la app.")
                    TimerReadout(
                        "${remainingTime.toHours().toString().padStart(2, '0')}:${(remainingTime.toMinutes() % 60).toString().padStart(2, '0')}:${(remainingTime.seconds % 60).toString().padStart(2, '0')}",
                        "Tiempo restante",
                    )
                    LinearProgressIndicator(
                        progress = { (elapsedTime.toMillis().toFloat() / session.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transcurrido ${formatClock(elapsedTime)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Total ${formatClock(Duration.ofMillis(session.durationMillis))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Acceso protegido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
                                    Text(
                                        session.profileName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Terminar y revisar") }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Volver al plan") }
                }

                else -> {
                    RitmyMascot(modifier = Modifier.align(Alignment.CenterHorizontally), size = 116.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Un bloque para ti", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Elige cuánto tiempo quieres dedicarle y empieza cuando estés listo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text("Duración", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Elige un tiempo habitual o personalízalo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("Elegir otra duración")
                            }
                            if (!validDuration) {
                                Text("Elige una duración entre 1 min y 12 h.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Text("Acceso durante el bloque", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { selectedProfileId = null }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selectedProfileId == null) "✓ " else "") + "Solo esenciales")
                    }
                    profiles.forEach { option ->
                        OutlinedButton(onClick = { selectedProfileId = option.id }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (selectedProfileId == option.id) "✓ " else "") + option.name)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                editingProfileId = option.id
                                profileName = option.name
                                selectedPackages = option.packages
                                creatingProfile = true
                            }) { Text("Editar") }
                            OutlinedButton(onClick = { onDeleteProfile(option.id) }) { Text("Eliminar") }
                        }
                    }
                    OutlinedButton(onClick = {
                        editingProfileId = null
                        profileName = ""
                        selectedPackages = emptySet()
                        creatingProfile = !creatingProfile
                    }, modifier = Modifier.fillMaxWidth()) { Text("Crear perfil de acceso") }
                    if (creatingProfile) {
                        OutlinedTextField(profileName, { profileName = it }, label = { Text("Nombre del perfil") }, modifier = Modifier.fillMaxWidth())
                        installedApps.forEach { app ->
                            FilterChip(
                                selected = app.packageName in selectedPackages,
                                onClick = { selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName else selectedPackages + app.packageName },
                                label = { Text(app.label) },
                            )
                        }
                        Button(onClick = {
                            val editingId = editingProfileId
                            if (editingId == null) onCreateProfile(profileName.trim(), selectedPackages)
                            else onUpdateProfile(SavedAccessProfile(editingId, profileName.trim(), selectedPackages))
                            profileName = ""
                            selectedPackages = emptySet()
                            editingProfileId = null
                            creatingProfile = false
                        }, enabled = profileName.isNotBlank()) { Text("Guardar perfil") }
                    }
                    if (startError != null) Text(startError, color = MaterialTheme.colorScheme.error)
                    if (profiles.firstOrNull { it.id == selectedProfileId }?.packages?.contains("com.android.chrome") == true) {
                        Text("Este perfil permite Chrome para sitios de estudio aprobados durante la sesión.")
                    }
                    Button(onClick = { confirmingStart = true }, enabled = validDuration, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Iniciar sesión")
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Volver al plan") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showDurationPicker) {
        TimeWheelPickerDialog(
            title = "Duración personalizada",
            time = LocalTime.of(selectedMinutes / 60, selectedMinutes % 60),
            maxHour = 12,
            onDismiss = { showDurationPicker = false },
            onConfirm = { selected ->
                minutes = selected.hour * 60 + selected.minute
                showDurationPicker = false
            },
        )
    }
    if (confirmingStart) {
        val chosen = profiles.firstOrNull { it.id == selectedProfileId }
        AlertDialog(
            onDismissRequest = { confirmingStart = false },
            title = { Text("¿Iniciar el temporizador?") },
            text = { Text(if (selectedProfileId != null && chosen == null) "El perfil ya no está disponible. Elige otro." else "${chosen?.name ?: "Solo esenciales"} no se podrá cambiar durante esta sesión.") },
            confirmButton = { Button(onClick = { confirmingStart = false; onStart(Duration.ofMinutes(selectedMinutes.toLong()), chosen) }, enabled = selectedProfileId == null || chosen != null) { Text("Iniciar") } },
            dismissButton = { OutlinedButton(onClick = { confirmingStart = false }) { Text("Cambiar perfil") } },
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
