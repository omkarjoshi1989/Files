package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/**
 * Persists the ONE most-recently-played audio track (folder path, file path, and
 * playback position) so the player can resume exactly where the user left off.
 *
 * Only a single "most recent" entry is ever kept.  As soon as a different song
 * starts playing that entry is replaced — the previous song's progress is gone.
 */
object MusicResumeManager {

    private const val PREFS_NAME = "music_resume"
    private const val KEY_FOLDER_PATH = "last_folder_path"
    private const val KEY_FILE_PATH = "last_file_path"
    /** Playback position in milliseconds for the most-recently-played song. */
    private const val KEY_POSITION_MS = "last_position_ms"

    /**
     * Call this whenever a new (or the same) audio track becomes the active track.
     * [positionMs] should be 0 when a brand-new song starts; supply the current
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
     * Update only the playback position for the currently-saved most-recent song.
     * Cheaper than [saveLastPlayed] when the file identity hasn't changed.
     */
    fun savePosition(context: Context, positionMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
            .apply()
    }

    /**
     * Called by [MusicPlaybackService] when a media-item transition occurs in the
     * background player.  Updates the folder/file identity of the most-recent song
     * and resets the saved position to 0 **only** when the file actually changes
     * (i.e. a genuinely new song started).  If the same file is already persisted
     * (e.g. a fresh playlist-load to the same track for resume), the saved position
     * is left untouched so the resume offset is not clobbered.
     */
    fun saveLastPlayedFile(context: Context, folderPath: String, filePath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingFilePath = prefs.getString(KEY_FILE_PATH, null)
        val editor = prefs.edit()
            .putString(KEY_FOLDER_PATH, folderPath)
            .putString(KEY_FILE_PATH, filePath)
        if (existingFilePath != filePath) {
            // Different song → position resets to 0
            editor.putLong(KEY_POSITION_MS, 0L)
        }
        // Same file → leave KEY_POSITION_MS intact (preserve resume offset)
        editor.apply()
    }

    fun getLastFolderPath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_PATH, null)

    fun getLastFilePath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FILE_PATH, null)

    /** Returns the saved playback position (ms) for the most-recently-played song, or 0. */
    fun getLastPositionMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_POSITION_MS, 0L)

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

