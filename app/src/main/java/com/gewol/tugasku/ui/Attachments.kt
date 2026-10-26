package com.gewol.tugasku.ui

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.gewol.tugasku.R
import com.gewol.tugasku.isLinkAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

fun getFileName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
    }
    if (name.isNullOrBlank()) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
}

fun importFile(context: Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    val originalName = getFileName(context, uri)
    val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val file = File(dir, "${System.currentTimeMillis()}_$safeName")
    val input = context.contentResolver.openInputStream(uri)
    if (input == null) {
        null
    } else {
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        file.absolutePath
    }
} catch (e: Exception) {
    null
}

fun importPhoto(context: Context, uri: Uri): String? {
    val path = importFile(context, uri)
    if (path != null) return path
    return try {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { src ->
            file.outputStream().use { dst -> src.copyTo(dst) }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun isImageAttachment(path: String): Boolean {
    if (isLinkAttachment(path)) return false
    val name = File(path).name.lowercase()
    val ext = File(path).extension.lowercase()
    return ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") || name.startsWith("img_")
}

fun getDisplayName(path: String): String {
    if (isLinkAttachment(path)) {
        return try {
            Uri.parse(normalizeLink(path)).host ?: path
        } catch (e: Exception) {
            path
        }
    }
    val name = File(path).name
    return if (name.contains('_')) {
        val prefix = name.substringBefore('_')
        if (prefix.all { it.isDigit() } || prefix == "img") {
            name.substringAfter('_')
        } else {
            name
        }
    } else {
        name
    }
}

fun getFileSizeString(path: String): String {
    val file = File(path)
    if (!file.exists() || !file.isFile) return ""
    val bytes = file.length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun getMimeType(path: String): String {
    val ext = File(path).extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            else -> "*/*"
        }
}

fun openFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val mime = getMimeType(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.file_open_no_app), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.file_open_error), Toast.LENGTH_SHORT).show()
    }
}

fun deleteAttachmentFile(path: String) {
    runCatching { File(path).delete() }
}

fun deletePhotoFile(path: String) = deleteAttachmentFile(path)

fun normalizeLink(input: String): String {
    val t = input.trim()
    return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
}

fun openLink(context: Context, url: String) {
    try {
        val target = normalizeLink(url)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.link_open_error), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.link_open_error), Toast.LENGTH_SHORT).show()
    }
}

private fun decodeSampled(path: String, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}

@Composable
fun PhotoImage(
    path: String,
    modifier: Modifier = Modifier,
    maxSide: Int = 400,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxSide) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, maxSide) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.cd_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } ?: Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PhotoDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            PhotoImage(
                path = path,
                modifier = Modifier.fillMaxSize(),
                maxSide = 2048,
                contentScale = ContentScale.Fit
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) { Text(stringResource(R.string.close), color = Color.White) }
        }
    }
}

@Composable
fun FileTypeBadge(extension: String, modifier: Modifier = Modifier) {
    val ext = extension.lowercase()
    val (bg, fg, label) = when (ext) {
        "pdf" -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "PDF")
        "doc", "docx" -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), "DOC")
        "xls", "xlsx", "csv" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "XLS")
        "ppt", "pptx" -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), "PPT")
        "zip", "rar", "7z", "tar", "gz" -> Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), "ZIP")
        "txt" -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), "TXT")
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ext.uppercase().take(4).ifBlank { "FILE" }
        )
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LinkAttachmentCard(
    link: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
            ) {
                Text(
                    text = getDisplayName(link),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.open_link),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun FileAttachmentCard(
    path: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = File(path)
    val ext = file.extension
    val displayName = getDisplayName(path)
    val sizeText = getFileSizeString(path)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
        ) {
            FileTypeBadge(extension = ext)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sizeText.isNotBlank()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.open_file),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
