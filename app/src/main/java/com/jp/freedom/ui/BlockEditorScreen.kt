package com.jp.freedom.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.AccessProfile
import com.jp.freedom.domain.BlockValidation
import com.jp.freedom.domain.CompletionStatus
import com.jp.freedom.domain.PlannedBlock
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditorScreen(
    date: LocalDate,
    block: PlannedBlock?,
    onBack: () -> Unit,
    onSave: suspend (PlannedBlock) -> BlockValidation,
    onDelete: suspend (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(block?.id, date) { mutableStateOf(block?.name.orEmpty()) }
    var startTime by remember(block?.id, date) { mutableStateOf(block?.startTime ?: defaultStartTime()) }
    var endTime by remember(block?.id, date) { mutableStateOf(block?.endTime ?: startTime.plusMinutes(60)) }
    var accessProfile by remember(block?.id, date) {
        mutableStateOf(block?.accessProfile ?: AccessProfile.ESSENTIALS_ONLY)
    }
    var completionStatus by remember(block?.id, date) {
        mutableStateOf(block?.completionStatus ?: CompletionStatus.NOT_STARTED)
    }
    var expandedProfile by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (block == null) "Añadir bloque" else "Editar bloque",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Escribe una actividad para guardar el bloque."
                            return@Button
                        }
                        if (!startTime.isBefore(endTime)) {
                            errorMessage = "La hora de fin debe ser posterior a la hora de inicio."
                            return@Button
                        }
                        val updated = PlannedBlock(
                            id = block?.id ?: UUID.randomUUID().toString(),
                            date = date,
                            name = name.trim(),
                            startTime = startTime,
                            endTime = endTime,
                            accessProfile = accessProfile,
                            completionStatus = completionStatus,
                        )
                        scope.launch {
                            isSaving = true
                            errorMessage = null
                            val result = onSave(updated)
                            isSaving = false
                            errorMessage = validationMessage(result)
                            if (result == BlockValidation.Valid) onBack()
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(if (isSaving) "Guardando…" else "Guardar bloque")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.EventNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${date.dayOfMonth} de ${date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es", "PE"))}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Actividad") },
                placeholder = { Text("Ej. Estudiar matemáticas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = { Text("El nombre te ayuda a reconocer el bloque en tu agenda.") },
            )

            Text("Horario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeChoice(
                    title = "Inicio",
                    time = startTime,
                    modifier = Modifier.weight(1f),
                    onChoose = { picked ->
                        startTime = picked
                        errorMessage = null
                    },
                )
                TimeChoice(
                    title = "Fin",
                    time = endTime,
                    modifier = Modifier.weight(1f),
                    onChoose = { picked ->
                        endTime = picked
                        errorMessage = null
                    },
                )
            }
            Text(
                "Los bloques no pueden superponerse y deben terminar el mismo día.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Modo de bloqueo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = !expandedProfile },
            ) {
                OutlinedTextField(
                    value = accessProfile.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Acceso durante el bloque") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expandedProfile,
                    onDismissRequest = { expandedProfile = false },
                ) {
                    AccessProfile.entries.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.title) },
                            onClick = {
                                accessProfile = profile
                                expandedProfile = false
                            },
                        )
                    }
                }
            }
            Text(
                text = accessProfileDescription(accessProfile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (block != null) {
                Text("Progreso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompletionStatus.entries.forEach { status ->
                        FilterChip(
                            selected = completionStatus == status,
                            onClick = { completionStatus = status },
                            label = { Text(status.label()) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar bloque")
                }
            }
        }
    }

    if (showDeleteConfirmation && block != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("¿Eliminar este bloque?") },
            text = { Text("Se quitará de la agenda y no se podrá recuperar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        scope.launch { onDelete(block.id) }
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun TimeChoice(
    title: String,
    time: LocalTime,
    modifier: Modifier = Modifier,
    onChoose: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChoose(LocalTime.of(hour, minute)) },
                time.hour,
                time.minute,
                true,
            ).show()
        },
        modifier = modifier.height(56.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(Icons.Outlined.AccessTime, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun defaultStartTime(): LocalTime {
    val now = LocalTime.now().withSecond(0).withNano(0)
    val roundedMinute = ((now.minute + 14) / 15) * 15
    val rounded = if (roundedMinute == 60) now.plusHours(1).withMinute(0) else now.withMinute(roundedMinute)
    return if (rounded >= LocalTime.of(23, 0)) LocalTime.of(22, 30) else rounded
}

private fun validationMessage(validation: BlockValidation): String? = when (validation) {
    BlockValidation.Valid -> null
    is BlockValidation.MissingName -> "Escribe una actividad para guardar el bloque."
    is BlockValidation.InvalidTimeRange -> "La hora de fin debe ser posterior a la hora de inicio."
    is BlockValidation.Overlapping -> "Este bloque se superpone con “${validation.firstBlockName}” o “${validation.secondBlockName}”. Cambia el horario."
}

private fun accessProfileDescription(profile: AccessProfile): String = when (profile) {
    AccessProfile.ESSENTIALS_ONLY -> "Llamadas, mensajes, emergencias y el planificador permanecen disponibles."
    AccessProfile.ESSENTIALS_WITH_CHATGPT -> "También se permite la app y el sitio de ChatGPT."
    AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH -> "También se permiten ChatGPT y Google Search."
}
