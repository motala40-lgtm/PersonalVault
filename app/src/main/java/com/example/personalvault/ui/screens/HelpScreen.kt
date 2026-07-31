package com.example.personalvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalvault.R

private data class HelpSection(val titleRes: Int, val bodyRes: Int)

private val helpSections = listOf(
    HelpSection(R.string.help_intro_title, R.string.help_intro_body),
    HelpSection(R.string.help_folders_title, R.string.help_folders_body),
    HelpSection(R.string.help_content_title, R.string.help_content_body),
    HelpSection(R.string.help_favtrash_title, R.string.help_favtrash_body),
    HelpSection(R.string.help_reminders_title, R.string.help_reminders_body),
    HelpSection(R.string.help_contacts_title, R.string.help_contacts_body),
    HelpSection(R.string.help_settings_title, R.string.help_settings_body),
    HelpSection(R.string.help_backup_title, R.string.help_backup_body),
    HelpSection(R.string.help_support_title, R.string.help_support_body)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            items(helpSections) { section ->
                Text(
                    stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
                )
                Text(
                    stringResource(section.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
