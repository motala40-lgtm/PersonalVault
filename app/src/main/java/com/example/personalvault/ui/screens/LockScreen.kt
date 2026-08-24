package com.example.personalvault.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalvault.R
import com.example.personalvault.util.SecurityManager

private const val PIN_LENGTH = 4

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

    fun tryUnlock() {
        if (SecurityManager.verifyPin(context, pin)) {
            onUnlocked()
        } else {
            error = wrongPasswordText
            pin = ""
        }
    }

    fun onDigit(digit: Int) {
        if (pin.length >= PIN_LENGTH) return
        error = null
        pin += digit
        if (pin.length == PIN_LENGTH) tryUnlock()
    }

    fun onBackspace() {
        if (pin.isEmpty()) return
        error = null
        pin = pin.dropLast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFDBCE), Color(0xFFFAF9F6), Color(0xFFEAE2D0))
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFDBCE)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo_bayganikade),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.vault_locked),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1C1A)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.password_label),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF56423A)
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .then(
                            if (filled) {
                                Modifier.background(Color(0xFFA04111))
                            } else {
                                Modifier
                                    .border(2.dp, Color(0xFFDDC0B6), CircleShape)
                                    .background(Color(0xFFFAF9F6))
                            }
                        )
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))

        val digitRows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9)
        )
        digitRows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                row.forEach { digit ->
                    PinPadButton(digit.toString(), onClick = { onDigit(digit) })
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Spacer(Modifier.size(64.dp))
            PinPadButton("0", onClick = { onDigit(0) })
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onBackspace() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Backspace, contentDescription = stringResource(R.string.back), tint = Color(0xFFA04111))
            }
        }

        Spacer(Modifier.weight(1f))

        if (biometricEnabled) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFFFFF))
                    .clickable(onClick = onRequestBiometric),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.biometric_login),
                    tint = Color(0xFFA04111),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        TextButton(onClick = { showForgotDialog = true }) {
            Text(stringResource(R.string.forgot_password), color = Color(0xFF56423A))
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
private fun PinPadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color(0xFFF27E4B).copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF622000)
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
