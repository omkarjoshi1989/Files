package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr"
    )

    fun isMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in imageExtensions || ext in videoExtensions || ext in audioExtensions
    }

    fun isImageFile(file: File): Boolean = file.extension.lowercase() in imageExtensions
    fun isVideoFile(file: File): Boolean = file.extension.lowercase() in videoExtensions
    fun isAudioFile(file: File): Boolean = file.extension.lowercase() in audioExtensions

    fun getMediaFilesInFolder(folder: File): List<File> {
        return folder.listFiles()
            ?.filter { it.isFile && isMediaFile(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return mime ?: "application/octet-stream"
    }

    fun getOpenFileIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Open with")
    }
}

