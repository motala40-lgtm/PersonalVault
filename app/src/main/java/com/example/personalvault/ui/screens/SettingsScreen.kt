package com.example.personalvault.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.personalvault.R
import com.example.personalvault.ui.theme.ScreenBackground
import com.example.personalvault.ui.theme.accentScreenBackground
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.BackupManager
import com.example.personalvault.util.PastelPalette
import com.example.personalvault.util.SecurityManager
import com.example.personalvault.viewmodel.VaultViewModel
import com.example.personalvault.util.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SUPPORT_EMAIL = "Newlifetech25@hotmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VaultViewModel, isDarkTheme: Boolean, onBack: () -> Unit, onOpenHelp: () -> Unit, onThemeOrLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(AppPreferences.getThemeMode(context)) }
    var lockEnabled by remember { mutableStateOf(SecurityManager.isLockEnabled(context)) }
    var biometricEnabled by remember { mutableStateOf(SecurityManager.isBiometricEnabled(context)) }
    var screenshotBlocked by remember { mutableStateOf(SecurityManager.isScreenshotBlockEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var accentHex by remember { mutableStateOf(AppPreferences.getAccentColorHex(context)) }
    var wallpaperPath by remember { mutableStateOf(AppPreferences.getCustomWallpaperPath(context)) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = com.example.personalvault.util.FileUtils.copyUriToInternalStorage(
                context, uri, "wallpaper_${System.currentTimeMillis()}.jpg"
            )
            // Drop any previous custom wallpaper file so they don't pile up unused.
            wallpaperPath?.let { old -> runCatching { java.io.File(old).delete() } }
            wallpaperPath = saved.absolutePath
            AppPreferences.setCustomWallpaperPath(context, saved.absolutePath)
            onThemeOrLanguageChanged()
        }
    }
    var showFolderRecoveryDialog by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    var showRestoreSuccessDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }

    // Same background as the folder list (accent color or custom photo), so Settings
    // doesn't feel like a different, plainer app.
    ScreenBackground(isDarkTheme) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // Scrollable: with the accent-color row and folder-recovery section added, this no
        // longer reliably fits one screen — without scroll, the lower sections (including the
        // "set recovery answers" button) could end up unreachable on smaller screens.
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            ThemeMode.values().forEach { mode ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            AppPreferences.setThemeMode(context, mode)
                            onThemeOrLanguageChanged()
                        }
                    )
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row {
                // "White" — the plain/no-gradient option — always comes first.
                AccentSwatch(
                    hex = null,
                    selected = accentHex == null,
                    onClick = {
                        accentHex = null
                        AppPreferences.setAccentColorHex(context, null)
                        // A picked color/white must win over any leftover custom wallpaper —
                        // otherwise the photo (which ScreenBackground checks first) keeps
                        // showing and the person's color choice silently does nothing.
                        wallpaperPath?.let { runCatching { java.io.File(it).delete() } }
                        wallpaperPath = null
                        AppPreferences.setCustomWallpaperPath(context, null)
                        onThemeOrLanguageChanged()
                    }
                )
                PastelPalette.forEach { hex ->
                    AccentSwatch(
                        hex = hex,
                        selected = accentHex == hex,
                        onClick = {
                            accentHex = hex
                            AppPreferences.setAccentColorHex(context, hex)
                            wallpaperPath?.let { runCatching { java.io.File(it).delete() } }
                            wallpaperPath = null
                            AppPreferences.setCustomWallpaperPath(context, null)
                            onThemeOrLanguageChanged()
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.custom_wallpaper_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    com.example.personalvault.markAwaitingExternalResult(context)
                    wallpaperPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }) {
                    Text(stringResource(R.string.pick_wallpaper_button))
                }
                if (wallpaperPath != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        wallpaperPath?.let { runCatching { java.io.File(it).delete() } }
                        wallpaperPath = null
                        AppPreferences.setCustomWallpaperPath(context, null)
                        onThemeOrLanguageChanged()
                    }) {
                        Text(stringResource(R.string.remove_wallpaper_button))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.security), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.lock_with_password))
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !SecurityManager.hasPinSet(context)) {
                            showPinDialog = true
                        } else {
                            lockEnabled = enabled
                            SecurityManager.setLockEnabled(context, enabled)
                        }
                    }
                )
            }

            if (lockEnabled) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.biometric_login))
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = {
                            biometricEnabled = it
                            SecurityManager.setBiometricEnabled(context, it)
                        }
                    )
                }
                TextButton(onClick = { showPinDialog = true }) {
                    Text(stringResource(R.string.change_password))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.block_screenshot))
                Switch(
                    checked = screenshotBlocked,
                    onCheckedChange = {
                        screenshotBlocked = it
                        SecurityManager.setScreenshotBlockEnabled(context, it)
                        onThemeOrLanguageChanged()
                    }
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.folder_recovery_section_title), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.folder_recovery_section_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showFolderRecoveryDialog = true }) {
                Text(stringResource(R.string.set_folder_recovery_button))
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.backup_section_title), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.backup_section_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showExportPasswordDialog = true },
                enabled = !backupInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_backup_button))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { com.example.personalvault.markAwaitingExternalResult(context); restorePicker.launch(arrayOf("*/*")) },
                enabled = !backupInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.restore_backup_button))
            }
            if (backupInProgress) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.help_section_title), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_help_button))
            }

            Spacer(Modifier.height(28.dp))

            Text(stringResource(R.string.support), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                }
                com.example.personalvault.markAwaitingExternalResult(context)
                runCatching { context.startActivity(intent) }
            }) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.contact_support))
            }

            Spacer(Modifier.height(28.dp))
            val versionLabel = remember {
                runCatching {
                    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    "${pkgInfo.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkgInfo)})"
                }.getOrDefault("")
            }
            Text(
                stringResource(R.string.version_label, versionLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    } // close ScreenBackground

    if (showExportPasswordDialog) {
        val appPasswordExists = SecurityManager.hasPinSet(context)
        BackupPasswordDialog(
            title = if (appPasswordExists) stringResource(R.string.confirm_app_password_title) else stringResource(R.string.set_backup_password_title),
            confirmRequired = true,
            verifyAgainstAppPassword = appPasswordExists,
            hint = if (appPasswordExists) stringResource(R.string.confirm_app_password_hint) else stringResource(R.string.set_backup_password_no_recovery_hint),
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { password ->
                showExportPasswordDialog = false
                backupInProgress = true
                coroutineScope.launch {
                    // Make sure nothing the person just did (adding a photo, creating a
                    // folder, etc.) is still mid-write before taking the backup's snapshot —
                    // otherwise it could silently be left out even though it's already
                    // visible on screen.
                    viewModel.awaitPendingWrites()
                    val result = runCatching {
                        withContext(Dispatchers.IO) { BackupManager.exportBackup(context, password) }
                    }
                    backupInProgress = false
                    val file = result.getOrNull()
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        com.example.personalvault.markAwaitingExternalResult(context)
                        context.startActivity(Intent.createChooser(intent, null))
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.backup_export_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    if (showRestoreSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* must restart to continue safely — no dismiss-without-action */ },
            title = { Text(stringResource(R.string.backup_restore_success)) },
            text = { Text(stringResource(R.string.restore_restart_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    // A full process restart — every screen needs to reopen the database
                    // that restore just replaced, not keep using stale connections/state.
                    val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(restartIntent)
                    Runtime.getRuntime().exit(0)
                }) { Text(stringResource(R.string.restart_now)) }
            }
        )
    }

    if (showRestorePasswordDialog) {
        BackupPasswordDialog(
            title = stringResource(R.string.enter_backup_password_title),
            confirmRequired = false,
            hint = stringResource(R.string.enter_backup_password_hint),
            onDismiss = {
                showRestorePasswordDialog = false
                pendingRestoreUri = null
            },
            onConfirm = { password ->
                val uri = pendingRestoreUri
                showRestorePasswordDialog = false
                if (uri != null) {
                    backupInProgress = true
                    coroutineScope.launch {
                        val restoreResult = withContext(Dispatchers.IO) {
                            BackupManager.importBackup(context, uri, password)
                        }
                        backupInProgress = false
                        pendingRestoreUri = null
                        if (restoreResult is BackupManager.RestoreResult.Success) {
                            // The old database connection is now closed and pointing at
                            // replaced files — every screen must reopen it fresh, so a full
                            // app restart is required rather than just showing a toast.
                            showRestoreSuccessDialog = true
                        } else {
                            val messageRes = when (restoreResult) {
                                is BackupManager.RestoreResult.WrongPassword -> R.string.backup_restore_wrong_password
                                is BackupManager.RestoreResult.IncompleteFile -> R.string.backup_restore_incomplete
                                else -> R.string.backup_restore_invalid_file
                            }
                            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    if (showFolderRecoveryDialog) {
        SetFolderRecoveryDialog(
            onDismiss = { showFolderRecoveryDialog = false },
            onConfirm = { pet, city ->
                SecurityManager.setFolderRecoveryAnswers(context, pet, city)
                showFolderRecoveryDialog = false
            }
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin, question, answer ->
                SecurityManager.setPin(context, pin)
                if (question != null && answer != null) {
                    SecurityManager.setSecurityQuestion(context, question, answer)
                }
                SecurityManager.setLockEnabled(context, true)
                lockEnabled = true
                showPinDialog = false
            }
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmRequired: Boolean,
    // When true, this dialog is verifying the person's EXISTING app-wide password (for
    // export) rather than inventing a brand-new one-off backup password — so there's no
    // confirm field, and a wrong entry is caught immediately via SecurityManager.verifyPin
    // instead of silently producing a backup encrypted with an unintended value.
    verifyAgainstAppPassword: Boolean = false,
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minLengthError = stringResource(R.string.password_min_error)
    val mismatchError = stringResource(R.string.passwords_mismatch_error)
    val wrongAppPasswordError = stringResource(R.string.wrong_password)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true
                )
                if (confirmRequired && !verifyAgainstAppPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.confirm_password_label)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.length < 4 -> error = minLengthError
                    verifyAgainstAppPassword && !SecurityManager.verifyPin(context, password) -> error = wrongAppPasswordError
                    confirmRequired && !verifyAgainstAppPassword && password != confirmPassword -> error = mismatchError
                    else -> onConfirm(password)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SetFolderRecoveryDialog(onDismiss: () -> Unit, onConfirm: (pet: String, city: String) -> Unit) {
    var petAnswer by remember { mutableStateOf("") }
    var cityAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val bothRequiredError = stringResource(R.string.folder_recovery_answers_required_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_folder_recovery_button)) },
        text = {
            Column {
                Text(stringResource(R.string.folder_recovery_question_pet))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = petAnswer,
                    onValueChange = { petAnswer = it },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.folder_recovery_question_city))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cityAnswer,
                    onValueChange = { cityAnswer = it },
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
                if (petAnswer.isBlank() || cityAnswer.isBlank()) {
                    error = bothRequiredError
                } else {
                    onConfirm(petAnswer, cityAnswer)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AccentSwatch(hex: String?, selected: Boolean, onClick: () -> Unit) {
    val swatchColor = hex?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.White
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(swatchColor)
            // White needs a visible border on its own; every swatch gets a stronger one when
            // selected so it's clear which color is currently active.
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private val PRESET_SECURITY_QUESTIONS = listOf(
    "preset_q_pet",
    "preset_q_city",
    "preset_q_school",
    "preset_q_mother_name"
)

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (pin: String, question: String?, answer: String?) -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val existingQuestion = SecurityManager.getSecurityQuestion(context)
    var selectedQuestionKey by remember {
        mutableStateOf(PRESET_SECURITY_QUESTIONS.firstOrNull() ?: "preset_q_pet")
    }
    var questionMenuExpanded by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minDigitsError = stringResource(R.string.password_min_error)
    val mismatchError = stringResource(R.string.passwords_mismatch_error)
    val securityRequiredError = stringResource(R.string.security_question_required_error)
    // A security question is only mandatory the first time — once one exists, changing the
    // password doesn't force the user to redo it (they can still edit it here if they want).
    val needsSecuritySetup = !SecurityManager.hasSecurityQuestion(context)
    // Preset questions (not free-typed text) so the exact wording is always known and
    // consistent — matching the same pattern already used for folder-lock recovery.
    val questionLabels = PRESET_SECURITY_QUESTIONS.associateWith { key ->
        stringResource(id = context.resources.getIdentifier(key, "string", context.packageName))
    }
    val selectedQuestionText = questionLabels[selectedQuestionKey] ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_password_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(stringResource(R.string.password_min_digits_label)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = { Text(stringResource(R.string.confirm_password_label)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                )

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.security_question_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = selectedQuestionText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.security_question_label)) },
                        trailingIcon = {
                            IconButton(onClick = { questionMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { questionMenuExpanded = true },
                        singleLine = true
                    )
                    DropdownMenu(expanded = questionMenuExpanded, onDismissRequest = { questionMenuExpanded = false }) {
                        PRESET_SECURITY_QUESTIONS.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(questionLabels[key] ?: "") },
                                onClick = { selectedQuestionKey = key; questionMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(stringResource(R.string.security_answer_label)) },
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
                val question = selectedQuestionText
                when {
                    pin.length < 4 -> error = minDigitsError
                    pin != confirmPin -> error = mismatchError
                    needsSecuritySetup && (question.isBlank() || answer.isBlank()) -> error = securityRequiredError
                    else -> onConfirm(pin, question.takeIf { it.isNotBlank() }, answer.takeIf { it.isNotBlank() })
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
