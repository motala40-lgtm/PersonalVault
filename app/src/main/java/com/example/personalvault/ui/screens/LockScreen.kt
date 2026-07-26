package com.example.personalvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalvault.R
import com.example.personalvault.util.PinCheckResult
import com.example.personalvault.util.SecurityManager
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Counts down while a brute-force lockout is active; 0 means the user can try again.
    var lockoutRemainingMillis by remember { mutableStateOf(0L) }
    val biometricEnabled = remember { SecurityManager.isBiometricEnabled(context) }
    val wrongPasswordAttemptsLeftTemplate = stringResource(R.string.wrong_password_attempts_left)
    val lockedOutTemplate = stringResource(R.string.locked_out_message)
    val secondsTemplate = stringResource(R.string.seconds_format)
    val minutesSecondsTemplate = stringResource(R.string.minutes_seconds_format)

    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).toInt().coerceAtLeast(1)
        return if (totalSeconds < 60) {
            String.format(secondsTemplate, totalSeconds)
        } else {
            String.format(minutesSecondsTemplate, totalSeconds / 60, totalSeconds % 60)
        }
    }

    // Live countdown while locked out, so the message stays accurate second by second.
    LaunchedEffect(lockoutRemainingMillis > 0) {
        while (lockoutRemainingMillis > 0) {
            delay(1000)
            lockoutRemainingMillis = (lockoutRemainingMillis - 1000).coerceAtLeast(0)
            error = if (lockoutRemainingMillis > 0) {
                String.format(lockedOutTemplate, formatDuration(lockoutRemainingMillis))
            } else {
                null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) onRequestBiometric()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.vault_locked), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        val isLockedOut = lockoutRemainingMillis > 0
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; if (!isLockedOut) error = null },
            label = { Text(stringResource(R.string.password_label)) },
            enabled = !isLockedOut,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !isLockedOut,
            onClick = {
                when (val result = SecurityManager.checkPin(context, pin)) {
                    is PinCheckResult.Success -> onUnlocked()
                    is PinCheckResult.WrongPin -> {
                        error = String.format(wrongPasswordAttemptsLeftTemplate, result.attemptsRemaining)
                        pin = ""
                    }
                    is PinCheckResult.LockedOut -> {
                        lockoutRemainingMillis = result.remainingMillis
                        error = String.format(lockedOutTemplate, formatDuration(result.remainingMillis))
                        pin = ""
                    }
                }
            }
        ) {
            Text(stringResource(R.string.login))
        }

        if (biometricEnabled && !isLockedOut) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRequestBiometric) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.biometric_login))
            }
        }
    }
}
