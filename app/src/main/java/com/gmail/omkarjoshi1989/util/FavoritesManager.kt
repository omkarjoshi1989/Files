package com.gmail.omkarjoshi1989.util

import android.content.Context

object FavoritesManager {

    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_paths"

    fun getFavorites(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(context: Context, path: String): Boolean = path in getFavorites(context)

    /**
     * Toggles the favorite status of [path].
     * @return `true` if the file is now a favorite, `false` if it was removed.
     */
    fun toggleFavorite(context: Context, path: String): Boolean {
        val current = getFavorites(context).toMutableSet()
        return if (current.contains(path)) {
            current.remove(path)
            save(context, current)
            false
        } else {
            current.add(path)
            save(context, current)
            true
        }
    }

    fun removeFavorite(context: Context, path: String) {
        val current = getFavorites(context).toMutableSet()
        current.remove(path)
        save(context, current)
    }

    private fun save(context: Context, paths: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_FAVORITES, paths).apply()
    }
}

