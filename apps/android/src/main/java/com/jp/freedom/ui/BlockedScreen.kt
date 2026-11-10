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
import com.jp.freedom.enforcement.BlockedReason

@Composable
fun BlockedScreen(
    reason: BlockedReason?,
    onOpenPlanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToBlockedApp: () -> Unit,
) {
    val isEveningRestriction = reason == BlockedReason.EVENING_RESTRICTION
    val isSiteFilterSetup = reason == BlockedReason.SITE_FILTER_SETUP
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
                text = when {
                    isEveningRestriction -> "Evening restriction is active"
                    isSiteFilterSetup -> "Website filter needs setup"
                    else -> "App blocked during focus time"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    isEveningRestriction -> "Complete the required blocks for tomorrow in your planner to unlock your phone."
                    isSiteFilterSetup -> "Turn on the website filter in Settings to use ChatGPT or Google Search in Chrome during this block."
                    else -> "This app isn't included in the access mode selected for this block."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onOpenPlanner, modifier = Modifier.height(52.dp)) {
                Text("Open planner")
            }
            if (isSiteFilterSetup) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.height(52.dp)) {
                    Text("Set up website filter")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBackToBlockedApp, modifier = Modifier.height(52.dp)) {
                Text("Go to home")
            }
        }
    }
}
