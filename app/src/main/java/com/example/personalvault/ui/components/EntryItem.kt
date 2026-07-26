package com.example.personalvault.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.personalvault.R
import com.example.personalvault.data.Entry
import com.example.personalvault.data.EntryType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    var showMenu by remember { mutableStateOf(false) }
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
                                if (entry.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.pin_label)
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
