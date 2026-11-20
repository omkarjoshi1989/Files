package com.gmail.omkarjoshi1989.util

import android.content.Context

object HiddenGalleryFoldersManager {

    private const val PREFS_NAME = "hidden_gallery_folders_prefs"
    private const val KEY_HIDDEN_FOLDERS = "hidden_folder_paths"

    fun getHiddenFolders(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN_FOLDERS, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun hideFolders(context: Context, folderPaths: Collection<String>) {
        val current = getHiddenFolders(context).toMutableSet()
        current.addAll(folderPaths)
        save(context, current)
    }

    fun unhideFolders(context: Context, folderPaths: Collection<String>) {
        val current = getHiddenFolders(context).toMutableSet()
        current.removeAll(folderPaths.toSet())
        save(context, current)
    }

    private fun save(context: Context, folderPaths: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_HIDDEN_FOLDERS, folderPaths)
            .apply()
    }
}


