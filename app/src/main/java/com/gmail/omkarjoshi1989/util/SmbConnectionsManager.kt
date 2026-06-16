package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.util.Base64
import com.gmail.omkarjoshi1989.model.SmbAuthMode
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import java.util.UUID

object SmbConnectionsManager {

    private const val PREFS = "smb_connections_prefs"
    private const val KEY_IDS = "connection_ids"
    private const val REC_PREFIX = "connection_"
    private const val SEP = "\u0001"

    fun getConnections(context: Context): List<SmbConnectionConfig> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (prefs.getString(KEY_IDS, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ids.mapNotNull { id ->
            val encoded = prefs.getString("$REC_PREFIX$id", null) ?: return@mapNotNull null
            decode(id, encoded)
        }
    }

    fun getConnection(context: Context, id: String): SmbConnectionConfig? {
        return getConnections(context).firstOrNull { it.id == id }
    }

    fun saveConnection(context: Context, config: SmbConnectionConfig): SmbConnectionConfig {
        val normalized = config.copy(
            id = config.id.ifBlank { UUID.randomUUID().toString() },
            host = config.host.trim(),
            displayName = config.displayName.trim(),
            defaultShareName = config.defaultShareName.trim(),
            username = config.username.trim(),
            domain = config.domain.trim()
        )

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (prefs.getString(KEY_IDS, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (!ids.contains(normalized.id)) ids.add(0, normalized.id)

        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .putString("$REC_PREFIX${normalized.id}", encode(normalized))
            .apply()

        return normalized
    }

    fun deleteConnection(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (prefs.getString(KEY_IDS, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != id }

        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .remove("$REC_PREFIX$id")
            .apply()
    }

    fun markConnectedNow(context: Context, id: String) {
        val current = getConnection(context, id) ?: return
        saveConnection(context, current.copy(lastConnectedAt = System.currentTimeMillis()))
    }

    private fun encode(config: SmbConnectionConfig): String {
        val raw = listOf(
            config.displayName,
            config.host,
            config.port.toString(),
            config.defaultShareName,
            config.authMode.name,
            config.username,
            config.password,
            config.domain,
            config.lastConnectedAt.toString()
        ).joinToString(SEP)

        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decode(id: String, encoded: String): SmbConnectionConfig? {
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val raw = String(bytes, Charsets.UTF_8)
            val parts = raw.split(SEP)
            if (parts.size < 9) return null

            SmbConnectionConfig(
                id = id,
                displayName = parts[0],
                host = parts[1],
                port = parts[2].toIntOrNull() ?: 445,
                defaultShareName = parts[3],
                authMode = runCatching { SmbAuthMode.valueOf(parts[4]) }.getOrDefault(SmbAuthMode.GUEST),
                username = parts[5],
                password = parts[6],
                domain = parts[7],
                lastConnectedAt = parts[8].toLongOrNull() ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }
}

