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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.FocusSettings
import com.jp.freedom.domain.SUPPORTED_EVENING_CUTOFFS
import com.jp.freedom.domain.BlocklistValidation
import com.jp.freedom.domain.DesktopSiteBlocklist
import com.jp.freedom.data.SyncStatus
import com.jp.freedom.sync.EndpointValidation
import com.jp.freedom.sync.SyncConfiguration
import com.jp.freedom.sync.SyncEndpoint
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: FocusSettings,
    desktopBlockedDomains: List<String>,
    syncConfiguration: SyncConfiguration,
    syncStatus: SyncStatus,
    syncRevision: Long,
    syncMessage: String?,
    accessibilityEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    siteFilterEnabled: Boolean,
    onBack: () -> Unit,
    onSettingsChange: (FocusSettings) -> Unit,
    onDesktopDomainsChange: (List<String>) -> Unit,
    onConfigureSync: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestSiteFilter: () -> Unit,
) {
    var newDomain by rememberSaveable { mutableStateOf("") }
    var domainError by remember { mutableStateOf<String?>(null) }
    var endpoint by rememberSaveable(syncConfiguration.endpoint) { mutableStateOf(syncConfiguration.endpoint) }
    var writeToken by remember { mutableStateOf("") }
    var syncConfigError by remember { mutableStateOf<String?>(null) }
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
                Text("Sitios bloqueados en Chrome", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Esta lista se administra en el teléfono y se comparte con Chrome cuando sincroniza. WhatsApp Web sigue disponible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                desktopBlockedDomains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(domain, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = {
                            domainError = null
                            onDesktopDomainsChange(desktopBlockedDomains - domain)
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Quitar $domain")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it; domainError = null },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Dominio") },
                        placeholder = { Text("ejemplo.com") },
                    )
                    Button(onClick = {
                        val domain = DesktopSiteBlocklist.normalize(newDomain)
                        when (val validation = DesktopSiteBlocklist.validate(desktopBlockedDomains + domain)) {
                            BlocklistValidation.Valid -> {
                                onDesktopDomainsChange(desktopBlockedDomains + domain)
                                newDomain = ""
                            }
                            BlocklistValidation.DuplicateDomain -> domainError = "Ese dominio ya está en la lista."
                            BlocklistValidation.TooManyDomains -> domainError = "La lista admite hasta 100 dominios."
                            is BlocklistValidation.InvalidDomain -> domainError = "Escribe un dominio válido, como ejemplo.com, sin ruta ni comodines."
                        }
                    }) { Text("Añadir") }
                }
                domainError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; syncConfigError = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Dirección de sincronización") },
                        placeholder = { Text("https://tu-api.example") },
                    )
                    OutlinedTextField(
                        value = writeToken,
                        onValueChange = { writeToken = it; syncConfigError = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Token de escritura de Android") },
                        placeholder = { Text(if (syncConfiguration.hasWriteToken) "Guardado de forma segura" else "SYNC_API_WRITE_TOKEN") },
                    )
                    Text(
                        "La clave se guarda cifrada en este teléfono. No uses el token de solo lectura de Chrome.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        when (val validation = SyncEndpoint.validate(endpoint)) {
                            is EndpointValidation.Invalid -> syncConfigError = validation.explanation
                            is EndpointValidation.Valid -> when {
                                writeToken.isBlank() && !syncConfiguration.hasWriteToken ->
                                    syncConfigError = "Ingresa el token de escritura de Android para configurar la sincronización."
                                else -> {
                                    onConfigureSync(validation.endpoint, writeToken.trim())
                                    endpoint = validation.endpoint
                                    writeToken = ""
                                    syncConfigError = null
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (syncConfiguration.hasWriteToken) "Guardar conexión" else "Configurar sincronización")
                    }
                    syncConfigError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(syncStatusLabel(syncStatus, syncConfiguration.hasWriteToken), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            syncMessage ?: when {
                                !syncConfiguration.hasWriteToken -> "Los cambios se guardan en el teléfono. Configura la API y el token de escritura para sincronizarlos."
                                syncStatus == SyncStatus.CONFLICT_RESOLVED -> "Se combinó una versión del servidor con tus cambios pendientes y se guardó el resultado."
                                syncStatus == SyncStatus.PENDING -> "Los cambios locales esperan conexión y se volverán a enviar automáticamente."
                                syncStatus == SyncStatus.UP_TO_DATE -> "Los cambios del teléfono y del servidor están sincronizados."
                                else -> "Los planes y la aplicación de reglas siguen funcionando desde este teléfono sin internet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = syncConfiguration.hasWriteToken && syncStatus != SyncStatus.SYNCING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (syncStatus == SyncStatus.ERROR) "Reintentar sincronización" else "Sincronizar ahora")
                        }
                        Text(
                            if (syncRevision >= 0) {
                                "Revisión guardada en el servidor: $syncRevision. La extensión muestra su última revisión descargada para que puedas compararlas."
                            } else {
                                "El teléfono todavía no ha guardado una revisión en el servidor."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

private fun syncStatusLabel(status: SyncStatus, configured: Boolean): String = when {
    !configured -> "Sin configurar"
    status == SyncStatus.SYNCING -> "Sincronizando"
    status == SyncStatus.PENDING -> "Pendiente"
    status == SyncStatus.UP_TO_DATE -> "Al día"
    status == SyncStatus.CONFLICT_RESOLVED -> "Conflicto resuelto"
    status == SyncStatus.ERROR -> "Necesita atención"
    else -> "Listo para sincronizar"
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
