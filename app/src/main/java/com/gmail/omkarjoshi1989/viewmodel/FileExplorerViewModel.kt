package com.gmail.omkarjoshi1989.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmail.omkarjoshi1989.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ClipboardOperation { CUT, COPY }

data class ClipboardData(
    val file: File,
    val operation: ClipboardOperation
)

data class FileExplorerUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<File> = emptyList(),
    val showHiddenFiles: Boolean = false,
    val clipboard: ClipboardData? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val operationMessage: String? = null
)

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    private val _showHiddenFiles = MutableStateFlow(SettingsManager.isShowHiddenFiles(application))
    private val _clipboard = MutableStateFlow<ClipboardData?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _operationMessage = MutableStateFlow<String?>(null)
    private val _fileListTrigger = MutableStateFlow(0L) // bump to refresh

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "show_hidden_files") {
            _showHiddenFiles.value = SettingsManager.isShowHiddenFiles(application)
        }
    }

    init {
        application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>()
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    val uiState: StateFlow<FileExplorerUiState> = combine(
        _currentPath,
        _showHiddenFiles,
        _clipboard,
        combine(_isLoading, _isRefreshing) { l, r -> Pair(l, r) },
        combine(_errorMessage, _operationMessage, _fileListTrigger) { err, op, _ -> Triple(err, op, null) },
    ) { path, showHidden, clipboard, loadingStates, extras ->
        val (loading, refreshing) = loadingStates
        val (error, opMsg, _) = extras
        val dir = File(path)
        val files = loadFiles(dir, showHidden)
        FileExplorerUiState(
            currentPath = path,
            files = files,
            showHiddenFiles = showHidden,
            clipboard = clipboard,
            isLoading = loading,
            isRefreshing = refreshing,
            errorMessage = error,
            operationMessage = opMsg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileExplorerUiState())

    private fun loadFiles(directory: File, showHidden: Boolean): List<File> {
        val allFiles = directory.listFiles() ?: return emptyList()
        return allFiles
            .filter { showHidden || !it.isHidden }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun navigateTo(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            _currentPath.value = dir.absolutePath
            _errorMessage.value = null
        } else {
            _errorMessage.value = "Cannot access: $path"
        }
    }

    fun navigateUp(): Boolean {
        val current = File(_currentPath.value)
        val parent = current.parentFile
        return if (parent != null && parent.canRead()) {
            _currentPath.value = parent.absolutePath
            _errorMessage.value = null
            true
        } else {
            false
        }
    }


    fun cutFile(file: File) {
        _clipboard.value = ClipboardData(file, ClipboardOperation.CUT)
        _operationMessage.value = "Cut: ${file.name}"
    }

    fun copyFile(file: File) {
        _clipboard.value = ClipboardData(file, ClipboardOperation.COPY)
        _operationMessage.value = "Copied: ${file.name}"
    }

    fun paste() {
        val clipData = _clipboard.value ?: return
        val destination = File(_currentPath.value, clipData.file.name)

        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    if (clipData.file.absolutePath == destination.absolutePath) {
                        throw IllegalStateException("Source and destination are the same")
                    }
                    when (clipData.operation) {
                        ClipboardOperation.COPY -> {
                            if (clipData.file.isDirectory) {
                                clipData.file.copyRecursively(destination, overwrite = false)
                            } else {
                                clipData.file.copyTo(destination, overwrite = false)
                            }
                        }
                        ClipboardOperation.CUT -> {
                            if (clipData.file.isDirectory) {
                                clipData.file.copyRecursively(destination, overwrite = false)
                                clipData.file.deleteRecursively()
                            } else {
                                clipData.file.copyTo(destination, overwrite = false)
                                clipData.file.delete()
                            }
                        }
                    }
                }
                _clipboard.value = null
                _operationMessage.value = "Pasted: ${clipData.file.name}"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Paste failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val newFile = File(file.parent, newName)
                    if (newFile.exists()) {
                        throw IllegalStateException("A file with name '$newName' already exists")
                    }
                    if (!file.renameTo(newFile)) {
                        throw IllegalStateException("Rename failed")
                    }
                }
                _operationMessage.value = "Renamed to: $newName"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Rename failed: ${e.message}"
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val success = if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    if (!success) throw IllegalStateException("Delete failed")
                }
                _operationMessage.value = "Deleted: ${file.name}"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewFolder(name: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val newDir = File(_currentPath.value, name)
                    if (newDir.exists()) {
                        throw IllegalStateException("'$name' already exists")
                    }
                    if (!newDir.mkdir()) {
                        throw IllegalStateException("Failed to create folder")
                    }
                }
                _operationMessage.value = "Folder created: $name"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Create folder failed: ${e.message}"
            }
        }
    }

    fun createNewFile(name: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val newFile = File(_currentPath.value, name)
                    if (newFile.exists()) {
                        throw IllegalStateException("'$name' already exists")
                    }
                    if (!newFile.createNewFile()) {
                        throw IllegalStateException("Failed to create file")
                    }
                }
                _operationMessage.value = "File created: $name"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Create file failed: ${e.message}"
            }
        }
    }

    fun getOpenFileIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Open with")
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return mime ?: "application/octet-stream"
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun refreshFiles() {
        _fileListTrigger.value = System.currentTimeMillis()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.delay(300) // brief delay for visual feedback
            }
            refreshFiles()
            _isRefreshing.value = false
        }
    }

    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
