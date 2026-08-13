package com.example.personalvault.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personalvault.R

/**
 * First-launch welcome screen — shown exactly once (gated by
 * [com.example.personalvault.util.AppPreferences.hasSeenOnboarding]) before the person ever
 * reaches the folder list or a lock screen.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_easy_archive),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.app_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C1A)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3A3529),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA04111),
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.get_started), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { /* informational only, no dedicated privacy screen yet */ }) {
                Text(
                    stringResource(R.string.learn_about_privacy),
                    color = Color(0xFF56423A),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
