package com.gmail.omkarjoshi1989.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmail.omkarjoshi1989.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ClipboardOperation { CUT, COPY }

/** Holds one or many files queued for a cut/copy operation. */
data class ClipboardData(
    val files: List<File>,
    val operation: ClipboardOperation
)

private data class SelectionState(
    val isMode: Boolean = false,
    val paths: Set<String> = emptySet()
)

data class FileExplorerUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<File> = emptyList(),
    val showHiddenFiles: Boolean = false,
    val clipboard: ClipboardData? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val operationMessage: String? = null,
    val folderSizes: Map<String, Long> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet()
)

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    private val _showHiddenFiles = MutableStateFlow(SettingsManager.isShowHiddenFiles(application))
    private val _clipboard = MutableStateFlow<ClipboardData?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _operationMessage = MutableStateFlow<String?>(null)
    private val _fileListTrigger = MutableStateFlow(0L)
    private val _folderSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _selectionState = MutableStateFlow(SelectionState())

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "show_hidden_files") {
            _showHiddenFiles.value = SettingsManager.isShowHiddenFiles(application)
        }
    }

    // FileObserver to detect external changes (deletions, creations, renames)
    private var fileObserver: FileObserver? = null
    private var debounceJob: Job? = null

    private val observerMask = FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE

    @Suppress("DEPRECATION")
    private fun buildObserver(path: String): FileObserver =
        if (Build.VERSION.SDK_INT >= 29) {
            object : FileObserver(File(path), observerMask) {
                override fun onEvent(event: Int, fileName: String?) = onFsEvent()
            }
        } else {
            object : FileObserver(path, observerMask) {
                override fun onEvent(event: Int, fileName: String?) = onFsEvent()
            }
        }

    /** Called from the FileObserver thread; debounced to avoid rapid-fire refreshes. */
    private fun onFsEvent() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300)
            refreshFiles()
        }
    }

    private fun startWatching(path: String) {
        fileObserver?.stopWatching()
        fileObserver = buildObserver(path).also { it.startWatching() }
    }

    init {
        application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefListener)
        startWatching(_currentPath.value)
        viewModelScope.launch {
            _currentPath.collect { path -> startWatching(path) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
        fileObserver = null
        getApplication<Application>()
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    val uiState: StateFlow<FileExplorerUiState> = combine(
        _currentPath,
        _showHiddenFiles,
        _clipboard,
        combine(_isLoading, _isRefreshing, _selectionState) { l, r, sel -> Triple(l, r, sel) },
        combine(_errorMessage, _operationMessage, _fileListTrigger, _folderSizes) { err, op, _, sizes -> Triple(err, op, sizes) }
    ) { path, showHidden, clipboard, loadingSel, extras ->
        val (loading, refreshing, selState) = loadingSel
        val (error, opMsg, folderSizes) = extras
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
            operationMessage = opMsg,
            folderSizes = folderSizes,
            isSelectionMode = selState.isMode,
            selectedFilePaths = selState.paths
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileExplorerUiState())

    private fun loadFiles(directory: File, showHidden: Boolean): List<File> {
        val allFiles = directory.listFiles() ?: return emptyList()
        val filteredFiles = allFiles
            .filter { showHidden || !it.isHidden }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        filteredFiles.filter { it.isDirectory }.forEach { folder ->
            computeFolderSize(folder, showHidden)
        }
        return filteredFiles
    }

    private fun computeFolderSize(folder: File, showHidden: Boolean) {
        val folderPath = folder.absolutePath
        if (_folderSizes.value.containsKey(folderPath)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val size = calculateDirectorySize(folder, showHidden)
                _folderSizes.update { it + (folderPath to size) }
            } catch (_: Exception) { }
        }
    }

    private fun calculateDirectorySize(directory: File, showHidden: Boolean): Long {
        var size = 0L
        val files = directory.listFiles() ?: return 0L
        for (file in files) {
            if (!showHidden && file.isHidden) continue
            size += if (file.isDirectory) calculateDirectorySize(file, showHidden) else file.length()
        }
        return size
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun navigateTo(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            clearSelection()
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
            clearSelection()
            _currentPath.value = parent.absolutePath
            _errorMessage.value = null
            true
        } else {
            false
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun enterSelectionMode(file: File) {
        _selectionState.value = SelectionState(isMode = true, paths = setOf(file.absolutePath))
    }

    fun toggleSelection(file: File) {
        val current = _selectionState.value
        if (!current.isMode) return
        val path = file.absolutePath
        val newPaths = if (path in current.paths) current.paths - path else current.paths + path
        _selectionState.value = if (newPaths.isEmpty()) SelectionState() else current.copy(paths = newPaths)
    }

    fun selectAll() {
        val allPaths = uiState.value.files.map { it.absolutePath }.toSet()
        _selectionState.update { it.copy(isMode = true, paths = allPaths) }
    }

    fun clearSelection() {
        _selectionState.value = SelectionState()
    }

    // ── Single-file clipboard (bottom sheet) ──────────────────────────────────

    fun cutFile(file: File) {
        _clipboard.value = ClipboardData(listOf(file), ClipboardOperation.CUT)
        _operationMessage.value = "Cut: ${file.name}"
    }

    fun copyFile(file: File) {
        _clipboard.value = ClipboardData(listOf(file), ClipboardOperation.COPY)
        _operationMessage.value = "Copied: ${file.name}"
    }

    // ── Batch clipboard (selection mode) ──────────────────────────────────────

    fun cutSelected() {
        val files = selectedFiles()
        _clipboard.value = ClipboardData(files, ClipboardOperation.CUT)
        _operationMessage.value = if (files.size == 1) "Cut: ${files[0].name}" else "Cut ${files.size} items"
        clearSelection()
    }

    fun copySelected() {
        val files = selectedFiles()
        _clipboard.value = ClipboardData(files, ClipboardOperation.COPY)
        _operationMessage.value = if (files.size == 1) "Copied: ${files[0].name}" else "Copied ${files.size} items"
        clearSelection()
    }

    fun deleteSelected() {
        val files = selectedFiles()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    files.forEach { file ->
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
                _operationMessage.value = "Deleted ${files.size} item(s)"
                clearSelection()
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun selectedFiles(): List<File> {
        val paths = _selectionState.value.paths
        return uiState.value.files.filter { it.absolutePath in paths }
    }

    // ── Paste ─────────────────────────────────────────────────────────────────

    fun paste() {
        val clipData = _clipboard.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    clipData.files.forEach { sourceFile ->
                        val destination = File(_currentPath.value, sourceFile.name)
                        if (sourceFile.absolutePath == destination.absolutePath) return@forEach
                        when (clipData.operation) {
                            ClipboardOperation.COPY -> {
                                if (sourceFile.isDirectory) sourceFile.copyRecursively(destination, overwrite = false)
                                else sourceFile.copyTo(destination, overwrite = false)
                            }
                            ClipboardOperation.CUT -> {
                                if (sourceFile.isDirectory) {
                                    sourceFile.copyRecursively(destination, overwrite = false)
                                    sourceFile.deleteRecursively()
                                } else {
                                    sourceFile.copyTo(destination, overwrite = false)
                                    sourceFile.delete()
                                }
                            }
                        }
                    }
                }
                _clipboard.value = null
                val count = clipData.files.size
                _operationMessage.value = if (count == 1) "Pasted: ${clipData.files[0].name}" else "Pasted $count items"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Paste failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Other file operations ─────────────────────────────────────────────────

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val newFile = File(file.parent, newName)
                    if (newFile.exists()) throw IllegalStateException("A file with name '$newName' already exists")
                    if (!file.renameTo(newFile)) throw IllegalStateException("Rename failed")
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
                    val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
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
                    if (newDir.exists()) throw IllegalStateException("'$name' already exists")
                    if (!newDir.mkdir()) throw IllegalStateException("Failed to create folder")
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
                    if (newFile.exists()) throw IllegalStateException("'$name' already exists")
                    if (!newFile.createNewFile()) throw IllegalStateException("Failed to create file")
                }
                _operationMessage.value = "File created: $name"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Create file failed: ${e.message}"
            }
        }
    }

    // ── Zip / Unzip ───────────────────────────────────────────────────────────

    fun zipFiles(files: List<File>, archiveName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val name = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName else "$archiveName.zip"
                    val outFile = File(_currentPath.value, name)
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                        files.forEach { file -> addToZip(zos, file, file.name) }
                    }
                }
                _operationMessage.value = "Created: $archiveName.zip"
                clearSelection()
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Zip failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> addToZip(zos, child, "$entryName/${child.name}") }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun unzipFile(zipFile: File) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val destination = zipFile.parentFile ?: return@withContext
                    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(destination, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { zis.copyTo(it) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                _operationMessage.value = "Unzipped: ${zipFile.name}"
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Unzip failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Intent helpers ────────────────────────────────────────────────────────

    fun getOpenFileIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Open with")
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    // ── Message helpers ───────────────────────────────────────────────────────

    fun clearError() { _errorMessage.value = null }
    fun clearOperationMessage() { _operationMessage.value = null }

    private fun refreshFiles() {
        _fileListTrigger.value = System.currentTimeMillis()
        _folderSizes.value = emptyMap()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            withContext(Dispatchers.IO) { delay(300) }
            refreshFiles()
            _isRefreshing.value = false
        }
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    fun getFolderSize(folder: File): String? =
        _folderSizes.value[folder.absolutePath]?.let { formatFileSize(it) }
}
