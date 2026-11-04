package com.jp.freedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BlockedScreen(
    reason: String?,
    onOpenPlanner: () -> Unit,
    onBackToBlockedApp: () -> Unit,
) {
    val isEveningRestriction = reason == "evening_restriction"
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(44.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isEveningRestriction) "Restricción nocturna activa" else "App bloqueada durante el enfoque",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isEveningRestriction) {
                    "Completa los bloques requeridos del próximo día en tu planificador para liberar el teléfono."
                } else {
                    "Esta app no forma parte del modo de acceso elegido para el bloque actual."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onOpenPlanner, modifier = Modifier.height(52.dp)) {
                Text("Abrir planificador")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBackToBlockedApp, modifier = Modifier.height(52.dp)) {
                Text("Volver al inicio")
            }
        }
    }
}
