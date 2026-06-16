package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.net.Uri
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SmbStreamRegistry {

    data class StreamEntry(
        val connection: SmbConnectionConfig,
        val shareName: String,
        val remotePath: String,
        val displayName: String,
        val createdAtMs: Long
    )

    private const val PREFIX = "smb_stream_"
    private val entries = ConcurrentHashMap<String, StreamEntry>()

    fun registerVideoStream(
        context: Context,
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        displayName: String
    ): File {
        val token = UUID.randomUUID().toString()
        entries[token] = StreamEntry(
            connection = connection,
            shareName = shareName,
            remotePath = remotePath,
            displayName = displayName,
            createdAtMs = System.currentTimeMillis()
        )

        val rawExt = displayName.substringAfterLast('.', "")
            .trim()
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
        val ext = rawExt ?: "mp4"
        val baseName = displayName.substringBeforeLast('.', displayName)
        val safeBase = baseName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(48)
            .ifBlank { "video" }
        val markerFile = File(context.cacheDir, "${PREFIX}${token}_${safeBase}.$ext")
        if (!markerFile.exists()) {
            markerFile.writeText("SMB_STREAM_TOKEN=$token")
        }

        evictOldEntries(maxAgeMs = 6 * 60 * 60 * 1000L)
        return markerFile
    }

    fun findByUri(uri: Uri): StreamEntry? {
        val token = extractToken(uri) ?: return null
        return entries[token]
    }

    private fun extractToken(uri: Uri): String? {
        val name = uri.lastPathSegment ?: return null
        if (!name.startsWith(PREFIX)) return null
        val rest = name.removePrefix(PREFIX)
        val token = rest.substringBefore('_').trim()
        return token.takeIf { it.isNotBlank() }
    }

    private fun evictOldEntries(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        entries.entries.removeIf { now - it.value.createdAtMs > maxAgeMs }
    }
}

