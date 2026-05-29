package com.gmail.omkarjoshi1989.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.request.ImageRequest
import java.io.File
import kotlin.math.absoluteValue

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
            // Show extension label in CAPITAL letters with a colored background
            val displayExt = if (extension.isNotEmpty()) extension.uppercase() else "FILE"
            val bgColor = getExtensionColor(extension)
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor),
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
            // Generate a stable color from extension hash
            val hash = extension.hashCode().absoluteValue
            val hueOptions = listOf(
                Color(0xFF7E57C2), Color(0xFF26C6DA), Color(0xFFEC407A),
                Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFF42A5F5)
            )
            hueOptions[hash % hueOptions.size]
        }
    }
}

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico"
)

private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
)

