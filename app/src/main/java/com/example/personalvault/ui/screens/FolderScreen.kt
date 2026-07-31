package com.example.personalvault.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.personalvault.R
import com.example.personalvault.data.Entry
import com.example.personalvault.data.EntryType
import com.example.personalvault.data.Folder
import com.example.personalvault.ui.components.EntryGridCard
import com.example.personalvault.util.AppPreferences
import com.example.personalvault.util.FileUtils
import com.example.personalvault.util.GridColumns
import com.example.personalvault.viewmodel.VaultViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folder: Folder,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entriesForFolder(folder.id).collectAsState()
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableStateOf(AppPreferences.getGridColumns(context)) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var viewingImageEntry by remember { mutableStateOf<Entry?>(null) }
    var viewingTextEntry by remember { mutableStateOf<Entry?>(null) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun autoName(): String {
        val stamp = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        return "${context.getString(R.string.generic_file)} - $stamp"
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.forEach { uri ->
            val displayName = FileUtils.getDisplayName(context, uri) ?: autoName()
            val saved = FileUtils.copyUriToInternalStorage(context, uri, displayName)
            viewModel.addFileEntry(folder.id, EntryType.IMAGE, saved.absolutePath, displayName)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val displayName = FileUtils.getDisplayName(context, it) ?: autoName()
            val saved = FileUtils.copyUriToInternalStorage(context, it, displayName)
            viewModel.addFileEntry(folder.id, EntryType.FILE, saved.absolutePath, displayName)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            val pdf = FileUtils.imageFileToPdf(context, file)
            viewModel.addFileEntry(folder.id, EntryType.PDF_SCAN, pdf.absolutePath, pdf.name)
        }
    }

    fun launchCameraCapture() {
        val file = FileUtils.createImageCaptureFile(context)
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCameraCapture() }

    fun onScanDocumentClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count_format, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val toDelete = entries.filter { it.id in selectedIds }
                            viewModel.moveEntriesToTrash(toDelete)
                            exitSelectionMode()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(folder.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            gridColumns = AppPreferences.cycleGridColumns(context)
                        }) {
                            Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.change_grid_size))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_content))
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_image)) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = { showAddMenu = false; imagePicker.launch("image/*") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_file)) },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            onClick = { showAddMenu = false; filePicker.launch("*/*") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_document)) },
                            leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                            onClick = { showAddMenu = false; onScanDocumentClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_label)) },
                            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                            onClick = { showAddMenu = false; showNoteDialog = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_entries_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val imageHeight = when (gridColumns) {
                GridColumns.ONE -> 260.dp
                GridColumns.TWO -> 170.dp
                GridColumns.THREE -> 110.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns.count),
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 8.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryGridCard(
                        entry = entry,
                        imageHeight = imageHeight,
                        selectionMode = selectionMode,
                        isSelected = entry.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (entry.id in selectedIds) {
                                    selectedIds - entry.id
                                } else {
                                    selectedIds + entry.id
                                }
                                if (selectedIds.isEmpty()) selectionMode = false
                            } else {
                                when (entry.type) {
                                    EntryType.TEXT -> viewingTextEntry = entry
                                    EntryType.IMAGE -> viewingImageEntry = entry
                                    EntryType.FILE, EntryType.PDF_SCAN -> openEntryExternally(context, entry)
                                }
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(entry.id)
                            }
                        },
                        onTogglePin = { viewModel.togglePin(entry) },
                        onToggleFavorite = { viewModel.toggleFavorite(entry) },
                        onDelete = { viewModel.moveToTrash(entry) },
                        onRename = { newName -> viewModel.renameEntry(entry, newName) }
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(stringResource(R.string.note_label)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTextEntry(folder.id, text)
                    showNoteDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    viewingImageEntry?.let { entry ->
        ImageViewerDialog(entry = entry, onDismiss = { viewingImageEntry = null })
    }

    viewingTextEntry?.let { entry ->
        TextViewerDialog(entry = entry, onDismiss = { viewingTextEntry = null })
    }
}

/** Opens a FILE/PDF_SCAN entry in whatever external app the device has for its type. */
private fun openEntryExternally(context: android.content.Context, entry: Entry) {
    val file = File(entry.content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.no_app_to_open_file), Toast.LENGTH_LONG).show()
        }
}

/** Full-screen image viewer with pinch-to-zoom/pan — this is what makes tapping a photo "enlarge" it. */
@Composable
private fun ImageViewerDialog(entry: Entry, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(entry.content),
                contentDescription = entry.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }
        }
    }
}

/** Full-text viewer for TEXT entries — the grid card only shows a truncated 6-line preview. */
@Composable
private fun TextViewerDialog(entry: Entry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                Text(entry.content, style = TextStyle(textDirection = TextDirection.Content))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) }
        }
    )
}
