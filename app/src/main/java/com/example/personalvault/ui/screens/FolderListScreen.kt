package com.example.personalvault.ui.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalvault.R
import com.example.personalvault.data.Folder
import com.example.personalvault.ui.components.EntryItem
import com.example.personalvault.ui.theme.accentScreenBackground
import com.example.personalvault.util.AppLanguage
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.PastelPalette
import com.example.personalvault.util.SecurityManager
import com.example.personalvault.viewmodel.VaultViewModel

// Cheerful pastel palette — shared with the theme accent-color picker in Settings.
private val FolderColors = PastelPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    viewModel: VaultViewModel,
    isDarkTheme: Boolean,
    onOpenFolder: (Folder) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Per-folder lock flow. Each holds the folder currently going through that step;
    // "changingPasswordFor" verifies the old PIN first, then hands off to "settingLockFor"
    // to collect the new one — reusing the same set-pin dialog for both lock and re-lock.
    var openingFolder by remember { mutableStateOf<Folder?>(null) }
    var settingLockFor by remember { mutableStateOf<Folder?>(null) }
    var removingLockFor by remember { mutableStateOf<Folder?>(null) }
    var changingPasswordFor by remember { mutableStateOf<Folder?>(null) }
    // Set alongside settingLockFor only when we got there via "forgot folder password" while
    // trying to open a folder — after the new password is saved, we still owe the person
    // actually getting into the folder they were trying to open.
    var openFolderAfterNewPassword by remember { mutableStateOf<Folder?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // The background gradient now follows whatever accent color the user picked in Settings
    // (one of the pastel folder colors, or "White" for a plain background) instead of being
    // a fixed sky blue. Dark theme always keeps the flat theme background regardless.
    val accentHex = AppPreferences.getAccentColorHex(context)
    val screenBackground = accentScreenBackground(accentHex, isDarkTheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(screenBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_title)) },
                    actions = {
                        Image(
                            painter = painterResource(R.drawable.logo_easy_archive),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                // A cheerful, fixed modern blue for the bottom bar — independent of the
                // user's accent-color choice above, since this is a design accent rather
                // than the theme background. Dark theme keeps the default bar color.
                val bottomBarColor = if (isDarkTheme) BottomAppBarDefaults.containerColor else Color(0xFF1E88E5)
                val bottomBarContentColor = if (isDarkTheme) contentColorFor(bottomBarColor) else Color.White

                BottomAppBar(
                    containerColor = bottomBarColor,
                    contentColor = bottomBarContentColor,
                    actions = {
                        // Each icon sits on its own small raised chip (bigger + a translucent
                        // circle behind it) so they stand out more than a flat icon would —
                        // and each keeps its own fixed color in light theme, as requested.
                        // In dark theme, Settings falls back to a light neutral instead of pure
                        // black, since black-on-dark would repeat the exact "text disappears"
                        // problem this whole round of fixes is trying to solve.
                        BottomBarChip(onClick = onOpenFavorites, contentDescriptionText = stringResource(R.string.nav_favorites)) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(26.dp))
                        }
                        BottomBarChip(onClick = onOpenReminders, contentDescriptionText = stringResource(R.string.nav_reminders)) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(26.dp))
                        }
                        BottomBarChip(onClick = onOpenContacts, contentDescriptionText = stringResource(R.string.nav_contacts)) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(26.dp))
                        }
                        BottomBarChip(onClick = onOpenTrash, contentDescriptionText = stringResource(R.string.nav_trash)) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEC407A), modifier = Modifier.size(26.dp))
                        }
                        BottomBarChip(onClick = onOpenSettings, contentDescriptionText = stringResource(R.string.nav_settings)) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = if (isDarkTheme) bottomBarContentColor else Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        BottomBarChip(onClick = { showLanguageDialog = true }, contentDescriptionText = stringResource(R.string.app_language)) {
                            // A flat monochrome icon can't look "colorful" — a real emoji glyph
                            // renders in full color on Android regardless of icon tint.
                            Text("\uD83C\uDF0D", fontSize = 24.sp)
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = if (isDarkTheme) FloatingActionButtonDefaults.containerColor else Color.White,
                            contentColor = if (isDarkTheme) contentColorFor(FloatingActionButtonDefaults.containerColor) else bottomBarColor
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_folder))
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
                                FolderCard(
                                    folder = folder,
                                    onClick = {
                                        if (folder.isLocked) openingFolder = folder else onOpenFolder(folder)
                                    },
                                    onLockFolder = { settingLockFor = folder },
                                    onRemoveLock = { removingLockFor = folder },
                                    onChangePassword = { changingPasswordFor = folder }
                                )
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

    openingFolder?.let { folder ->
        VerifyFolderPinDialog(
            folder = folder,
            onDismiss = { openingFolder = null },
            onVerified = { usedRecovery ->
                openingFolder = null
                if (usedRecovery) {
                    // Recovering via the two questions means the old PIN is presumably lost —
                    // walk straight into setting a new one instead of silently reopening the
                    // folder with the same forgotten PIN still in place.
                    settingLockFor = folder
                    openFolderAfterNewPassword = folder
                } else {
                    onOpenFolder(folder)
                }
            }
        )
    }

    removingLockFor?.let { folder ->
        VerifyFolderPinDialog(
            folder = folder,
            onDismiss = { removingLockFor = null },
            onVerified = {
                viewModel.updateFolder(folder.copy(isLocked = false, pinHash = null))
                removingLockFor = null
            }
        )
    }

    changingPasswordFor?.let { folder ->
        VerifyFolderPinDialog(
            folder = folder,
            onDismiss = { changingPasswordFor = null },
            onVerified = {
                changingPasswordFor = null
                settingLockFor = folder
            }
        )
    }

    settingLockFor?.let { folder ->
        SetFolderPinDialog(
            onDismiss = {
                settingLockFor = null
                openFolderAfterNewPassword = null
            },
            onConfirm = { pin ->
                viewModel.updateFolder(folder.copy(isLocked = true, pinHash = SecurityManager.hashValue(pin)))
                settingLockFor = null
                if (openFolderAfterNewPassword == folder) {
                    openFolderAfterNewPassword = null
                    onOpenFolder(folder)
                }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }
}

/**
 * Wraps a bottom-bar icon in a small raised circular chip — bigger and more visually prominent
 * than a bare IconButton, which is what makes these icons read as "embossed" rather than flat.
 */
@Composable
private fun BottomBarChip(
    onClick: () -> Unit,
    contentDescriptionText: String,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = contentDescriptionText }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FolderCard(
    folder: Folder,
    onClick: () -> Unit,
    onLockFolder: () -> Unit,
    onRemoveLock: () -> Unit,
    onChangePassword: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
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
                        tint = Color(0xFF5A5A5A),
                        modifier = Modifier.size(32.dp)
                    )
                    if (folder.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF5A5A5A),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    )
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (folder.isLocked) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.change_folder_password)) },
                    onClick = { showMenu = false; onChangePassword() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_folder_lock)) },
                    onClick = { showMenu = false; onRemoveLock() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lock_folder)) },
                    onClick = { showMenu = false; onLockFolder() }
                )
            }
        }
    }
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPreferences.getLanguage(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column {
                AppLanguage.values().forEach { lang ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = language == lang,
                            onClick = {
                                if (language != lang) {
                                    language = lang
                                    AppPreferences.setLanguage(context, lang)
                                    (context as? Activity)?.recreate()
                                }
                            }
                        )
                        Text(if (lang == AppLanguage.FA) stringResource(R.string.language_fa) else stringResource(R.string.language_en))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
        }
    )
}

@Composable
private fun VerifyFolderPinDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onVerified: (usedRecovery: Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pin by remember { mutableStateOf("") }
    var petAnswer by remember { mutableStateOf("") }
    var cityAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Fallback path for a forgotten folder PIN: two fixed recovery questions (set up once
    // in Settings) rather than the folder's own PIN.
    var useRecoveryQuestions by remember { mutableStateOf(false) }
    val hasRecoverySetup = remember { SecurityManager.hasFolderRecoverySetup(context) }
    val wrongPasswordText = stringResource(R.string.wrong_password)
    val wrongRecoveryAnswerText = stringResource(R.string.wrong_folder_recovery_answer)
    val noRecoverySetupNotice = stringResource(R.string.no_folder_recovery_set_notice)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name) },
        text = {
            Column {
                if (useRecoveryQuestions) {
                    Text(stringResource(R.string.folder_recovery_question_pet))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = petAnswer,
                        onValueChange = { petAnswer = it; error = null },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.folder_recovery_question_city))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cityAnswer,
                        onValueChange = { cityAnswer = it; error = null },
                        singleLine = true
                    )
                } else {
                    Text(stringResource(R.string.enter_folder_password))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; error = null },
                        label = { Text(stringResource(R.string.password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (!useRecoveryQuestions) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        if (hasRecoverySetup) {
                            useRecoveryQuestions = true
                            error = null
                        } else {
                            error = noRecoverySetupNotice
                        }
                    }) {
                        Text(stringResource(R.string.forgot_folder_password))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val success = if (useRecoveryQuestions) {
                    SecurityManager.verifyFolderRecoveryAnswers(context, petAnswer, cityAnswer)
                } else {
                    folder.pinHash != null && folder.pinHash == SecurityManager.hashValue(pin)
                }
                if (success) {
                    onVerified(useRecoveryQuestions)
                } else {
                    error = if (useRecoveryQuestions) wrongRecoveryAnswerText else wrongPasswordText
                }
            }) { Text(stringResource(R.string.login)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SetFolderPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minDigitsError = stringResource(R.string.password_min_error)
    val mismatchError = stringResource(R.string.passwords_mismatch_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_folder_password_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(stringResource(R.string.password_min_digits_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = { Text(stringResource(R.string.confirm_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = minDigitsError
                    pin != confirmPin -> error = mismatchError
                    else -> onConfirm(pin)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
                FolderColors.chunked(4).forEach { rowColors ->
                    Row {
                        rowColors.forEach { colorHex ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .then(
                                        if (selectedColor == colorHex)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
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
