package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
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

    fun isZipFile(file: File): Boolean = file.extension.lowercase() == "zip"

    fun isPdfFile(file: File): Boolean = file.extension.lowercase() == "pdf"

    /** Returns true if the file is an image or video (visual media). */
    fun isVisualMediaFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in imageExtensions || ext in videoExtensions
    }

    fun getMediaFilesInFolder(context: Context, folder: File): List<File> {
        val showHidden = SettingsManager.isShowHiddenFiles(context)
        return folder.listFiles()
            ?.filter { it.isFile && isMediaFile(it) && (showHidden || !it.isHidden) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun getAudioFilesInFolder(context: Context, folder: File): List<File> {
        val showHidden = SettingsManager.isShowHiddenFiles(context)
        return folder.listFiles()
            ?.filter { it.isFile && isAudioFile(it) && (showHidden || !it.isHidden) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun getImageFilesInFolder(context: Context, folder: File): List<File> {
        val showHidden = SettingsManager.isShowHiddenFiles(context)
        return folder.listFiles()
            ?.filter { it.isFile && isImageFile(it) && (showHidden || !it.isHidden) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun getVideoFilesInFolder(context: Context, folder: File): List<File> {
        val showHidden = SettingsManager.isShowHiddenFiles(context)
        return folder.listFiles()
            ?.filter { it.isFile && isVideoFile(it) && (showHidden || !it.isHidden) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /**
     * Returns files from [folder] that belong to the same media group as [referenceFile].
     * Images and videos are each kept in their own group so that opening an image
     * only allows swiping through other images (not videos) and vice-versa.
     * Audio files are grouped separately.
     * Falls back to all media files if the reference file type is unknown.
     */
    fun getFilesOfSameType(context: Context, folder: File, referenceFile: File): List<File> {
        return when {
            isImageFile(referenceFile) -> getImageFilesInFolder(context, folder)
            isVideoFile(referenceFile) -> getVideoFilesInFolder(context, folder)
            isAudioFile(referenceFile) -> getAudioFilesInFolder(context, folder)
            else -> getMediaFilesInFolder(context, folder)
        }
    }

    /**
     * Converts an absolute file-system path to a human-readable display path by
     * stripping the internal-storage root ("/storage/emulated/0") so the user
     * sees e.g. "Pictures/Instagram" instead of "/storage/emulated/0/Pictures/Instagram".
     *
     * If [path] is exactly the internal-storage root (or empty after stripping),
     * returns "Internal Storage".
     */
    fun toDisplayPath(path: String): String {
        val internalRoot = "/storage/emulated/0"
        val stripped = if (path.startsWith(internalRoot)) {
            path.removePrefix(internalRoot).trimStart('/')
        } else {
            path.trimStart('/')
        }
        return stripped.ifEmpty { "Internal Storage" }
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

    /**
     * Returns an intent chooser that can be used to share a single file with other apps.
     * The file is exposed via FileProvider and a read permission flag is granted on the chooser.
     */
    fun getShareFileIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share file")
    }

    /**
     * Strips leading numeric prefixes (e.g. "01 ", "01. ", "1 - ", "02 03 ")
     * from audio file names so they display cleanly in the notification and UI.
     */
    fun stripNumericPrefix(name: String): String =
        name.replace(Regex("^(\\d+[\\s._\\-]*)+"), "").trim()

    /**
     * Resolves a [Uri] (content:// or file://) received from an external ACTION_VIEW intent
     * into a [File] pointing to the actual file on disk.
     *
     * For content:// URIs the MediaStore DATA column is queried first; if absent the
     * file is copied to the app's cache dir so the existing viewers can open it.
     *
     * Returns null if the URI cannot be resolved.
     */
    fun resolveUriToFile(context: Context, uri: Uri): File? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it) }
            "content" -> {
                // Try the MediaStore real-path column first (works for most local files)
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) {
                                val path = cursor.getString(idx)
                                if (!path.isNullOrBlank()) return File(path)
                            }
                        }
                        null
                    }
                } catch (_: Exception) { null }

                // Fallback: copy to cache so ExoPlayer / Coil can read it
                ?: run {
                    try {
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val ext = MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mimeType) ?: "tmp"
                        val displayName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "file"
                        val cacheFile = File(context.cacheDir, "open_${displayName.take(40)}.$ext")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        cacheFile
                    } catch (_: Exception) { null }
                }
            }
            else -> null
        }
    }
}
