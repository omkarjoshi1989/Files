package com.gmail.omkarjoshi1989.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File

@Composable
fun FileThumbnail(
    file: File,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val extension = file.extension.lowercase()

    when {
        file.isDirectory -> {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                modifier = modifier.size(size),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        extension in imageExtensions -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .size(120)
                    .build(),
                contentDescription = file.name,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }

        extension in videoExtensions -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .size(120)
                    .build(),
                contentDescription = file.name,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }

        else -> {
            val (icon, tint) = getFileIconAndTint(extension)
            Icon(
                imageVector = icon,
                contentDescription = "File",
                modifier = modifier.size(size),
                tint = tint
            )
        }
    }
}

@Composable
private fun getFileIconAndTint(extension: String): Pair<ImageVector, Color> {
    return when (extension) {
        "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE53935)
        "doc", "docx", "odt", "rtf" -> Icons.Filled.Description to Color(0xFF1E88E5)
        "xls", "xlsx", "ods", "csv" -> Icons.Filled.TableChart to Color(0xFF43A047)
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> Icons.Filled.AudioFile to Color(0xFFFF9800)
        "apk" -> Icons.Filled.Android to Color(0xFF4CAF50)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico"
)

private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
)

