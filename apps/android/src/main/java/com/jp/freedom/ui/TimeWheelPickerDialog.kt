package com.jp.freedom.ui

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
fun TimeWheelPickerDialog(
    title: String,
    time: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    maxHour: Int = 23,
) {
    val context = LocalContext.current
    val hourPicker = remember(time, maxHour) {
        NumberPicker(context).apply {
            minValue = 0
            maxValue = maxHour
            value = time.hour
            wrapSelectorWheel = true
            setFormatter { String.format("%02d", it) }
            contentDescription = "Horas"
        }
    }
    val minutePicker = remember(time, maxHour) {
        NumberPicker(context).apply {
            minValue = 0
            maxValue = if (maxHour < 23 && time.hour == maxHour) 0 else 59
            value = time.minute.coerceAtMost(maxValue)
            wrapSelectorWheel = true
            setFormatter { String.format("%02d", it) }
            contentDescription = "Minutos"
        }
    }
    hourPicker.setOnValueChangedListener { _, _, hour ->
        minutePicker.maxValue = if (maxHour < 23 && hour == maxHour) 0 else 59
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Horas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AndroidView(
                        factory = { hourPicker },
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("daily-review-hour-picker"),
                    )
                }
                Text(":", style = MaterialTheme.typography.headlineMedium)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Minutos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AndroidView(
                        factory = { minutePicker },
                        modifier = Modifier.padding(horizontal = 4.dp).testTag("daily-review-minute-picker"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(hourPicker.value, minutePicker.value)) }) {
                Text("Confirmar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
