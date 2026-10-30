package com.chengying.clearview.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.chengying.clearview.core.designsystem.theme.ClearViewTokens
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ThumbnailState(val loading: Boolean = true, val bitmap: Bitmap? = null)

@Composable
private fun rememberThumbnail(path: String?, reloadKey: Boolean): ThumbnailState {
    val context = LocalContext.current
    val state = remember(context, path, reloadKey) { mutableStateOf(ThumbnailState()) }
    LaunchedEffect(context, path, reloadKey) {
        val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(context, path) }
        state.value = ThumbnailState(loading = false, bitmap = bitmap)
    }
    return state.value
}

@Composable
fun DiagnosisThumbnail(
    path: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    reloadKey: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    containerColor: Color = ClearViewTokens.colors.imageCanvas,
) {
    val thumbnail = rememberThumbnail(path, reloadKey)
    Box(modifier.background(containerColor), contentAlignment = Alignment.Center) {
        thumbnail.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Text(
            text = if (thumbnail.loading) "读取影像中" else "影像不可用",
            style = ClearViewTokens.typography.bodySmall,
            color = ClearViewTokens.colors.imageText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(ClearViewTokens.spacing.xs),
        )
    }
}

private fun decodeThumbnail(context: Context, path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val uri = Uri.parse(path)
        fun openSource(): InputStream? = when (uri.scheme) {
            null -> File(path).inputStream()
            "file" -> uri.path?.let { File(it).inputStream() }
            "content" -> context.contentResolver.openInputStream(uri)
            else -> null // Never fetch a remote or substitute image for a local record.
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        openSource()?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
