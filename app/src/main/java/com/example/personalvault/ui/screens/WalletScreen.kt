package com.example.personalvault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.personalvault.R
import com.example.personalvault.data.WalletCard
import com.example.personalvault.ui.theme.ScreenBackground
import com.example.personalvault.util.FileUtils
import com.example.personalvault.viewmodel.VaultViewModel

/**
 * A private, free-form card wallet — any kind of card (bank, ID, insurance, anything), stored
 * as one or two photos plus an optional label. Deliberately does NOT have typed fields (card
 * number, expiry, etc): the photo itself is the record, matching how a physical wallet works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(viewModel: VaultViewModel, isDarkTheme: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val cards by viewModel.walletCards.collectAsState()

    var pendingFrontPath by remember { mutableStateOf<String?>(null) }
    var pendingBackPath by remember { mutableStateOf<String?>(null) }
    var captureTarget by remember { mutableStateOf<String?>(null) } // "front" | "back" | null
    var showAddChoiceMenu by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var showCameraScreen by remember { mutableStateOf(false) }
    var viewingCard by remember { mutableStateOf<WalletCard?>(null) }
    var deletingCard by remember { mutableStateOf<WalletCard?>(null) }

    fun resetAddFlow() {
        pendingFrontPath = null
        pendingBackPath = null
        captureTarget = null
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = FileUtils.copyUriToInternalStorage(context, uri, "wallet_${System.currentTimeMillis()}.jpg")
            if (captureTarget == "back") pendingBackPath = saved.absolutePath else pendingFrontPath = saved.absolutePath
            if (captureTarget == "back") showLabelDialog = true else showAddChoiceMenu = false
        }
        captureTarget = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showCameraScreen = true }

    fun requestCamera(target: String) {
        captureTarget = target
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) showCameraScreen = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun requestGallery(target: String) {
        captureTarget = target
        com.example.personalvault.markAwaitingExternalResult(context)
        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    ScreenBackground(isDarkTheme) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.wallet_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    resetAddFlow()
                    showAddChoiceMenu = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.wallet_add_card))
                }
            }
        ) { padding ->
            if (cards.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.wallet_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.586f) // standard card ratio
                                    .clickable { viewingCard = card }
                            ) {
                                AsyncImageCompat(
                                    path = card.frontImagePath,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                // Three-dot menu, top-right of the thumbnail — matches the
                                // same style used for files inside regular folders.
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ) {
                                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                                        }
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = { menuExpanded = false; deletingCard = card }
                                        )
                                    }
                                }
                            }
                            if (!card.label.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    card.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Step 1: choose how to capture the FRONT photo, or (if front already picked) the BACK.
    if (showAddChoiceMenu) {
        val isBackStep = pendingFrontPath != null
        AlertDialog(
            onDismissRequest = {
                showAddChoiceMenu = false
                if (!isBackStep) resetAddFlow()
            },
            title = { Text(if (isBackStep) stringResource(R.string.wallet_add_back_photo) else stringResource(R.string.wallet_add_front_photo)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showAddChoiceMenu = false
                        requestCamera(if (isBackStep) "back" else "front")
                    }) { Text(stringResource(R.string.wallet_take_photo)) }
                    TextButton(onClick = {
                        showAddChoiceMenu = false
                        requestGallery(if (isBackStep) "back" else "front")
                    }) { Text(stringResource(R.string.wallet_choose_from_gallery)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (isBackStep) {
                    TextButton(onClick = {
                        showAddChoiceMenu = false
                        showLabelDialog = true
                    }) { Text(stringResource(R.string.wallet_skip_back_photo)) }
                } else {
                    TextButton(onClick = {
                        showAddChoiceMenu = false
                        resetAddFlow()
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    // After the front photo is captured/picked (outside the dialog above, since gallery/camera
    // results arrive asynchronously), offer the back-photo step.
    LaunchedEffect(pendingFrontPath) {
        if (pendingFrontPath != null && pendingBackPath == null && !showLabelDialog) {
            showAddChoiceMenu = true
        }
    }

    // Step 2: optional label, then save.
    if (showLabelDialog) {
        var labelText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { /* must finish or cancel explicitly */ },
            title = { Text(stringResource(R.string.wallet_label_title)) },
            text = {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text(stringResource(R.string.wallet_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val front = pendingFrontPath
                    if (front != null) {
                        viewModel.addWalletCard(
                            WalletCard(
                                label = labelText.trim().ifBlank { null },
                                frontImagePath = front,
                                backImagePath = pendingBackPath
                            )
                        )
                    }
                    showLabelDialog = false
                    resetAddFlow()
                }) { Text(stringResource(R.string.wallet_save_card)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLabelDialog = false
                    resetAddFlow()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Full-screen zoomable viewer for an existing card — no dialog chrome; delete now lives
    // in the grid's three-dot menu instead of as a button here.
    viewingCard?.let { card ->
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            Column(
                Modifier.fillMaxSize().padding(top = 48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ZoomableCardImage(path = card.frontImagePath, modifier = Modifier.weight(1f))
                card.backImagePath?.let { back ->
                    Spacer(Modifier.height(8.dp))
                    ZoomableCardImage(path = back, modifier = Modifier.weight(1f))
                }
            }
            IconButton(
                onClick = { viewingCard = null },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }

    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text(stringResource(R.string.wallet_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWalletCard(card)
                    deletingCard = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCard = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCameraScreen) {
        CardCameraScreen(
            onCaptured = { file ->
                showCameraScreen = false
                if (captureTarget == "back") {
                    pendingBackPath = file.absolutePath
                    showLabelDialog = true
                } else {
                    pendingFrontPath = file.absolutePath
                }
                captureTarget = null
            },
            onCancel = {
                showCameraScreen = false
                captureTarget = null
            }
        )
    }
}

@Composable
private fun AsyncImageCompat(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    coil.compose.AsyncImage(
        model = FileUtils.resolveVaultFile(context, path),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/** A single card photo the person can pinch-to-zoom and pan, for the full-screen viewer. */
@Composable
private fun ZoomableCardImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    coil.compose.AsyncImage(
        model = FileUtils.resolveVaultFile(context, path),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            }
    )
}
