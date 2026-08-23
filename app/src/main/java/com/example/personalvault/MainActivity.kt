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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
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
import com.example.personalvault.util.CrashReporter
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

// AppCompatActivity (a strict superset of FragmentActivity — all Fragment/ActivityResult
// APIs still work) is required for AppCompatDelegate.setApplicationLocales() to correctly
// auto-recreate this Activity when the app language changes; a plain FragmentActivity
// doesn't wire up that hook.
class MainActivity : AppCompatActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private var recomposeTrigger by mutableStateOf(0)

    // Compose state that lives on the Activity itself (not inside `remember`), so it can also
    // be flipped from outside Compose — specifically, when the whole app goes to background.
    private var unlocked by mutableStateOf(true)

    // Re-lock the vault whenever the entire app (every screen, not just one Activity call like
    // opening the camera) leaves the foreground. Without this, once unlocked the vault stayed
    // open forever until the app process was killed.
    //
    // Beyond re-locking, we also finish() the Activity on background so returning via the
    // Recents screen starts the app fresh (folder list / lock screen) rather than resuming
    // wherever the person left off. We only do this when the whole TASK is no longer visible
    // (isFinishing==false but the app genuinely backgrounded), and never while we're waiting on
    // a picker/camera/share result we launched ourselves.
    private val lockOnBackgroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            if (SecurityManager.isLockEnabled(this)) {
                unlocked = false
            }
            if (!isChangingConfigurations && !awaitingExternalResult) {
                finish()
            }
            // One-shot: clear the guard so the next real backgrounding finishes normally.
            awaitingExternalResult = false
        }
    }

    // Set to true right before launching an external picker/camera/share intent, so the
    // background-finish above doesn't kill us while we're legitimately waiting for its result.
    var awaitingExternalResult: Boolean = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always first, unconditionally, so it's guaranteed to catch anything that follows —
        // including the locale check right below, which (on a genuine first-ever launch) can
        // itself trigger an Activity recreate() partway through this very function.
        CrashReporter.install(this)

        if (LocaleHelper.ensureDefaultLanguageIfNeverSet()) {
            // A recreate() is already in flight from the call above (setting the app's very
            // first default language) — stop here rather than continuing to set up state and
            // UI on this Activity instance, which is about to be torn down and replaced by a
            // fresh onCreate() run anyway (where this same check becomes a no-op and normal
            // startup proceeds). Continuing past this point was producing crashes: parts of
            // onCreate() — permission requests, security checks, and especially setContent()
            // building the whole Compose UI — could run against, or race with the teardown
            // of, an Activity instance already being destroyed.
            return
        }

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
                var pendingCrashReport by remember { mutableStateOf(CrashReporter.getPendingCrashReport(this)) }

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
                            viewModel = viewModel,
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

                pendingCrashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { /* require an explicit choice */ },
                        title = { Text(stringResource(R.string.crash_report_title)) },
                        text = { Text(stringResource(R.string.crash_report_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val intent = CrashReporter.buildCrashEmailIntent(report)
                                runCatching { startActivity(intent) }
                                CrashReporter.clearPendingCrashReport(this)
                                pendingCrashReport = null
                            }) { Text(stringResource(R.string.crash_report_send)) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashReporter.clearPendingCrashReport(this)
                                pendingCrashReport = null
                            }) { Text(stringResource(R.string.crash_report_decline)) }
                        }
                    )
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

/**
 * Marks that an external picker/camera/share intent is about to be launched, so the app's
 * background-finish logic (which otherwise restarts the app fresh whenever it leaves the
 * foreground) doesn't kill the Activity while it's legitimately waiting for that result.
 * Safe no-op if the context isn't a MainActivity.
 */
fun markAwaitingExternalResult(context: android.content.Context) {
    var c = context
    while (c is android.content.ContextWrapper) {
        if (c is MainActivity) { c.awaitingExternalResult = true; return }
        c = c.baseContext
    }
}
