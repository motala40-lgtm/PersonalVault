package com.example.personalvault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalvault.data.Folder
import com.example.personalvault.ui.screens.*
import com.example.personalvault.ui.theme.PersonalVaultTheme
import com.example.personalvault.util.SecurityManager
import com.example.personalvault.util.ThemeMode
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.LocaleHelper
import com.example.personalvault.viewmodel.VaultViewModel
import com.example.personalvault.worker.TrashCleanupWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor

sealed class Screen {
    object FolderList : Screen()
    data class FolderDetail(val folder: Folder) : Screen()
    object Favorites : Screen()
    object Trash : Screen()
    object Reminders : Screen()
    object Contacts : Screen()
    object Help : Screen()
    object Settings : Screen()
}

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private var recomposeTrigger by mutableStateOf(0)

    // Compose state that lives on the Activity itself (not inside `remember`), so it can also
    // be flipped from outside Compose — specifically, when the whole app goes to background.
    private var unlocked by mutableStateOf(true)

    // Re-lock the vault whenever the entire app (every screen, not just one Activity call like
    // opening the camera) leaves the foreground. Without this, once unlocked the vault stayed
    // open forever until the app process was killed.
    private val lockOnBackgroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && SecurityManager.isLockEnabled(this)) {
            unlocked = false
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyStoredLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        unlocked = !SecurityManager.isLockEnabled(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockOnBackgroundObserver)
        requestNotificationPermissionIfNeeded()

        applyScreenshotProtection()
        schedulePeriodicTrashCleanup()

        setContent {
            recomposeTrigger // read to force recomposition when settings change
            val themeMode = AppPreferences.getThemeMode(this)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PersonalVaultTheme(darkTheme = darkTheme) {
                var screen by remember { mutableStateOf<Screen>(Screen.FolderList) }
                var showOnboarding by remember { mutableStateOf(!AppPreferences.hasSeenOnboarding(this)) }

                if (showOnboarding) {
                    WelcomeScreen(onGetStarted = {
                        AppPreferences.setHasSeenOnboarding(this, true)
                        showOnboarding = false
                    })
                } else if (!unlocked) {
                    LockScreen(
                        onUnlocked = { unlocked = true },
                        onRequestBiometric = { showBiometricPrompt { unlocked = true } }
                    )
                } else {
                    when (val current = screen) {
                        is Screen.FolderList -> FolderListScreen(
                            viewModel = viewModel,
                            isDarkTheme = darkTheme,
                            onOpenFolder = { screen = Screen.FolderDetail(it) },
                            onOpenFavorites = { screen = Screen.Favorites },
                            onOpenTrash = { screen = Screen.Trash },
                            onOpenReminders = { screen = Screen.Reminders },
                            onOpenContacts = { screen = Screen.Contacts },
                            onOpenSettings = { screen = Screen.Settings },
                            onSearch = { viewModel.onSearchQueryChanged(it) }
                        )
                        is Screen.FolderDetail -> FolderScreen(
                            folder = current.folder,
                            viewModel = viewModel,
                            onBack = { screen = Screen.FolderList }
                        )
                        is Screen.Favorites -> FavoritesScreen(
                            viewModel = viewModel,
                            isDarkTheme = darkTheme,
                            onBack = { screen = Screen.FolderList }
                        )
                        is Screen.Trash -> TrashScreen(
                            viewModel = viewModel,
                            isDarkTheme = darkTheme,
                            onBack = { screen = Screen.FolderList }
                        )
                        is Screen.Reminders -> ReminderListScreen(
                            viewModel = viewModel,
                            isDarkTheme = darkTheme,
                            onBack = { screen = Screen.FolderList }
                        )
                        is Screen.Contacts -> ContactsScreen(
                            viewModel = viewModel,
                            isDarkTheme = darkTheme,
                            onBack = { screen = Screen.FolderList }
                        )
                        is Screen.Settings -> SettingsScreen(
                            isDarkTheme = darkTheme,
                            onBack = { screen = Screen.FolderList },
                            onOpenHelp = { screen = Screen.Help },
                            onThemeOrLanguageChanged = {
                                recomposeTrigger++
                                applyScreenshotProtection()
                            }
                        )
                        is Screen.Help -> HelpScreen(
                            onBack = { screen = Screen.Settings }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockOnBackgroundObserver)
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Required at runtime on Android 13+ (API 33) — without it, reminder notifications
        // are silently dropped by the system.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyScreenshotProtection() {
        if (SecurityManager.isScreenshotBlockEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun schedulePeriodicTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trash_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_login))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()
        prompt.authenticate(promptInfo)
    }
}
