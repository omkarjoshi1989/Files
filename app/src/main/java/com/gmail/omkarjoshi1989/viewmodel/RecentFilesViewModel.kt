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
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalFilesScanned: Int = 0
)

class RecentFilesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecentFilesUiState())
    val uiState: StateFlow<RecentFilesUiState> = _uiState.asStateFlow()

    init {
        loadRecentFiles()
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(Environment.getExternalStorageDirectory())
                }
                _uiState.value = _uiState.value.copy(
                    files = files,
                    isLoading = false,
                    totalFilesScanned = files.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    files = emptyList()
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
                _uiState.value = _uiState.value.copy(
                    files = files,
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

