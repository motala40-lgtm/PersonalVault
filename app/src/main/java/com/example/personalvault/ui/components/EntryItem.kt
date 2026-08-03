package com.example.personalvault.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.personalvault.R
import com.example.personalvault.data.Entry
import com.example.personalvault.data.EntryType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Row-style entry, kept for screens with a simple flat list (Favorites, Trash, search results).
 */
@Composable
fun EntryItem(
    entry: Entry,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    inTrash: Boolean = false,
    onRestore: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val timeString = remember(entry.createdAt) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(entry.createdAt))
    }
    val genericFileText = stringResource(R.string.generic_file)
    val shareText = stringResource(R.string.share)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            when (entry.type) {
                EntryType.TEXT -> {
                    Text(
                        text = entry.content,
                        style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Content)
                    )
                }
                EntryType.IMAGE -> {
                    AsyncImage(
                        model = File(entry.content),
                        contentDescription = entry.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                EntryType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                EntryType.FILE, EntryType.PDF_SCAN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (entry.type == EntryType.PDF_SCAN) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(entry.fileName ?: genericFileText)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(timeString, style = MaterialTheme.typography.labelSmall)

                Row {
                    if (inTrash) {
                        IconButton(onClick = { onRestore?.invoke() }) {
                            Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore))
                        }
                        IconButton(onClick = { onDeletePermanently?.invoke() }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_permanently))
                        }
                    } else {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = stringResource(R.string.pin_label),
                                tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(R.string.favorite_label),
                                tint = if (entry.isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                        if (entry.type != EntryType.TEXT) {
                            IconButton(onClick = {
                                shareEntry(context, entry, shareText)
                            }) {
                                Icon(Icons.Default.Share, contentDescription = shareText)
                            }
                            IconButton(onClick = {
                                saveEntryToDeviceWithFeedback(context, entry)
                            }) {
                                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.save_to_device))
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern grid-style card used by FolderScreen: big centered thumbnail, three-dot menu,
 * file name + date footer, and a selection checkbox overlay for multi-select mode.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EntryGridCard(
    entry: Entry,
    imageHeight: androidx.compose.ui.unit.Dp,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val timeString = remember(entry.createdAt) {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(entry.createdAt))
    }
    val genericFileText = stringResource(R.string.generic_file)
    val shareText = stringResource(R.string.share)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                ) {
                    when (entry.type) {
                        EntryType.TEXT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = entry.content,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Content)
                                )
                            }
                        }
                        EntryType.IMAGE -> {
                            AsyncImage(
                                model = File(entry.content),
                                contentDescription = entry.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        EntryType.VIDEO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        EntryType.FILE, EntryType.PDF_SCAN -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (entry.type == EntryType.PDF_SCAN) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    // Three-dot menu, top-right of the thumbnail.
                    if (!selectionMode) {
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
                                    text = { Text(stringResource(R.string.pin_label)) },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                    onClick = { menuExpanded = false; onTogglePin() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorite_label)) },
                                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                                    onClick = { menuExpanded = false; onToggleFavorite() }
                                )
                                if (entry.type != EntryType.TEXT) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename)) },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = { menuExpanded = false; showRenameDialog = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(shareText) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = { menuExpanded = false; shareEntry(context, entry, shareText) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.save_to_device)) },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick = { menuExpanded = false; saveEntryToDeviceWithFeedback(context, entry) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { menuExpanded = false; onDelete() }
                                )
                            }
                        }
                    }

                    // Selection checkbox, top-left, only shown once selection mode is active.
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }

                    if (entry.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.pin_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                }

                Column(Modifier.padding(10.dp)) {
                    if (entry.type != EntryType.TEXT) {
                        Text(
                            text = entry.fileName ?: genericFileText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(timeString, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(entry.fileName ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(newName)
                    showRenameDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun shareEntry(context: android.content.Context, entry: Entry, shareChooserTitle: String) {
    val file = File(entry.content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
}

/** Runs the actual file copy on a plain background thread (no ViewModel/coroutine scope is
 *  available at this level) and reports success/failure with a Toast on the main thread. */
private fun saveEntryToDeviceWithFeedback(context: android.content.Context, entry: Entry) {
    val successMsg = context.getString(R.string.save_to_device_success)
    val failMsg = context.getString(R.string.save_to_device_failed)
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
        val success = com.example.personalvault.util.FileUtils.exportEntryToDevice(context, entry)
        mainHandler.post {
            Toast.makeText(context, if (success) successMsg else failMsg, Toast.LENGTH_LONG).show()
        }
    }.start()
}
