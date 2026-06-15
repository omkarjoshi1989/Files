package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

object SettingsManager {

    private const val PREFS_NAME = "app_settings"
    private const val PREFS_FOLDER_SORT = "folder_sort_settings"
    private const val KEY_MASTER_PASSWORD_ENABLED = "master_password_enabled"
    private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BACKGROUND_PLAYBACK = "video_background_playback"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getFolderSortPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FOLDER_SORT, Context.MODE_PRIVATE)
    }

    fun isMasterPasswordEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MASTER_PASSWORD_ENABLED, true)
    }

    fun setMasterPasswordEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_PASSWORD_ENABLED, enabled).apply()
    }

    fun isShowHiddenFiles(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_HIDDEN_FILES, false)
    }

    fun setShowHiddenFiles(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_HIDDEN_FILES, show).apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val name = getPrefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isBackgroundPlaybackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_PLAYBACK, false)
    }

    fun setBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
    }

    /**
     * Saves the sorting preference for a specific folder path.
     * @param folderPath Absolute path of the folder
     * @param sortOption Sorting option (e.g., "NAME", "DATE", "SIZE", "TYPE")
     * @param ascending Whether sorting is ascending (true) or descending (false)
     */
    fun saveFolderSort(context: Context, folderPath: String, sortOption: String, ascending: Boolean) {
        val prefs = getFolderSortPrefs(context)
        prefs.edit()
            .putString("${folderPath}_option", sortOption)
            .putBoolean("${folderPath}_ascending", ascending)
            .apply()
    }

    /**
     * Retrieves the sorting preference for a specific folder path.
     * @param folderPath Absolute path of the folder
     * @return Pair of (sortOption, ascending), or null if no preference is saved (defaults to NAME/ascending)
     */
    fun getFolderSort(context: Context, folderPath: String): Pair<String, Boolean>? {
        val prefs = getFolderSortPrefs(context)
        val option = prefs.getString("${folderPath}_option", null)
        return if (option != null) {
            val ascending = prefs.getBoolean("${folderPath}_ascending", true)
            Pair(option, ascending)
        } else {
            null // No preference saved, will use default (NAME, ascending)
        }
    }

    /**
     * Saves the view-mode preference (LIST or GRID) for a specific folder path.
     * @param folderPath Absolute path of the folder
     * @param viewMode   "LIST" or "GRID"
     */
    fun saveFolderViewMode(context: Context, folderPath: String, viewMode: String) {
        getFolderSortPrefs(context).edit()
            .putString("${folderPath}_view_mode", viewMode)
            .apply()
    }

    /**
     * Retrieves the view-mode preference for a specific folder path.
     * @param folderPath Absolute path of the folder
     * @return "LIST" or "GRID"; defaults to "LIST" if no preference is saved.
     */
    fun getFolderViewMode(context: Context, folderPath: String): String {
        return getFolderSortPrefs(context).getString("${folderPath}_view_mode", "LIST") ?: "LIST"
    }
}
