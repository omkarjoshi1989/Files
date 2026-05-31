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
    private const val KEY_MASTER_PASSWORD_ENABLED = "master_password_enabled"
    private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
    private const val KEY_THEME_MODE = "theme_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
}
