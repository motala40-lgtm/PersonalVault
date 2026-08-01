package com.example.personalvault.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.data.Contact
import com.example.personalvault.ui.theme.ScreenBackground
import com.example.personalvault.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: VaultViewModel, isDarkTheme: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var deletingContact by remember { mutableStateOf<Contact?>(null) }

    // Small contact lists don't need a dedicated DB search query — filtering the already
    // loaded list client-side keeps this screen simple.
    val filtered = remember(contacts, query) {
        if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.phone?.contains(query) == true ||
                    it.phone2?.contains(query) == true
            }
        }
    }

    ScreenBackground(isDarkTheme) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_contacts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_contact))
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_contacts_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_contacts_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            onClick = { editingContact = contact },
                            onCall = { dialNumber(context, contact.phone) },
                            onMessage = { sendSms(context, contact.phone) },
                            onToggleFavorite = { viewModel.toggleContactFavorite(contact) },
                            onDelete = { deletingContact = contact }
                        )
                    }
                }
            }
        }
    }
    }

    if (showAddDialog) {
        ContactEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, phone2, email, note ->
                viewModel.addContact(Contact(name = name, phone = phone, phone2 = phone2, email = email, note = note))
                showAddDialog = false
            }
        )
    }

    editingContact?.let { contact ->
        ContactEditDialog(
            initial = contact,
            onDismiss = { editingContact = null },
            onSave = { name, phone, phone2, email, note ->
                viewModel.updateContact(contact.copy(name = name, phone = phone, phone2 = phone2, email = email, note = note))
                editingContact = null
            }
        )
    }

    deletingContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { deletingContact = null },
            title = { Text(stringResource(R.string.delete_contact_title)) },
            text = { Text(contact.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContact(contact)
                    deletingContact = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingContact = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (!contact.phone.isNullOrBlank()) {
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!contact.phone.isNullOrBlank()) {
                IconButton(onClick = onCall) {
                    Icon(Icons.Default.Call, contentDescription = stringResource(R.string.call))
                }
                IconButton(onClick = onMessage) {
                    Icon(Icons.Default.Message, contentDescription = stringResource(R.string.send_sms))
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorite_label)) },
                        leadingIcon = {
                            Icon(
                                if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null
                            )
                        },
                        onClick = { menuExpanded = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactEditDialog(
    initial: Contact?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?, phone2: String?, email: String?, note: String?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var phone2 by remember { mutableStateOf(initial?.phone2 ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val nameRequiredError = stringResource(R.string.contact_name_required_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_contact else R.string.edit_contact)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.contact_name_label)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.contact_phone_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone2,
                    onValueChange = { phone2 = it },
                    label = { Text(stringResource(R.string.contact_phone2_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.contact_email_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.contact_note_label)) },
                    maxLines = 3
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = nameRequiredError
                } else {
                    onSave(
                        name.trim(),
                        phone.trim().ifBlank { null },
                        phone2.trim().ifBlank { null },
                        email.trim().ifBlank { null },
                        note.trim().ifBlank { null }
                    )
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun dialNumber(context: android.content.Context, phone: String?) {
    if (phone.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    runCatching { context.startActivity(intent) }
}

private fun sendSms(context: android.content.Context, phone: String?) {
    if (phone.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
    runCatching { context.startActivity(intent) }
}
