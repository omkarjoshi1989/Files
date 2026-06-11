package com.gmail.omkarjoshi1989.ui.components

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.Parameters
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

// ── Pre-fetched-metadata overload (zero stat() on main thread) ───────────────

/**
 * Preferred overload used by [FileListItem].
 *
 * All metadata ([isDirectory], [extension], [lastModified]) is supplied by the
 * caller from a pre-fetched [FileItem] — no filesystem calls are made on the
 * main/composition thread.
 */
@Composable
fun FileThumbnail(
    absolutePath: String,
    isDirectory: Boolean,
    extension: String,      // lowercase, empty for directories
    lastModified: Long,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    FileThumbnailImpl(
        absolutePath = absolutePath,
        isDirectory  = isDirectory,
        extension    = extension,
        lastModified = lastModified,
        size         = size,
        modifier     = modifier
    )
}

// ── Legacy File-based overload (backward compat for other screens) ────────────

/**
 * Backward-compatible overload used by [FavoritesScreen], [AudioFoldersScreen]
 * and [GlobalSearchScreen] where a raw [File] is available but no [FileItem].
 *
 * NOTE: This overload calls [File.isDirectory] and [File.lastModified] inside
 * Compose `remember` blocks, which issues stat() system-calls.  It is acceptable
 * for screens that show small lists, but **do not use it** in the main file
 * explorer list — use the pre-fetched overload above instead.
 */
@Composable
fun FileThumbnail(
    file: File,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val extension    = remember(file.absolutePath) { file.extension.lowercase() }
    val isDirectory  = remember(file.absolutePath) { file.isDirectory }
    val lastModified = remember(file.absolutePath) { file.lastModified() }
    FileThumbnailImpl(
        absolutePath = file.absolutePath,
        isDirectory  = isDirectory,
        extension    = extension,
        lastModified = lastModified,
        size         = size,
        modifier     = modifier
    )
}

// ── Shared implementation ─────────────────────────────────────────────────────

@Composable
private fun FileThumbnailImpl(
    absolutePath: String,
    isDirectory: Boolean,
    extension: String,
    lastModified: Long,
    size: Dp,
    modifier: Modifier
) {
    when {
        isDirectory -> {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                modifier = modifier.size(size),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        extension in imageExtensions -> {
            val placeholder = remember { ColorPainter(Color(0xFFBDBDBD)) }
            val context = LocalContext.current
            val imageRequest = remember(absolutePath, lastModified) {
                ImageRequest.Builder(context)
                    .data(File(absolutePath))
                    .size(120)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey("img:$absolutePath:$lastModified")
                    .diskCacheKey("img:$absolutePath:$lastModified")
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                placeholder = placeholder,
                error = placeholder,
                contentDescription = null,
                modifier = modifier.size(size).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }

        extension in audioExtensions -> {
            val context = LocalContext.current
            val placeholder = remember { ColorPainter(Color(0xFFFF9800)) }
            val albumArt by produceState<ByteArray?>(
                initialValue = null,
                key1 = absolutePath,
                key2 = lastModified
            ) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(absolutePath)
                        val art = retriever.embeddedPicture
                        retriever.release()
                        art
                    } catch (_: Exception) { null }
                }
            }

            if (albumArt != null) {
                val imageRequest = remember(absolutePath, lastModified) {
                    ImageRequest.Builder(context)
                        .data(albumArt)
                        .size(120)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("audio:$absolutePath:$lastModified")
                        .diskCacheKey("audio:$absolutePath:$lastModified")
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    placeholder = placeholder,
                    error = placeholder,
                    contentDescription = null,
                    modifier = modifier.size(size).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val bgColor = remember(extension) { getExtensionColor(extension) }
                Box(
                    modifier = modifier.size(size).clip(RoundedCornerShape(6.dp)).background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.6f)
                    )
                }
            }
        }

        extension in videoExtensions -> {
            val placeholder = remember { ColorPainter(Color(0xFF757575)) }
            val context = LocalContext.current
            val videoRequest = remember(absolutePath, lastModified) {
                ImageRequest.Builder(context)
                    .data(File(absolutePath))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .size(120)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .parameters(
                        Parameters.Builder()
                            .set(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, 0L)
                            .build()
                    )
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey("vid:$absolutePath:$lastModified")
                    .diskCacheKey("vid:$absolutePath:$lastModified")
                    .build()
            }
            AsyncImage(
                model = videoRequest,
                placeholder = placeholder,
                error = placeholder,
                contentDescription = null,
                modifier = modifier.size(size).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }

        else -> {
            val displayExt = remember(extension) {
                if (extension.isNotEmpty()) extension.uppercase() else "FILE"
            }
            val bgColor = remember(extension) { getExtensionColor(extension) }
            Box(
                modifier = modifier.size(size).clip(RoundedCornerShape(6.dp)).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayExt,
                    color = Color.White,
                    fontSize = if (displayExt.length <= 3) 11.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun getExtensionColor(extension: String): Color {
    return when (extension) {
        "pdf" -> Color(0xFFE53935)
        "doc", "docx", "odt", "rtf" -> Color(0xFF1E88E5)
        "xls", "xlsx", "ods", "csv" -> Color(0xFF43A047)
        "ppt", "pptx", "odp" -> Color(0xFFFF7043)
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> Color(0xFFFF9800)
        "apk" -> Color(0xFF4CAF50)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8D6E63)
        "txt", "log", "md" -> Color(0xFF78909C)
        "json", "xml", "html", "css", "js", "ts", "kt", "java", "py" -> Color(0xFF5C6BC0)
        "sql", "db", "sqlite" -> Color(0xFF26A69A)
        else -> {
            val hash = extension.hashCode().absoluteValue
            val hueOptions = listOf(
                Color(0xFF7E57C2), Color(0xFF26C6DA), Color(0xFFEC407A),
                Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFF42A5F5)
            )
            hueOptions[hash % hueOptions.size]
        }
    }
}

private val audioExtensions = setOf(
    "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "aiff", "alac"
)
private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico"
)
private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
)
