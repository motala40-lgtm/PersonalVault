package com.example.personalvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.ui.components.EntryItem
import com.example.personalvault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: VaultViewModel, onBack: () -> Unit) {
    val trash by viewModel.trash.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_trash)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                stringResource(R.string.trash_auto_delete_notice),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                items(trash, key = { it.id }) { entry ->
                    EntryItem(
                        entry = entry,
                        onTogglePin = {},
                        onToggleFavorite = {},
                        onDelete = {},
                        inTrash = true,
                        onRestore = { viewModel.restoreFromTrash(entry) },
                        onDeletePermanently = { viewModel.deletePermanently(entry) }
                    )
                }
            }
        }
    }
}
