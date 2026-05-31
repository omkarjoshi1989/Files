package com.gmail.omkarjoshi1989.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmail.omkarjoshi1989.model.ZipEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class ZipUiState(
    val zipFile: File? = null,
    val currentPath: String = "", // "" means root of ZIP
    val entries: List<ZipEntryItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ZipViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ZipUiState())
    val uiState: StateFlow<ZipUiState> = _uiState.asStateFlow()

    private var allEntries: List<ZipEntryItem> = emptyList()

    fun loadZipFile(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(zipFile = file, isLoading = true, errorMessage = null)
            try {
                val items = withContext(Dispatchers.IO) {
                    ZipFile(file).use { zip ->
                        zip.entries().asSequence().map { entry ->
                            ZipEntryItem(
                                name = entry.name.trimEnd('/').substringAfterLast('/'),
                                fullPath = entry.name,
                                isDirectory = entry.isDirectory,
                                size = entry.size,
                                lastModified = entry.time
                            )
                        }.toList()
                    }
                }
                allEntries = items
                updateVisibleEntries("")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load ZIP: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun updateVisibleEntries(path: String) {
        val normalizedPath = if (path.isEmpty() || path.endsWith("/")) path else "$path/"
        
        // Find immediate children of normalizedPath
        val visibleItems = allEntries.filter { entry ->
            if (normalizedPath.isEmpty()) {
                !entry.fullPath.trimEnd('/').contains('/')
            } else {
                entry.fullPath.startsWith(normalizedPath) && 
                entry.fullPath != normalizedPath &&
                !entry.fullPath.removePrefix(normalizedPath).trimEnd('/').contains('/')
            }
        }.distinctBy { it.fullPath.trimEnd('/') }
         .sortedWith(compareByDescending<ZipEntryItem> { it.isDirectory }.thenBy { it.name.lowercase() })

        _uiState.value = _uiState.value.copy(currentPath = path, entries = visibleItems)
    }

    fun navigateTo(path: String) {
        updateVisibleEntries(path)
    }

    fun navigateUp(): Boolean {
        val current = _uiState.value.currentPath
        if (current.isEmpty()) return false
        
        val parent = current.trimEnd('/').substringBeforeLast('/', "")
        updateVisibleEntries(parent)
        return true
    }
}
