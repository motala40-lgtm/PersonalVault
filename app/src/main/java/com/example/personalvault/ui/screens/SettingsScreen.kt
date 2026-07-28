package com.example.personalvault.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.util.AppLanguage
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.SecurityManager
import com.example.personalvault.util.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeOrLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(AppPreferences.getThemeMode(context)) }
    var language by remember { mutableStateOf(AppPreferences.getLanguage(context)) }
    var lockEnabled by remember { mutableStateOf(SecurityManager.isLockEnabled(context)) }
    var biometricEnabled by remember { mutableStateOf(SecurityManager.isBiometricEnabled(context)) }
    var screenshotBlocked by remember { mutableStateOf(SecurityManager.isScreenshotBlockEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
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

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AppLanguage.values().forEach { lang ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = language == lang,
                        onClick = {
                            if (language != lang) {
                                language = lang
                                AppPreferences.setLanguage(context, lang)
                                // A locale change only takes effect on resource lookups after
                                // attachBaseContext runs again, so the Activity must be recreated.
                                (context as? Activity)?.recreate()
                            }
                        }
                    )
                    Text(if (lang == AppLanguage.FA) stringResource(R.string.language_fa) else stringResource(R.string.language_en))
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.security), style = MaterialTheme.typography.titleMedium)
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
                        onThemeOrLanguageChanged() // reuse to trigger activity to re-apply FLAG_SECURE
                    }
                )
            }
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                SecurityManager.setPin(context, pin)
                SecurityManager.setLockEnabled(context, true)
                lockEnabled = true
                showPinDialog = false
            }
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minDigitsError = stringResource(R.string.password_min_error)
    val mismatchError = stringResource(R.string.passwords_mismatch_error)

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
