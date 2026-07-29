package com.example.personalvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Surface (rather than a bare Column) is what makes this screen react to theme/color
    // changes — without it the background stayed whatever the static window background was.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_title)) }
                )
            },
            bottomBar = {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = onOpenFavorites) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.nav_favorites))
                        }
                        IconButton(onClick = onOpenReminders) {
                            Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.nav_reminders))
                        }
                        IconButton(onClick = onOpenTrash) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.nav_trash))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_folder))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                if (query.isNotBlank()) {
                    Text(
                        stringResource(R.string.search_results),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
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
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.folders_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(8.dp))

                    if (folders.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.no_folders_yet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders, key = { it.id }) { folder ->
                                FolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                            }
                        }
                    }
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
private fun FolderCard(folder: Folder, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(android.graphics.Color.parseColor(folder.colorHex))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
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
