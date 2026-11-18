package com.jp.freedom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.jp.freedom.domain.AllowedAppsList
import com.jp.freedom.domain.AllowedAppsListPolicy
import com.jp.freedom.domain.AllowedAppsListValidation
import com.jp.freedom.domain.InstalledApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsListsScreen(
    lists: List<AllowedAppsList>,
    installedApps: List<InstalledApp>,
    selectedListId: String,
    selectionMode: Boolean,
    lockedListId: String?,
    onBack: () -> Unit,
    onSelect: (AllowedAppsList) -> Unit,
    onCreate: () -> Unit,
    onEdit: (AllowedAppsList) -> Unit,
    onDelete: (AllowedAppsList) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<AllowedAppsList?>(null) }
    val labelsByPackage = remember(installedApps) { installedApps.associate { it.packageName to it.label } }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allowed apps lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Create list")
                }
            }
        },
    ) { inset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inset),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (selectionMode) "Choose which apps stay available during your session. All other apps will be blocked."
                    else "Manage reusable lists of apps that stay available during a session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                AllowedAppsListCard(
                    list = AllowedAppsList.ESSENTIALS,
                    preview = "Phone, messages, and system functions",
                    selected = selectedListId == AllowedAppsList.ESSENTIALS_ID,
                    selectionMode = selectionMode,
                    locked = false,
                    onClick = { onSelect(AllowedAppsList.ESSENTIALS) },
                    onEdit = {},
                    onDelete = {},
                )
            }
            if (lists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            "You don't have any custom lists yet. Create one for the apps you need during your sessions.",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(lists, key = AllowedAppsList::id) { list ->
                val locked = lockedListId == list.id
                AllowedAppsListCard(
                    list = list,
                    preview = list.packageNames.sorted().mapNotNull(labelsByPackage::get).let { names ->
                        names.take(3).joinToString(" · ") + if (names.size > 3) " · +${names.size - 3}" else ""
                    },
                    selected = selectedListId == list.id,
                    selectionMode = selectionMode,
                    locked = locked,
                    onClick = { onSelect(list) },
                    onEdit = { onEdit(list) },
                    onDelete = { pendingDelete = list },
                )
            }
        }
    }

    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${list.name}”?") },
            text = { Text("This list will be removed from your phone. Sessions already in progress will keep their allowed apps until they end.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(list)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AllowedAppsListCard(
    list: AllowedAppsList,
    preview: String,
    selected: Boolean,
    selectionMode: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isEssentials = list.id == AllowedAppsList.ESSENTIALS_ID
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = selectionMode) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected && selectionMode) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (locked) Icon(Icons.Outlined.Lock, contentDescription = "In use", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (isEssentials) preview else "${list.packageNames.size} apps allowed${if (preview.isNotBlank()) " · $preview" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectionMode) {
                IconButton(onClick = onClick) {
                    Icon(
                        if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Choose ${list.name}",
                        tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isEssentials) {
                IconButton(onClick = onEdit, enabled = !locked) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar ${list.name}")
                }
                IconButton(onClick = onDelete, enabled = !locked) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${list.name}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsListEditorScreen(
    existingList: AllowedAppsList?,
    lists: List<AllowedAppsList>,
    installedApps: List<InstalledApp>,
    locked: Boolean,
    onBack: () -> Unit,
    onSave: suspend (AllowedAppsList) -> AllowedAppsListValidation,
) {
    val scope = rememberCoroutineScope()
    var name by remember(existingList?.id) { mutableStateOf(existingList?.name.orEmpty()) }
    var selectedPackages by remember(existingList?.id) { mutableStateOf(existingList?.packageNames.orEmpty()) }
    var search by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val filteredApps = remember(installedApps, search) {
        installedApps.filter { app ->
            search.isBlank() || app.label.contains(search, ignoreCase = true) || app.packageName.contains(search, ignoreCase = true)
        }
    }
    val nameValidation = AllowedAppsListPolicy.validate(name, lists, existingList?.id)
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingList == null) "New list" else "Edit list") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        val list = AllowedAppsList(
                            id = existingList?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            packageNames = selectedPackages,
                        )
                        scope.launch {
                            isSaving = true
                            error = null
                            when (onSave(list)) {
                                AllowedAppsListValidation.Valid -> onBack()
                                AllowedAppsListValidation.ActiveSessionLocked -> error = "This list is in use and can't be edited until the session ends."
                                AllowedAppsListValidation.MissingName -> error = "Enter a name for this list."
                                AllowedAppsListValidation.NameTooLong -> error = "Use a name with up to ${AllowedAppsListPolicy.MAX_NAME_LENGTH} characters."
                                AllowedAppsListValidation.DuplicateName -> error = "A list with this name already exists."
                            }
                            isSaving = false
                        }
                    },
                    enabled = !locked && !isSaving && nameValidation == AllowedAppsListValidation.Valid,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (isSaving) "Saving…" else "Save list") }
                if (error != null) Text(error.orEmpty(), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
                if (locked) Text("This list is locked while its session is active.", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { inset ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(AllowedAppsListPolicy.MAX_NAME_LENGTH); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("List name") },
                singleLine = true,
                enabled = !locked,
            )
            when (nameValidation) {
                AllowedAppsListValidation.Valid -> Unit
                AllowedAppsListValidation.MissingName -> Text("Enter a name for this list.", color = MaterialTheme.colorScheme.error)
                AllowedAppsListValidation.NameTooLong -> Text("The name can't be longer than ${AllowedAppsListPolicy.MAX_NAME_LENGTH} characters.", color = MaterialTheme.colorScheme.error)
                AllowedAppsListValidation.DuplicateName -> Text("A list with this name already exists, or the name is reserved for Essentials.", color = MaterialTheme.colorScheme.error)
                AllowedAppsListValidation.ActiveSessionLocked -> Unit
            }
            Text("Choose the apps you want to keep available. Unselected apps will be blocked during the session.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search apps") },
                singleLine = true,
                enabled = !locked,
            )
            if (filteredApps.isEmpty()) {
                Text("No installed apps found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredApps, key = InstalledApp::packageName) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !locked) {
                                selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName
                                else selectedPackages + app.packageName
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                enabled = !locked,
                                onCheckedChange = { checked ->
                                    selectedPackages = if (checked) selectedPackages + app.packageName
                                    else selectedPackages - app.packageName
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text("${selectedPackages.size} apps selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
