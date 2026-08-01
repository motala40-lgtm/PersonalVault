package com.example.personalvault.ui.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.example.personalvault.ui.theme.ScreenBackground
import com.example.personalvault.ui.theme.accentScreenBackground
import com.example.personalvault.util.AppLanguage
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.GridColumns
import com.example.personalvault.util.PastelPalette
import com.example.personalvault.util.SecurityManager
import com.example.personalvault.viewmodel.VaultViewModel

// Cheerful pastel palette — shared with the theme accent-color picker in Settings.
private val FolderColors = PastelPalette

/** The three non-lock actions available from a folder's three-dot menu. */
private enum class FolderMenuAction { COPY, DELETE, SHARE }

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

    // Copy / delete / share for a folder that's locked must first verify its PIN — otherwise
    // the copy action alone would let someone bypass a folder lock by simply duplicating it
    // into an unlocked copy.
    var pendingLockedAction by remember { mutableStateOf<Pair<Folder, FolderMenuAction>?>(null) }
    var confirmingDeleteFor by remember { mutableStateOf<Folder?>(null) }
    val copyNameSuffix = stringResource(R.string.folder_copy_suffix)
    val shareChooserTitle = stringResource(R.string.share_folder)
    val shareEmptyNotice = stringResource(R.string.folder_share_empty_notice)
    val context = LocalContext.current
    var folderGridColumns by remember { mutableStateOf(AppPreferences.getFolderGridColumns(context)) }

    fun runFolderAction(folder: Folder, action: FolderMenuAction) {
        when (action) {
            FolderMenuAction.COPY -> viewModel.copyFolder(folder, copyNameSuffix)
            FolderMenuAction.DELETE -> confirmingDeleteFor = folder
            FolderMenuAction.SHARE -> viewModel.shareFolder(context, folder) { zip ->
                if (zip == null) {
                    android.widget.Toast.makeText(context, shareEmptyNotice, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", zip
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, shareChooserTitle))
                }
            }
        }
    }

    fun requestFolderAction(folder: Folder, action: FolderMenuAction) {
        if (folder.isLocked) {
            pendingLockedAction = folder to action
        } else {
            runFolderAction(folder, action)
        }
    }

    ScreenBackground(isDarkTheme) {
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
                val bottomBarColor = if (isDarkTheme) BottomAppBarDefaults.containerColor else Color(0xFF4FC3F7)
                val bottomBarContentColor = if (isDarkTheme) contentColorFor(bottomBarColor) else Color.White

                BottomAppBar(
                    containerColor = bottomBarColor,
                    contentColor = bottomBarContentColor,
                    actions = {
                        AppIconChip(
                            icon = Icons.Rounded.Favorite,
                            baseColor = Color(0xFFE53935),
                            contentDescriptionText = stringResource(R.string.nav_favorites),
                            onClick = onOpenFavorites
                        )
                        AppIconChip(
                            icon = Icons.Rounded.NotificationsActive,
                            baseColor = Color(0xFFFFA726),
                            contentDescriptionText = stringResource(R.string.nav_reminders),
                            onClick = onOpenReminders
                        )
                        AppIconChip(
                            icon = Icons.Rounded.Phone,
                            baseColor = Color(0xFF43A047),
                            contentDescriptionText = stringResource(R.string.nav_contacts),
                            onClick = onOpenContacts
                        )
                        AppIconChip(
                            icon = Icons.Rounded.DeleteOutline,
                            baseColor = Color(0xFFD6336C),
                            contentDescriptionText = stringResource(R.string.nav_trash),
                            onClick = onOpenTrash
                        )
                        AppIconChip(
                            icon = Icons.Rounded.Settings,
                            baseColor = Color(0xFF37474F),
                            contentDescriptionText = stringResource(R.string.nav_settings),
                            onClick = onOpenSettings
                        )
                        EmojiIconChip(
                            emoji = "\uD83C\uDF0D",
                            baseColor = Color(0xFF1E88E5),
                            contentDescriptionText = stringResource(R.string.app_language),
                            onClick = { showLanguageDialog = true }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xFF7C4DFF),
                            contentColor = Color.White
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
                    trailingIcon = {
                        IconButton(onClick = {
                            folderGridColumns = AppPreferences.cycleFolderGridColumns(context)
                        }) {
                            Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.change_grid_size))
                        }
                    },
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
                            columns = GridCells.Fixed(folderGridColumns.count),
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
                                    onChangePassword = { changingPasswordFor = folder },
                                    onCopyFolder = { requestFolderAction(folder, FolderMenuAction.COPY) },
                                    onDeleteFolder = { requestFolderAction(folder, FolderMenuAction.DELETE) },
                                    onShareFolder = { requestFolderAction(folder, FolderMenuAction.SHARE) }
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

    pendingLockedAction?.let { (folder, action) ->
        VerifyFolderPinDialog(
            folder = folder,
            onDismiss = { pendingLockedAction = null },
            onVerified = {
                pendingLockedAction = null
                runFolderAction(folder, action)
            }
        )
    }

    confirmingDeleteFor?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmingDeleteFor = null },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(folder.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder)
                    confirmingDeleteFor = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteFor = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(onDismiss = { showLanguageDialog = false })
    }
}

/**
 * A colorful, rounded-square "chip" icon with a subtle gradient + drop shadow to approximate
 * a glossy 3D look — a simple vector icon can't fully match custom 3D artwork, but the
 * gradient/shadow combination gets meaningfully closer than a flat tinted icon.
 */
@Composable
private fun AppIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    baseColor: Color,
    contentDescriptionText: String,
    onClick: () -> Unit
) {
    val gradientTop = lerp(baseColor, Color.White, 0.35f)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = contentDescriptionText }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), clip = false)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(gradientTop, baseColor))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** Same chip shell as [AppIconChip], but for the language button's emoji glyph (not a tintable icon). */
@Composable
private fun EmojiIconChip(
    emoji: String,
    baseColor: Color,
    contentDescriptionText: String,
    onClick: () -> Unit
) {
    val gradientTop = lerp(baseColor, Color.White, 0.35f)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = contentDescriptionText }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), clip = false)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(gradientTop, baseColor))),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
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
    onChangePassword: () -> Unit,
    onCopyFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    onShareFolder: () -> Unit
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_folder)) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = { showMenu = false; onCopyFolder() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_folder)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { showMenu = false; onShareFolder() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_folder)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { showMenu = false; onDeleteFolder() }
            )
        }
    }
}

/** Maps each [AppLanguage] to the string resource holding its name, in its own native script. */
private fun languageNameRes(lang: AppLanguage): Int = when (lang) {
    AppLanguage.FA -> R.string.language_fa
    AppLanguage.EN -> R.string.language_en
    AppLanguage.FR -> R.string.language_fr
    AppLanguage.DE -> R.string.language_de
    AppLanguage.ES -> R.string.language_es
    AppLanguage.AR -> R.string.language_ar
    AppLanguage.RU -> R.string.language_ru
    AppLanguage.ZH -> R.string.language_zh
    AppLanguage.HI -> R.string.language_hi
    AppLanguage.TR -> R.string.language_tr
    AppLanguage.SV -> R.string.language_sv
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPreferences.getLanguage(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                        Text(stringResource(languageNameRes(lang)))
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
                    folder.pinHash != null && SecurityManager.verifyFolderPin(pin, folder.pinHash)
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
