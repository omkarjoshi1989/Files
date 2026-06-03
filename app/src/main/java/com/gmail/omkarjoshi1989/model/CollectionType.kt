package com.gmail.omkarjoshi1989.model

import java.io.File

enum class CollectionType(val displayName: String) {
    MUSIC("Music"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    IMAGES_VIDEOS("Images & Videos"),
    PDF("PDF"),
    APPLICATIONS("Applications"),
    RECYCLE_BIN("Recycle Bin"),
    BOOKMARK("Bookmark")
}

private val MUSIC_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "amr", "mid", "midi", "3ga", "ape", "mka"
)
private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico", "tiff", "tif", "raw", "cr2", "nef"
)
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mts", "m2ts", "vob", "rmvb"
)
private val PDF_EXTENSIONS = setOf("pdf")

fun CollectionType.matchesFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return when (this) {
        CollectionType.MUSIC -> ext in MUSIC_EXTENSIONS
        CollectionType.IMAGES -> ext in IMAGE_EXTENSIONS
        CollectionType.VIDEOS -> ext in VIDEO_EXTENSIONS
        CollectionType.IMAGES_VIDEOS -> ext in IMAGE_EXTENSIONS || ext in VIDEO_EXTENSIONS
        CollectionType.PDF -> ext in PDF_EXTENSIONS
        CollectionType.APPLICATIONS, CollectionType.RECYCLE_BIN, CollectionType.BOOKMARK -> true
    }
}

/**
 * Returns true if [folder] (or any of its descendants) contains at least one file
 * that matches this collection type.
 *
 * For collection types where folder filtering is not applicable
 * (APPLICATIONS, RECYCLE_BIN, BOOKMARK) this always returns true.
 */
fun CollectionType.folderContainsMatchingFiles(folder: File): Boolean {
    if (this == CollectionType.APPLICATIONS ||
        this == CollectionType.RECYCLE_BIN ||
        this == CollectionType.BOOKMARK) return true
    val children = folder.listFiles() ?: return false
    for (child in children) {
        if (child.isFile && this.matchesFile(child)) return true
        if (child.isDirectory && this.folderContainsMatchingFiles(child)) return true
    }
    return false
}

