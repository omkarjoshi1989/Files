package com.gmail.omkarjoshi1989.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Immutable snapshot of a single directory entry with all display-relevant metadata
 * pre-fetched on a background IO thread.
 *
 * WHY THIS EXISTS
 * ───────────────
 * Java's [File] API is lazy: every call to [File.isDirectory], [File.length] and
 * [File.lastModified] triggers a kernel stat() system-call.  For a folder with
 * 1 000+ entries (e.g. DCIM/Camera with 7 GB of content), calling these methods
 * during Compose recomposition or inside a sort comparator causes:
 *
 *   • 1 000+ stat() calls on the main thread while the LazyColumn scrolls → jank / ANR
 *   • 1 000+ stat() calls inside sortedWith() on Dispatchers.Default → blocking sort
 *
 * By pre-fetching all attributes exactly once on the IO thread (when the directory
 * is first scanned) and storing them as plain fields here, we get:
 *
 *   • Sorting = pure in-memory comparisons, zero IO
 *   • LazyColumn rendering = pure field reads, zero IO
 *   • Cache hits = absolutely instant (all data already in memory)
 *
 * API LEVEL NOTES
 * ───────────────
 * On API 26+ (Android 8+), we use [java.nio.file.Files.readAttributes] which
 * issues a single stat() per file instead of three separate calls.
 * On API 24–25 we fall back to three separate [File] calls.
 */
data class FileItem(
    /** Raw [File] reference — use only for I/O operations (open, copy, delete, etc.). */
    val file: File,
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    /**
     * File size in bytes.
     * For directories this is **-1** — we do not recurse just for a display size.
     * The `FileListItem` computes the folder item-count/size lazily via [produceState].
     */
    val size: Long,
    val lastModified: Long,
    /** Lower-case file extension, empty string for directories and extension-less files. */
    val extension: String,
    val isHidden: Boolean
) {
    companion object {
        /**
         * Reads all metadata from [file] and returns a [FileItem].
         *
         * **Must be called from a background/IO thread** — this performs filesystem I/O.
         *
         * On API 26+ uses NIO [BasicFileAttributes] to read all attributes in a
         * single stat() call (3× cheaper than the API 24–25 fallback).
         */
        fun fromFile(file: File): FileItem {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fromFileNio(file)
            } else {
                fromFileLegacy(file)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun fromFileNio(file: File): FileItem {
            return try {
                val attrs = java.nio.file.Files.readAttributes(
                    file.toPath(),
                    java.nio.file.attribute.BasicFileAttributes::class.java
                )
                val isDir = attrs.isDirectory
                FileItem(
                    file         = file,
                    name         = file.name,
                    absolutePath = file.absolutePath,
                    isDirectory  = isDir,
                    size         = if (isDir) -1L else attrs.size(),
                    lastModified = attrs.lastModifiedTime().toMillis(),
                    extension    = if (isDir) "" else file.extension.lowercase(),
                    isHidden     = file.name.startsWith(".")
                )
            } catch (_: Exception) {
                // NIO can fail for certain paths (e.g. proc, sys) – fall back gracefully
                fromFileLegacy(file)
            }
        }

        private fun fromFileLegacy(file: File): FileItem {
            val isDir = file.isDirectory          // stat() #1
            return FileItem(
                file         = file,
                name         = file.name,
                absolutePath = file.absolutePath,
                isDirectory  = isDir,
                size         = if (isDir) -1L else file.length(),  // stat() #2 (files only)
                lastModified = file.lastModified(),                  // stat() #3
                extension    = if (isDir) "" else file.extension.lowercase(),
                isHidden     = file.name.startsWith(".")
            )
        }
    }
}

