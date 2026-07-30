package com.example.personalvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.personalvault.R
import com.example.personalvault.util.SecurityManager

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val biometricEnabled = remember { SecurityManager.isBiometricEnabled(context) }
    val wrongPasswordText = stringResource(R.string.wrong_password)

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

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = null },
            label = { Text(stringResource(R.string.password_label)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (SecurityManager.verifyPin(context, pin)) {
                onUnlocked()
            } else {
                error = wrongPasswordText
            }
        }) {
            Text(stringResource(R.string.login))
        }

        if (biometricEnabled) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRequestBiometric) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.biometric_login))
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showForgotDialog = true }) {
            Text(stringResource(R.string.forgot_password))
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotDialog = false },
            onPasswordReset = {
                showForgotDialog = false
                onUnlocked()
            }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(onDismiss: () -> Unit, onPasswordReset: () -> Unit) {
    val context = LocalContext.current
    val question = remember { SecurityManager.getSecurityQuestion(context) }
    // step 0: no security question was ever set up — dead end, points to support.
    // step 1: show the question and collect the answer.
    // step 2: answer verified — collect and save a new PIN.
    var step by remember { mutableStateOf(if (question == null) 0 else 1) }
    var answer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val wrongAnswerText = stringResource(R.string.wrong_security_answer)
    val minDigitsError = stringResource(R.string.password_min_error)
    val mismatchError = stringResource(R.string.passwords_mismatch_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_password_title)) },
        text = {
            Column {
                when (step) {
                    0 -> Text(stringResource(R.string.no_security_question_set))
                    1 -> {
                        Text(question ?: "")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it; error = null },
                            label = { Text(stringResource(R.string.security_answer_label)) },
                            singleLine = true
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
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
                    }
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
                1 -> TextButton(onClick = {
                    if (SecurityManager.verifySecurityAnswer(context, answer)) {
                        error = null
                        step = 2
                    } else {
                        error = wrongAnswerText
                    }
                }) { Text(stringResource(R.string.confirm_action)) }
                else -> TextButton(onClick = {
                    when {
                        newPin.length < 4 -> error = minDigitsError
                        newPin != confirmPin -> error = mismatchError
                        else -> {
                            SecurityManager.setPin(context, newPin)
                            onPasswordReset()
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
