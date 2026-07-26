package com.example.personalvault.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.personalvault.R
import com.example.personalvault.data.EntryType
import com.example.personalvault.data.Folder
import com.example.personalvault.ui.components.EntryItem
import com.example.personalvault.util.FileUtils
import com.example.personalvault.viewmodel.VaultViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folder: Folder,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entriesForFolder(folder.id).collectAsState()
    var text by remember { mutableStateOf("") }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val saved = FileUtils.copyUriToInternalStorage(context, it, "image.jpg")
            viewModel.addFileEntry(folder.id, EntryType.IMAGE, saved.absolutePath, saved.name)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment ?: "file"
            val saved = FileUtils.copyUriToInternalStorage(context, it, name)
            viewModel.addFileEntry(folder.id, EntryType.FILE, saved.absolutePath, name)
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

    // CAMERA is a dangerous permission and must be requested at runtime; without this check,
    // tapping "scan document" on a device that hasn't granted it yet would silently fail.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCameraCapture() }

    fun onScanDocumentClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = stringResource(R.string.attach_image))
                        }
                        IconButton(onClick = { filePicker.launch("*/*") }) {
                            Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.attach_file))
                        }
                        IconButton(onClick = { onScanDocumentClick() }) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = stringResource(R.string.scan_document))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.note_placeholder)) },
                            maxLines = 4
                        )
                        IconButton(onClick = {
                            viewModel.addTextEntry(folder.id, text)
                            text = ""
                        }) {
                            Icon(Icons.Default.Send, contentDescription = stringResource(R.string.send))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 8.dp)
                .fillMaxSize()
        ) {
            items(entries, key = { it.id }) { entry ->
                EntryItem(
                    entry = entry,
                    onTogglePin = { viewModel.togglePin(entry) },
                    onToggleFavorite = { viewModel.toggleFavorite(entry) },
                    onDelete = { viewModel.moveToTrash(entry) }
                )
            }
        }
    }
}
