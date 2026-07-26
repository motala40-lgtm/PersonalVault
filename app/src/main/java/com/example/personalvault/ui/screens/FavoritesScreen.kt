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
fun FavoritesScreen(viewModel: VaultViewModel, onBack: () -> Unit) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 8.dp)) {
            items(favorites, key = { it.id }) { entry ->
                EntryItem(
                    entry = entry,
                    onTogglePin = { viewModel.togglePin(entry) },
                    onToggleFavorite = { viewModel.toggleFavorite(entry) },
                    onDelete = { viewModel.moveToTrash(entry) }
                )
            }
        }
    }
}
