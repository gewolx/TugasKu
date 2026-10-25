package com.gewol.tugasku.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import com.gewol.tugasku.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Salin foto pilihan pengguna ke penyimpanan internal aplikasi. Mengembalikan path file, atau null jika gagal. */
fun importPhoto(context: Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
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

fun deletePhotoFile(path: String) {
    runCatching { File(path).delete() }
}

/** Tambahkan "https://" jika pengguna menempel tautan tanpa skema. */
fun normalizeLink(input: String): String {
    val t = input.trim()
    return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
}

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
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

/** Tampilan foto layar penuh. */
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
