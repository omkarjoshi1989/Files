package com.gmail.omkarjoshi1989.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.SmbRemoteItem
import com.gmail.omkarjoshi1989.util.SmbMediaThumbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable
fun SmbRemoteThumbnail(
    connection: SmbConnectionConfig,
    shareName: String,
    item: SmbRemoteItem,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val extension = remember(item.name) { item.name.substringAfterLast('.', "").lowercase() }

    when {
        item.isDirectory -> {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                modifier = modifier.size(size),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        extension in imageExtensions -> {
            val context = LocalContext.current
            val placeholder = remember { ColorPainter(Color(0xFFBDBDBD)) }
            val imageThumb by produceState<ByteArray?>(
                initialValue = null,
                connection.id,
                shareName,
                item.path,
                item.lastModified,
                item.size
            ) {
                value = withContext(Dispatchers.IO) {
                    SmbMediaThumbRepository.getImageThumbnail(
                        context = context,
                        connection = connection,
                        shareName = shareName,
                        remotePath = item.path,
                        fileSize = item.size,
                        lastModified = item.lastModified
                    )
                }
            }

            if (imageThumb != null) {
                val imageRequest = remember(connection.id, shareName, item.path, item.lastModified, item.size, imageThumb) {
                    ImageRequest.Builder(context)
                        .data(imageThumb)
                        .size(120)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("smb_img:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
                        .diskCacheKey("smb_img:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
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
                val displayExt = remember(extension) { if (extension.isNotBlank()) extension.uppercase() else "FILE" }
                val bgColor = remember(extension) { extensionColor(extension) }
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
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }

        extension in videoExtensions -> {
            val context = LocalContext.current
            val placeholder = remember { ColorPainter(Color(0xFF757575)) }
            val videoThumb by produceState<ByteArray?>(
                initialValue = null,
                connection.id,
                shareName,
                item.path,
                item.lastModified,
                item.size
            ) {
                value = withContext(Dispatchers.IO) {
                    SmbMediaThumbRepository.getVideoThumbnail(
                        context = context,
                        connection = connection,
                        shareName = shareName,
                        remotePath = item.path,
                        fileSize = item.size,
                        lastModified = item.lastModified
                    )
                }
            }

            if (videoThumb != null) {
                val imageRequest = remember(connection.id, shareName, item.path, item.lastModified, item.size, videoThumb) {
                    ImageRequest.Builder(context)
                        .data(videoThumb)
                        .size(120)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("smb_vid:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
                        .diskCacheKey("smb_vid:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
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
                Box(
                    modifier = modifier
                        .size(size)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF455A64)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.62f)
                    )
                }
            }
        }

        extension in audioExtensions -> {
            val context = LocalContext.current
            val placeholder = remember { ColorPainter(Color(0xFFFF9800)) }
            val albumArt by produceState<ByteArray?>(
                initialValue = null,
                connection.id,
                shareName,
                item.path,
                item.lastModified,
                item.size
            ) {
                value = withContext(Dispatchers.IO) {
                    SmbMediaThumbRepository.getAudioAlbumArt(
                        context = context,
                        connection = connection,
                        shareName = shareName,
                        remotePath = item.path,
                        fileSize = item.size,
                        lastModified = item.lastModified
                    )
                }
            }

            if (albumArt != null) {
                val imageRequest = remember(connection.id, shareName, item.path, item.lastModified, item.size, albumArt) {
                    ImageRequest.Builder(context)
                        .data(albumArt)
                        .size(120)
                        .precision(Precision.INEXACT)
                        .allowHardware(false)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("smb_audio:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
                        .diskCacheKey("smb_audio:${connection.id}:$shareName:${item.path}:${item.lastModified}:${item.size}")
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
                Box(
                    modifier = modifier
                        .size(size)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF9800)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.62f)
                    )
                }
            }
        }

        else -> {
            val displayExt = remember(extension) { if (extension.isNotBlank()) extension.uppercase() else "FILE" }
            val bgColor = remember(extension) { extensionColor(extension) }
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
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun extensionColor(extension: String): Color {
    return when (extension) {
        "pdf" -> Color(0xFFE53935)
        "apk" -> Color(0xFF4CAF50)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8D6E63)
        "txt", "log", "md" -> Color(0xFF78909C)
        else -> {
            val hash = extension.hashCode().absoluteValue
            val options = listOf(
                Color(0xFF7E57C2),
                Color(0xFF26C6DA),
                Color(0xFFEC407A),
                Color(0xFF66BB6A),
                Color(0xFFAB47BC),
                Color(0xFF42A5F5)
            )
            options[hash % options.size]
        }
    }
}

private val audioExtensions = setOf(
    "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "aiff", "alac"
)

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
)

private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
)

