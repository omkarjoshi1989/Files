package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/**
 * Persists the last played audio folder and file path so the File Explorer
 * toolbar can offer a "Resume Music" quick-access button.
 */
object MusicResumeManager {

    private const val PREFS_NAME = "music_resume"
    private const val KEY_FOLDER_PATH = "last_folder_path"
    private const val KEY_FILE_PATH = "last_file_path"

    /** Call this whenever a new audio track starts playing. */
    fun saveLastPlayed(context: Context, folderPath: String, filePath: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_PATH, folderPath)
            .putString(KEY_FILE_PATH, filePath)
            .apply()
    }

    fun getLastFolderPath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_PATH, null)

    fun getLastFilePath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FILE_PATH, null)

    /** Returns true when both a folder and file path have been saved. */
    fun hasLastPlayed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_FOLDER_PATH) && prefs.contains(KEY_FILE_PATH)
    }

    /**
     * Builds a ready-to-use [MediaItem] from the last persisted file path, or null
     * if no track has been played yet / the file no longer exists.
     */
    fun getLastFileAsMediaItem(context: Context): MediaItem? {
        val filePath = getLastFilePath(context) ?: return null
        val file = File(filePath)
        if (!file.exists()) return null
        return MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(file.nameWithoutExtension)
                    .setArtist("Files App")
                    .build()
            )
            .build()
    }
}

