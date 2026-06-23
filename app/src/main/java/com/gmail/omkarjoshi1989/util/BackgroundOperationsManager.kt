package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundOperationState {
    ONGOING,
    COMPLETED,
    FAILED
}

data class BackgroundOperationItem(
    val id: String,
    val title: String,
    val detail: String,
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val current: Int,
    val total: Int,
    val state: BackgroundOperationState,
    val updatedAtMs: Long
)

object BackgroundOperationsManager {

    private const val MAX_ITEMS = 20
    private const val PREFS = "background_operations"
    private const val KEY_ITEMS = "items"
    private const val FIELD_SEP = "\u0001"
    private const val ITEM_SEP = "\u0002"

    @Volatile
    private var appContext: Context? = null

    private val _operations = MutableStateFlow<List<BackgroundOperationItem>>(emptyList())
    val operations: StateFlow<List<BackgroundOperationItem>> = _operations.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        _operations.value = loadPersisted()
    }

    fun start(
        id: String,
        title: String,
        detail: String,
        total: Int,
        fileName: String = "",
        sourcePath: String = "",
        destinationPath: String = ""
    ) {
        upsert(
            BackgroundOperationItem(
                id = id,
                title = title,
                detail = detail,
                fileName = fileName,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                current = 0,
                total = total.coerceAtLeast(0),
                state = BackgroundOperationState.ONGOING,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun progress(
        id: String,
        title: String,
        detail: String,
        current: Int,
        total: Int,
        fileName: String = "",
        sourcePath: String = "",
        destinationPath: String = ""
    ) {
        upsert(
            BackgroundOperationItem(
                id = id,
                title = title,
                detail = detail,
                fileName = fileName,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                current = current.coerceAtLeast(0),
                total = total.coerceAtLeast(0),
                state = BackgroundOperationState.ONGOING,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun complete(
        id: String,
        title: String,
        detail: String,
        current: Int,
        total: Int,
        fileName: String = "",
        sourcePath: String = "",
        destinationPath: String = ""
    ) {
        upsert(
            BackgroundOperationItem(
                id = id,
                title = title,
                detail = detail,
                fileName = fileName,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                current = current.coerceAtLeast(0),
                total = total.coerceAtLeast(0),
                state = BackgroundOperationState.COMPLETED,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun fail(
        id: String,
        title: String,
        detail: String,
        current: Int,
        total: Int,
        fileName: String = "",
        sourcePath: String = "",
        destinationPath: String = ""
    ) {
        upsert(
            BackgroundOperationItem(
                id = id,
                title = title,
                detail = detail,
                fileName = fileName,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                current = current.coerceAtLeast(0),
                total = total.coerceAtLeast(0),
                state = BackgroundOperationState.FAILED,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun clearFinished() {
        _operations.value = _operations.value.filter { it.state == BackgroundOperationState.ONGOING }
        persist()
    }

    private fun upsert(item: BackgroundOperationItem) {
        val next = _operations.value
            .filterNot { it.id == item.id }
            .plus(item)
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_ITEMS)
        _operations.value = next
        persist()
    }

    private fun persist() {
        val ctx = appContext ?: return
        val encoded = _operations.value.joinToString(ITEM_SEP) { item ->
            listOf(
                item.id,
                item.title,
                item.detail,
                item.fileName,
                item.sourcePath,
                item.destinationPath,
                item.current.toString(),
                item.total.toString(),
                item.state.name,
                item.updatedAtMs.toString()
            ).joinToString(FIELD_SEP)
        }
        val payload = Base64.encodeToString(encoded.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, payload)
            .apply()
    }

    private fun loadPersisted(): List<BackgroundOperationItem> {
        val ctx = appContext ?: return emptyList()
        val encoded = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null)
            ?: return emptyList()
        val raw = runCatching {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { return emptyList() }

        return raw.split(ITEM_SEP)
            .asSequence()
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEP)
                if (parts.size < 7) return@mapNotNull null
                val hasPathFields = parts.size >= 10
                val stateIdx = if (hasPathFields) 8 else 5
                val updatedIdx = if (hasPathFields) 9 else 6
                val state = runCatching { BackgroundOperationState.valueOf(parts[stateIdx]) }.getOrNull() ?: return@mapNotNull null
                BackgroundOperationItem(
                    id = parts[0],
                    title = parts[1],
                    detail = parts[2],
                    fileName = if (hasPathFields) parts[3] else parts[2],
                    sourcePath = if (hasPathFields) parts[4] else "",
                    destinationPath = if (hasPathFields) parts[5] else "",
                    current = if (hasPathFields) parts[6].toIntOrNull() ?: 0 else parts[3].toIntOrNull() ?: 0,
                    total = if (hasPathFields) parts[7].toIntOrNull() ?: 0 else parts[4].toIntOrNull() ?: 0,
                    state = state,
                    updatedAtMs = parts[updatedIdx].toLongOrNull() ?: 0L
                )
            }
            .sortedByDescending { it.updatedAtMs }
            .take(MAX_ITEMS)
            .toList()
    }
}

