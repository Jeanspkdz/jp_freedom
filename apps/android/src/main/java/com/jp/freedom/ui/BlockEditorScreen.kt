package com.jp.freedom.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
                        if (block == null) "Add block" else "Edit block",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                            errorMessage = "Enter an activity name to save this block."
                            return@Button
                        }
                        if (!startTime.isBefore(endTime)) {
                            errorMessage = "The end time must be after the start time."
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
                    Text(if (isSaving) "Saving…" else "Save block")
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
                    text = date.format(DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.US)),
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
                label = { Text("Activity") },
                placeholder = { Text("e.g. Study math") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = { Text("Use a name that helps you recognize this block in your schedule.") },
            )

            Text("Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeChoice(
                    title = "Start",
                    time = startTime,
                    modifier = Modifier.weight(1f),
                    onChoose = { picked ->
                        startTime = picked
                        errorMessage = null
                    },
                )
                TimeChoice(
                    title = "End",
                    time = endTime,
                    modifier = Modifier.weight(1f),
                    onChoose = { picked ->
                        endTime = picked
                        errorMessage = null
                    },
                )
            }
            Text(
                "Blocks can't overlap and must end on the same day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Access mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = !expandedProfile },
            ) {
                OutlinedTextField(
                    value = accessProfile.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Allowed apps during this block") },
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
                text = "Allowed apps: ${accessProfileDescription(accessProfile)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (block != null) {
                Text("Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    Text("Delete block")
                }
            }
        }
    }

    if (showDeleteConfirmation && block != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this block?") },
            text = { Text("This block will be removed from your schedule and can't be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        scope.launch { onDelete(block.id) }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
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
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
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
    if (showPicker) {
        TimeEntryDialog(
            title = "${title} time",
            time = time,
            onDismiss = { showPicker = false },
            onConfirm = {
                onChoose(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun TimeEntryDialog(
    title: String,
    time: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var hour by remember(time) { mutableStateOf(time.hour.toString().padStart(2, '0')) }
    var minute by remember(time) { mutableStateOf(time.minute.toString().padStart(2, '0')) }
    var showError by remember { mutableStateOf(false) }
    val parsedHour = hour.toIntOrNull()
    val parsedMinute = minute.toIntOrNull()
    val isValid = parsedHour != null && parsedHour in 0..23 &&
        parsedMinute != null && parsedMinute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the time in 24-hour format.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) {
                                hour = value
                                showError = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Hour") },
                        placeholder = { Text("00–23") },
                        singleLine = true,
                        isError = showError && (parsedHour == null || parsedHour !in 0..23),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) {
                                minute = value
                                showError = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minutes") },
                        placeholder = { Text("00–59") },
                        singleLine = true,
                        isError = showError && (parsedMinute == null || parsedMinute !in 0..59),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
                if (showError) {
                    Text(
                        "Enter a time between 00:00 and 23:59.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isValid) onConfirm(LocalTime.of(parsedHour, parsedMinute))
                else showError = true
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun defaultStartTime(): LocalTime {
    val now = LocalTime.now().withSecond(0).withNano(0)
    val roundedMinute = ((now.minute + 14) / 15) * 15
    val rounded = if (roundedMinute == 60) now.plusHours(1).withMinute(0) else now.withMinute(roundedMinute)
    return if (rounded >= LocalTime.of(23, 0)) LocalTime.of(22, 30) else rounded
}

private fun validationMessage(validation: BlockValidation): String? = when (validation) {
    BlockValidation.Valid -> null
    is BlockValidation.MissingName -> "Enter an activity name to save this block."
    is BlockValidation.InvalidTimeRange -> "The end time must be after the start time."
    is BlockValidation.NoteTooLong -> "The note is too long."
    is BlockValidation.Overlapping -> "This block overlaps with “${validation.firstBlockName}” or “${validation.secondBlockName}”. Change the time."
}

private fun accessProfileDescription(profile: AccessProfile): String = when (profile) {
    AccessProfile.ESSENTIALS_ONLY -> "Phone, messages, emergency calls, home screen, Settings, keyboard, and JP Freedom."
    AccessProfile.ESSENTIALS_WITH_CHATGPT -> "Essential apps and ChatGPT."
    AccessProfile.ESSENTIALS_WITH_CHATGPT_AND_SEARCH -> "Essential apps, ChatGPT, and Google Search."
}
