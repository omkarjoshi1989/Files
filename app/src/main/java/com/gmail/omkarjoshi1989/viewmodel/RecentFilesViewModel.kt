package com.gmail.omkarjoshi1989.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmail.omkarjoshi1989.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortOption(val label: String) {
    DATE("Date"),
    NAME("Name"),
    SIZE("Size"),
    TYPE("Type"),
    PATH("Path")
}

enum class FileFilter(val label: String) {
    ALL("All Files"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    IMAGES_AND_VIDEOS("Images & Videos"),
    AUDIO("Audio"),
    PDF("PDF")
}

data class RecentFilesUiState(
    val files: List<File> = emptyList(),
    val filteredFiles: List<File> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalFilesScanned: Int = 0,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: SortOption = SortOption.DATE,
    val sortAscending: Boolean = false,
    val fileFilter: FileFilter = FileFilter.ALL,
    val operationMessage: String? = null,
    val errorMessage: String? = null
)

class RecentFilesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RecentFilesUiState())
    val uiState: StateFlow<RecentFilesUiState> = _uiState.asStateFlow()

    init {
        loadRecentFiles()
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value
        val filtered = applyFilter(current.files, current.fileFilter)
            .let { if (query.isBlank()) it else it.filter { f -> f.name.contains(query, ignoreCase = true) } }
        _uiState.value = current.copy(
            searchQuery = query,
            filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending)
        )
    }

    fun toggleSearch() {
        val current = _uiState.value
        if (current.isSearchActive) {
            // Close search → clear query and reset filter
            val filtered = applyFilter(current.files, current.fileFilter)
            _uiState.value = current.copy(
                isSearchActive = false,
                searchQuery = "",
                filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending)
            )
        } else {
            _uiState.value = current.copy(isSearchActive = true)
        }
    }

    fun setSortOption(option: SortOption) {
        val current = _uiState.value
        val newAscending = if (current.sortOption == option) !current.sortAscending else {
            // Default ascending for name/path/type, descending for date/size
            when (option) {
                SortOption.NAME, SortOption.PATH, SortOption.TYPE -> true
                SortOption.DATE, SortOption.SIZE -> false
            }
        }
        _uiState.value = current.copy(
            sortOption = option,
            sortAscending = newAscending,
            filteredFiles = applySorting(current.filteredFiles, option, newAscending)
        )
    }

    fun setFileFilter(filter: FileFilter) {
        val current = _uiState.value
        val filtered = applyFilter(current.files, filter)
            .let { if (current.searchQuery.isBlank()) it else it.filter { f -> f.name.contains(current.searchQuery, ignoreCase = true) } }
        _uiState.value = current.copy(
            fileFilter = filter,
            filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending)
        )
    }

    fun clearOperationMessage() {
        _uiState.value = _uiState.value.copy(operationMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val newFile = File(file.parent, newName)
                    if (newFile.exists()) throw IllegalStateException("A file named '$newName' already exists")
                    if (!file.renameTo(newFile)) throw IllegalStateException("Rename failed")
                }
                _uiState.value = _uiState.value.copy(operationMessage = "Renamed to: $newName")
                refreshAndKeepState(newName)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Rename failed: ${e.message}")
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (!success) throw IllegalStateException("Delete failed")
                }
                _uiState.value = _uiState.value.copy(operationMessage = "Deleted: ${file.name}")
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Delete failed: ${e.message}")
            }
        }
    }

    private fun applyFilter(files: List<File>, filter: FileFilter): List<File> = when (filter) {
        FileFilter.ALL -> files
        FileFilter.IMAGES -> files.filter { FileUtils.isImageFile(it) }
        FileFilter.VIDEOS -> files.filter { FileUtils.isVideoFile(it) }
        FileFilter.IMAGES_AND_VIDEOS -> files.filter { FileUtils.isVisualMediaFile(it) }
        FileFilter.AUDIO -> files.filter { FileUtils.isAudioFile(it) }
        FileFilter.PDF -> files.filter { FileUtils.isPdfFile(it) }
    }

    private fun applySorting(files: List<File>, sortOption: SortOption, ascending: Boolean): List<File> {
        val sorted = when (sortOption) {
            SortOption.NAME -> files.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> files.sortedBy { it.length() }
            SortOption.DATE -> files.sortedBy { it.lastModified() }
            SortOption.TYPE -> files.sortedBy { it.extension.lowercase() }
            SortOption.PATH -> files.sortedBy { it.absolutePath.lowercase() }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(Environment.getExternalStorageDirectory())
                }
                val current = _uiState.value
                val filtered = applyFilter(files, current.fileFilter)
                    .let { if (current.searchQuery.isBlank()) it else it.filter { f -> f.name.contains(current.searchQuery, ignoreCase = true) } }
                _uiState.value = current.copy(
                    files = files,
                    filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending),
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
                val current = _uiState.value
                val filtered = applyFilter(files, current.fileFilter)
                    .let { if (current.searchQuery.isBlank()) it else it.filter { f -> f.name.contains(current.searchQuery, ignoreCase = true) } }
                _uiState.value = current.copy(
                    files = files,
                    filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending),
                    isRefreshing = false,
                    totalFilesScanned = files.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private fun refreshAndKeepState(renamedTo: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val files = withContext(Dispatchers.IO) {
                    scanAllFiles(Environment.getExternalStorageDirectory())
                }
                val current = _uiState.value
                val filtered = applyFilter(files, current.fileFilter)
                    .let { if (current.searchQuery.isBlank()) it else it.filter { f -> f.name.contains(current.searchQuery, ignoreCase = true) } }
                _uiState.value = current.copy(
                    files = files,
                    filteredFiles = applySorting(filtered, current.sortOption, current.sortAscending),
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

        // Sort by last modified descending (most recent first) as default
        result.sortByDescending { it.lastModified() }
        return result
    }
}
