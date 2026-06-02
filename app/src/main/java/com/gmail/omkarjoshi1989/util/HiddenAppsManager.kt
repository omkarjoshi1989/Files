package com.gmail.omkarjoshi1989.util

import android.content.Context

object HiddenAppsManager {

    private const val PREFS_NAME = "hidden_apps_prefs"
    private const val KEY_HIDDEN_PACKAGES = "hidden_packages"

    fun getHiddenApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()
    }

    fun hideApp(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, current).apply()
    }

    fun unhideApp(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, current).apply()
    }

    fun isHidden(context: Context, packageName: String): Boolean {
        return getHiddenApps(context).contains(packageName)
    }
}

