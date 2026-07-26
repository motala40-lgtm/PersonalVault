package com.example.personalvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.data.Folder
import com.example.personalvault.ui.components.EntryItem
import com.example.personalvault.viewmodel.VaultViewModel

private val FolderColors = listOf(
    "#6750A4", "#386641", "#BC4749", "#1D4E89", "#C08552", "#457B9D"
)

@Composable
fun FolderListScreen(
    viewModel: VaultViewModel,
    onOpenFolder: (Folder) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavShortcut(Icons.Default.Favorite, stringResource(R.string.nav_favorites), onOpenFavorites)
            NavShortcut(Icons.Default.Alarm, stringResource(R.string.nav_reminders), onOpenReminders)
            NavShortcut(Icons.Default.Delete, stringResource(R.string.nav_trash), onOpenTrash)
            NavShortcut(Icons.Default.Settings, stringResource(R.string.nav_settings), onOpenSettings)
        }

        Divider(Modifier.padding(vertical = 8.dp))

        if (query.isNotBlank()) {
            Text(stringResource(R.string.search_results), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
            LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                items(searchResults, key = { it.id }) { entry ->
                    EntryItem(
                        entry = entry,
                        onTogglePin = { viewModel.togglePin(entry) },
                        onToggleFavorite = { viewModel.toggleFavorite(entry) },
                        onDelete = { viewModel.moveToTrash(entry) }
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.folders_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                items(folders, key = { it.id }) { folder ->
                    FolderRow(folder = folder, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddFolderDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, color ->
                viewModel.createFolder(name, color, "Folder")
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun NavShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderRow(folder: Folder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(android.graphics.Color.parseColor(folder.colorHex)))
        )
        Spacer(Modifier.width(12.dp))
        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AddFolderDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(FolderColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name_label)) },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    FolderColors.forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, selectedColor) }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
