package com.gmail.omkarjoshi1989.util

import android.content.Context

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
}

