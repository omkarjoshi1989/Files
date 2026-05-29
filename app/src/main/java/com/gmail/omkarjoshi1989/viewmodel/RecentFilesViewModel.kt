package com.gmail.omkarjoshi1989.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RecentFilesUiState(
    val files: List<File> = emptyList(),
    val filteredFiles: List<File> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalFilesScanned: Int = 0,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

class RecentFilesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecentFilesUiState())
    val uiState: StateFlow<RecentFilesUiState> = _uiState.asStateFlow()

    init {
        loadRecentFiles()
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value
        val filtered = if (query.isBlank()) {
            current.files
        } else {
            current.files.filter { it.name.contains(query, ignoreCase = true) }
        }
        _uiState.value = current.copy(
            searchQuery = query,
            filteredFiles = filtered
        )
    }

    fun toggleSearch() {
        val current = _uiState.value
        if (current.isSearchActive) {
            // Close search → clear query and reset filter
            _uiState.value = current.copy(
                isSearchActive = false,
                searchQuery = "",
                filteredFiles = current.files
            )
        } else {
            _uiState.value = current.copy(isSearchActive = true)
        }
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(Environment.getExternalStorageDirectory())
                }
                val query = _uiState.value.searchQuery
                val filtered = if (query.isBlank()) files
                else files.filter { it.name.contains(query, ignoreCase = true) }
                _uiState.value = _uiState.value.copy(
                    files = files,
                    filteredFiles = filtered,
                    isLoading = false,
                    totalFilesScanned = files.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    files = emptyList(),
                    filteredFiles = emptyList()
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(Environment.getExternalStorageDirectory())
                }
                val query = _uiState.value.searchQuery
                val filtered = if (query.isBlank()) files
                else files.filter { it.name.contains(query, ignoreCase = true) }
                _uiState.value = _uiState.value.copy(
                    files = files,
                    filteredFiles = filtered,
                    isRefreshing = false,
                    totalFilesScanned = files.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private fun scanAllFiles(root: File): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    // Skip Android system directories to avoid slowdown
                    val name = child.name
                    if (name != "Android" || current == root) {
                        // Allow Android/ at root level but skip nested system dirs
                        if (name != ".thumbnails" && name != ".cache") {
                            stack.addLast(child)
                        }
                    }
                } else {
                    result.add(child)
                }
            }
        }

        // Sort by last modified descending (most recent first)
        result.sortByDescending { it.lastModified() }
        return result
    }
}

