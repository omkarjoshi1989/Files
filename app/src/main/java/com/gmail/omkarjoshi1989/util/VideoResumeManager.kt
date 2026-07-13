package com.gmail.omkarjoshi1989.util

import android.content.Context
import java.io.File

/**
 * Persists the ONE most-recently-played video (folder path, file path, and
 * playback position) so the player can resume exactly where the user left off.
 */
object VideoResumeManager {

    private const val PREFS_NAME = "video_resume"
    private const val KEY_FOLDER_PATH = "last_folder_path"
    private const val KEY_FILE_PATH = "last_file_path"
    /** Playback position in milliseconds for the most-recently-played video. */
    private const val KEY_POSITION_MS = "last_position_ms"

    /**
     * Call this whenever a new (or the same) video becomes the active track.
     * [positionMs] should be 0 when a brand-new video starts; supply the current
     * position when re-saving an ongoing playback (e.g. on app pause).
     */
    fun saveLastPlayed(
        context: Context,
        folderPath: String,
        filePath: String,
        positionMs: Long = 0L
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_PATH, folderPath)
            .putString(KEY_FILE_PATH, filePath)
            .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
            .apply()
    }

    /**
     * Update only the playback position for the currently-saved most-recent video.
     */
    fun savePosition(context: Context, positionMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun getLastFolderPath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_PATH, null)

    fun getLastFilePath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FILE_PATH, null)

    /** Returns the saved playback position (ms) for the most-recently-played video, or 0. */
    fun getLastPositionMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_POSITION_MS, 0L)

    /** Returns true when both a folder and file path have been saved. */
    fun hasLastPlayed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_FOLDER_PATH) && prefs.contains(KEY_FILE_PATH)
    }
}
