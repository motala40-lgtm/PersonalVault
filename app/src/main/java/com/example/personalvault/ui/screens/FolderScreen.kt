package com.example.personalvault.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.viewinterop.AndroidView
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
    // BUG FIX: entriesForFolder(id) calls .stateIn(...) internally, which starts a *new*
    // coroutine and a *new* StateFlow every time it's called. Without `remember`, every
    // recomposition called it again — creating StateFlow after StateFlow in an endless
    // loop, which is what made icons look "frozen and blinking" after any DB update (like
    // saving a note). Memoizing on folder.id means it's only created once per folder.
    val entries by remember(folder.id) { viewModel.entriesForFolder(folder.id) }.collectAsState()
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableStateOf(AppPreferences.getGridColumns(context)) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var viewingImageIndex by remember { mutableStateOf<Int?>(null) }
    var viewingVideoEntry by remember { mutableStateOf<Entry?>(null) }
    var viewingTextEntry by remember { mutableStateOf<Entry?>(null) }
    // Only the images, in the same order they appear in the grid — this is what lets the
    // full-screen viewer swipe through "the folder's photos" rather than every entry type.
    val imageEntries = remember(entries) { entries.filter { it.type == EntryType.IMAGE } }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun autoName(): String {
        val stamp = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        return "${context.getString(R.string.generic_file)} - $stamp"
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val displayName = FileUtils.getDisplayName(context, uri) ?: autoName()
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val saved = FileUtils.copyUriToInternalStorage(context, uri, displayName)
            val type = if (mimeType.startsWith("video/")) EntryType.VIDEO else EntryType.IMAGE
            viewModel.addFileEntry(folder.id, type, saved.absolutePath, displayName)
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
                            onClick = {
                                showAddMenu = false
                                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
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
                                    EntryType.IMAGE -> {
                                        val idx = imageEntries.indexOfFirst { it.id == entry.id }
                                        if (idx >= 0) viewingImageIndex = idx
                                    }
                                    EntryType.VIDEO -> viewingVideoEntry = entry
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

    viewingImageIndex?.let { idx ->
        ImageViewerDialog(
            images = imageEntries,
            initialIndex = idx,
            onDismiss = { viewingImageIndex = null }
        )
    }

    viewingVideoEntry?.let { entry ->
        VideoViewerDialog(entry = entry, onDismiss = { viewingVideoEntry = null })
    }

    viewingTextEntry?.let { entry ->
        TextViewerDialog(
            entry = entry,
            onSave = { newText -> viewModel.updateTextEntry(entry, newText) },
            onDismiss = { viewingTextEntry = null }
        )
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

/** Full-screen gallery viewer for a folder's photos — swipe left/right between them, pinch to
 *  zoom/pan the current one. Only ever shown the IMAGE entries, so swiping skips over notes,
 *  files, and videos in between. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ImageViewerDialog(images: List<Entry>, initialIndex: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }
                val entry = images[page]

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
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }
            if (images.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

/** Full-screen in-app video player — uses the platform's VideoView/MediaController rather
 *  than a new player dependency, since it needs no extra setup to just play a local file. */
@Composable
private fun VideoViewerDialog(entry: Entry, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(entry.content)
                        val controller = android.widget.MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { start() }
                    }
                },
                modifier = Modifier.fillMaxSize()
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

/** Opens read-only by default (a clean, keyboard-free view for actually reading the note) —
 *  tapping the edit icon switches to an editable field. Save/Cancel return to read mode. */
@Composable
private fun TextViewerDialog(entry: Entry, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(entry.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            if (isEditing) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(textDirection = TextDirection.Content)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text, style = TextStyle(textDirection = TextDirection.Content))
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(onClick = {
                    onSave(text)
                    isEditing = false
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            } else {
                TextButton(onClick = { isEditing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_note))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isEditing) {
                    // Discard in-progress edits and go back to the read-only view instead of
                    // closing outright — Cancel should undo the edit, not exit the note.
                    text = entry.content
                    isEditing = false
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(if (isEditing) R.string.cancel else R.string.back)) }
        }
    )
}
