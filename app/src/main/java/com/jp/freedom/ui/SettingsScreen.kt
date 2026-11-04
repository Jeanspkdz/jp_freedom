package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.SUPPORTED_EVENING_CUTOFFS
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: FocusSettings,
    accessibilityEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    siteFilterEnabled: Boolean,
    onBack: () -> Unit,
    onSettingsChange: (FocusSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestSiteFilter: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Configuración",
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Planificación de mañana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Define el mínimo de bloques y la hora en que comienza la restricción nocturna.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bloques requeridos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mínimo para liberar el teléfono", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Cada bloque cuenta una vez que está en el plan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (settings.requiredBlockCount > 1) {
                                onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount - 1))
                            }
                        },
                        enabled = settings.requiredBlockCount > 1,
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Reducir mínimo")
                    }
                    Text(
                        settings.requiredBlockCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = {
                            if (settings.requiredBlockCount < Int.MAX_VALUE) {
                                onSettingsChange(settings.copy(requiredBlockCount = settings.requiredBlockCount + 1))
                            }
                        },
                        enabled = settings.requiredBlockCount < Int.MAX_VALUE,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aumentar mínimo")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hora de corte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Si el plan requerido sigue incompleto a esta hora, se activa la restricción nocturna.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SUPPORTED_EVENING_CUTOFFS.forEachIndexed { index, cutoff ->
                        SegmentedButton(
                            selected = settings.eveningCutoff == cutoff,
                            onClick = { onSettingsChange(settings.copy(eveningCutoff = cutoff)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SUPPORTED_EVENING_CUTOFFS.size),
                        ) {
                            Text(cutoff.label())
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Protección del teléfono", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                PermissionCard(
                    icon = Icons.Outlined.AppBlocking,
                    title = "Filtro de sitios en Chrome",
                    enabled = siteFilterEnabled,
                    status = if (siteFilterEnabled) "Activo" else "Sin activar",
                    description = if (siteFilterEnabled) {
                        "El filtro aplica la lista a consultas DNS enviadas por esta VPN. No se ha verificado que impida todas las conexiones HTTPS a un sitio."
                    } else {
                        "Filtra consultas DNS mediante una VPN local durante los bloques. No se ha verificado que impida todas las conexiones HTTPS a un sitio. Android pedirá permiso para configurar la VPN."
                    },
                    actionLabel = if (siteFilterEnabled) "Gestionar en Ajustes" else "Activar filtro web",
                    onAction = onRequestSiteFilter,
                )
                PermissionCard(
                    icon = Icons.Outlined.AppBlocking,
                    title = "Bloqueo de apps",
                    enabled = accessibilityEnabled,
                    status = if (accessibilityEnabled) "Activo" else "Sin activar",
                    description = if (accessibilityEnabled) {
                        "JP Freedom puede mantener disponibles las apps permitidas y devolver el resto a tu plan."
                    } else {
                        "Activa el servicio de accesibilidad para restringir otras apps durante el enfoque o la restricción nocturna."
                    },
                    actionLabel = if (accessibilityEnabled) "Gestionar en Ajustes" else "Activar en Ajustes",
                    onAction = onOpenAccessibilitySettings,
                )
                PermissionCard(
                    icon = Icons.Outlined.Alarm,
                    title = "Inicio puntual de los bloqueos",
                    enabled = exactAlarmsAllowed,
                    status = if (exactAlarmsAllowed) "Disponible" else "Permiso pendiente",
                    description = if (exactAlarmsAllowed) {
                        "Android puede iniciar los bloques y evaluar la hora de corte puntualmente."
                    } else {
                        "Sin este permiso, Android puede retrasar el inicio de un bloque o la evaluación del corte."
                    },
                    actionLabel = if (exactAlarmsAllowed) "Ver permiso" else "Permitir alarmas exactas",
                    onAction = onRequestExactAlarmAccess,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Tus planes y ajustes se guardan en este teléfono y siguen disponibles sin internet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    status: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = colors.primary)
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (enabled) {
                    Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = colors.primary)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) colors.primary else colors.error,
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun LocalTime.label(): String = when (hour) {
    21 -> "9 p. m."
    22 -> "10 p. m."
    else -> "11 p. m."
}
