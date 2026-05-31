package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID

data class RecycleBinItem(
    val id: String,
    val originalPath: String,
    val name: String,
    val deletedAt: Long,
    val isDirectory: Boolean,
    val size: Long
)

object RecycleBinManager {

    private const val PREFS_NAME = "recycle_bin_prefs"
    private const val KEY_IDS = "item_ids"

    // Use a separator unlikely to appear in file paths
    private const val SEP = "\u0001"

    /** Returns the hidden recycle-bin folder on external storage. */
    fun getRecycleBinDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), ".RecycleBin")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Moves [file] (file or directory) to the recycle bin.
     * Returns true on success.
     */
    fun moveToRecycleBin(context: Context, file: File): Boolean {
        return try {
            val id = UUID.randomUUID().toString()
            val binDir = getRecycleBinDir()
            val dest = File(binDir, id)

            val isDir = file.isDirectory
            val success: Boolean = if (isDir) {
                file.copyRecursively(dest, overwrite = true) && file.deleteRecursively()
            } else {
                file.copyTo(dest, overwrite = true)
                file.delete()
            }

            if (success) {
                val size = if (isDir) calculateSize(dest) else dest.length()
                val item = RecycleBinItem(
                    id = id,
                    originalPath = file.absolutePath,
                    name = file.name,
                    deletedAt = System.currentTimeMillis(),
                    isDirectory = isDir,
                    size = size
                )
                saveItem(context, item)
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    /** Returns all items currently in the recycle bin, newest first. */
    fun listItems(context: Context): List<RecycleBinItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = (prefs.getString(KEY_IDS, "") ?: "").split(",").filter { it.isNotBlank() }
        return ids.mapNotNull { id ->
            val raw = prefs.getString("item_$id", null) ?: return@mapNotNull null
            parseItem(id, raw)
        }.sortedByDescending { it.deletedAt }
    }

    /**
     * Restores [item] to its original location.
     * Returns true on success; false if the file already exists at the destination
     * or the source is missing.
     */
    fun restoreItem(context: Context, item: RecycleBinItem): Boolean {
        return try {
            val src = File(getRecycleBinDir(), item.id)
            if (!src.exists()) {
                removeItem(context, item.id)
                return false
            }
            val dest = File(item.originalPath)
            if (dest.exists()) return false   // conflict – let caller report error

            dest.parentFile?.mkdirs()

            val success: Boolean = if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false) && src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = false)
                src.delete()
            }

            if (success) removeItem(context, item.id)
            success
        } catch (e: Exception) {
            false
        }
    }

    /** Permanently deletes [item] from the recycle bin. */
    fun permanentlyDelete(context: Context, item: RecycleBinItem): Boolean {
        return try {
            val file = File(getRecycleBinDir(), item.id)
            val deleted = if (file.isDirectory) file.deleteRecursively() else (file.delete() || !file.exists())
            removeItem(context, item.id)
            deleted
        } catch (e: Exception) {
            removeItem(context, item.id)
            false
        }
    }

    /** Permanently deletes every item in the recycle bin. */
    fun emptyRecycleBin(context: Context) {
        listItems(context).toList().forEach { permanentlyDelete(context, it) }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun saveItem(context: Context, item: RecycleBinItem) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = (prefs.getString(KEY_IDS, "") ?: "").let {
            if (it.isBlank()) item.id else "${item.id},$it"
        }
        val data = listOf(
            item.originalPath,
            item.name,
            item.deletedAt.toString(),
            item.isDirectory.toString(),
            item.size.toString()
        ).joinToString(SEP)

        prefs.edit()
            .putString(KEY_IDS, currentIds)
            .putString("item_${item.id}", data)
            .apply()
    }

    private fun removeItem(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = (prefs.getString(KEY_IDS, "") ?: "")
            .split(",").filter { it.isNotBlank() && it != id }
        prefs.edit()
            .putString(KEY_IDS, currentIds.joinToString(","))
            .remove("item_$id")
            .apply()
    }

    private fun parseItem(id: String, raw: String): RecycleBinItem? {
        return try {
            val parts = raw.split(SEP)
            RecycleBinItem(
                id = id,
                originalPath = parts[0],
                name = parts[1],
                deletedAt = parts[2].toLong(),
                isDirectory = parts[3].toBoolean(),
                size = parts[4].toLong()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) size += f.length() }
        return size
    }
}

