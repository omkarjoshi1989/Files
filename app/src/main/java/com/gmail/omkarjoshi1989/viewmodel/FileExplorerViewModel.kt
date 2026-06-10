package com.gmail.omkarjoshi1989.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmail.omkarjoshi1989.util.FileOperationNotificationHelper
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import com.gmail.omkarjoshi1989.util.SettingsManager
import com.gmail.omkarjoshi1989.util.ChunkedFileLoader
import com.gmail.omkarjoshi1989.util.ChunkConfig
import com.gmail.omkarjoshi1989.model.clearFolderMatchCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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

enum class FileSortOption(val label: String) {
    NAME("Name"), DATE("Date Modified"), SIZE("Size"), TYPE("Type")
}

/** Holds one or many files queued for a cut/copy operation. */
data class ClipboardData(
    val files: List<File>,
    val operation: ClipboardOperation
)

/**
 * Live progress state emitted during a ZIP extraction.
 * Null when no extraction is running.
 *
 * @param archiveName   display name of the archive currently being extracted
 * @param currentEntry  name of the ZIP entry being written right now
 * @param current       entries extracted so far
 * @param total         total entries in the current archive
 * @param fileIndex     0-based index of the archive being processed (batch mode)
 * @param totalFiles    total archives in the current batch
 */
data class UnzipProgressState(
    val archiveName: String,
    val currentEntry: String,
    val current: Int,
    val total: Int,
    val fileIndex: Int  = 0,
    val totalFiles: Int = 1
)

private data class SelectionState(
    val isMode: Boolean = false,
    val paths: Set<String> = emptySet()
)

private data class SortState(
    val option: FileSortOption = FileSortOption.NAME,
    val ascending: Boolean = true
)

private data class NavState(
    val path: String,
    val showHidden: Boolean,
    val searchQuery: String,
    val isSearchActive: Boolean,
    val sortState: SortState
)

data class FileExplorerUiState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<File> = emptyList(),
    val showHiddenFiles: Boolean = false,
    val clipboard: ClipboardData? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val operationMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: FileSortOption = FileSortOption.NAME,
    val sortAscending: Boolean = true
)

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    // ── Chunked Loading Configuration ────────────────────────────────────────
    /** Configuration for chunked file loading to handle large directories efficiently */
    private val chunkConfig = ChunkConfig(
        chunkSize = 150,              // Files displayed per subsequent chunk
        initialBatchSize = 200,       // Files shown in the very first paint
        maxConcurrentChunks = 2,
        chunkDelayMs = 50             // ms between subsequent chunk renders
    )

    private val _currentPath = MutableStateFlow(Environment.getExternalStorageDirectory().absolutePath)
    private val _showHiddenFiles = MutableStateFlow(SettingsManager.isShowHiddenFiles(application))

    /**
     * Shared across ALL ViewModel instances (main explorer + every collection).
     * Using [ClipboardRepository] means a file cut/copied in "Images" mode is
     * still available for pasting after the user switches to "All Files" mode.
     */
    private val _clipboard get() = ClipboardRepository.clipboard

    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _operationMessage = MutableStateFlow<String?>(null)
    private val _fileListTrigger = MutableStateFlow(0L)
    private val _selectionState = MutableStateFlow(SelectionState())
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _sortState = MutableStateFlow(SortState())

    /** Emits live unzip progress; null when no extraction is running. */
    private val _unzipProgress = MutableStateFlow<UnzipProgressState?>(null)
    val unzipProgress: StateFlow<UnzipProgressState?> = _unzipProgress.asStateFlow()

    /** Async-loaded file list. Populated on Dispatchers.IO so the UI thread is never blocked. */
    private val _files = MutableStateFlow<List<File>>(emptyList())

    /**
     * Raw (unfiltered/unsorted) directory listing cache.
     * Key = "$absolutePath|$showHidden".
     * Holds up to 30 folder listings so back-navigation and revisits are instant.
     * Sorted/filtered views are re-derived from this cache on Dispatchers.Default (CPU-only, fast).
     */
    private val rawFilesCache = LruCache<String, List<File>>(30)

    private fun rawCacheKey(path: String, showHidden: Boolean) = "$path|$showHidden"

    /** Evicts cache entries for [path] (both hidden-visible variants). */
    private fun invalidateCacheFor(path: String) {
        rawFilesCache.remove("$path|true")
        rawFilesCache.remove("$path|false")
    }

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
        
        // Load initial sort preference for the starting folder
        loadSortPreferenceForCurrentPath()
        
        viewModelScope.launch {
            _currentPath.collect { path -> 
                startWatching(path)
                // Load sort preference when path changes
                loadSortPreferenceForPath(path)
            }
        }

        // ── Async file loading ────────────────────────────────────────────────
        // collectLatest cancels the previous load whenever path/filter/sort changes,
        // so navigating quickly to another folder never blocks the UI.
        viewModelScope.launch {
            combine(
                combine(_currentPath, _showHiddenFiles, _searchQuery, _isSearchActive, _sortState) {
                        path, hidden, sq, searchActive, sort ->
                    NavState(path, hidden, sq, searchActive, sort)
                },
                _fileListTrigger
            ) { navState, _ -> navState }
            .collectLatest { navState -> loadFilesAsync(navState) }
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
        combine(_currentPath, _showHiddenFiles, _searchQuery, _isSearchActive, _sortState) { path, hidden, sq, searchActive, sort ->
            NavState(path, hidden, sq, searchActive, sort)
        },
        _clipboard,
        combine(_isLoading, _isLoadingMore, _isRefreshing, _selectionState) { l, lm, r, sel -> 
            Triple(Triple(l, lm, r), sel, Unit) 
        },
        combine(_errorMessage, _operationMessage) { err, op -> err to op },
        _files
    ) { navState, clipboard, loadingSel, errOp, files ->
        val (loadingStates, selState, _) = loadingSel
        val (loading, loadingMore, refreshing) = loadingStates
        val (error, opMsg) = errOp
        FileExplorerUiState(
            currentPath = navState.path,
            files = files,
            showHiddenFiles = navState.showHidden,
            clipboard = clipboard,
            isLoading = loading,
            isLoadingMore = loadingMore,
            isRefreshing = refreshing,
            errorMessage = error,
            operationMessage = opMsg,
            isSelectionMode = selState.isMode,
            selectedFilePaths = selState.paths,
            searchQuery = navState.searchQuery,
            isSearchActive = navState.isSearchActive,
            sortOption = navState.sortState.option,
            sortAscending = navState.sortState.ascending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileExplorerUiState())

    /**
     * Improved loading strategy — eliminates item reordering during progressive display:
     *
     * Fast path (cache hit):
     *   Display the cached raw list immediately after applying sort/filter on Dispatchers.Default.
     *   No disk I/O, no spinner — instant. FileObserver invalidates the cache on any FS change.
     *
     * Slow path (cache miss):
     *   1. Read ALL file metadata from disk in ONE listFiles() call (no double-read).
     *   2. Cache the raw (unsorted) list immediately.
     *   3. Sort the ENTIRE list once on Dispatchers.Default.
     *   4. Display the first [initialBatchSize] files immediately (spinner disappears).
     *   5. Append subsequent sorted chunks with small delays — because files are
     *      already in correct order, new chunks APPEND to the bottom and never
     *      cause already-visible items to jump/reorder.
     */
    private suspend fun loadFilesAsync(navState: NavState) {
        val key = rawCacheKey(navState.path, navState.showHidden)
        val cachedRaw = rawFilesCache.get(key)

        // ── Fast path: serve from cache ──────────────────────────────────────────
        // Covers both normal browsing and active search — no disk I/O at all.
        // FileObserver (CREATE/DELETE/MOVED/CLOSE_WRITE) evicts the cache whenever
        // the directory changes, keeping this data fresh.
        if (cachedRaw != null) {
            _files.value = withContext(Dispatchers.Default) {
                applySearchAndSort(cachedRaw, navState.searchQuery, navState.sortState, navState.showHidden)
            }
            return
        }

        // ── Slow path: cache miss — read from disk ───────────────────────────────
        if (!_isRefreshing.value) _isLoading.value = true

        try {
            val directory = File(navState.path)
            loadFilesInChunks(directory, navState, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load folder: ${e.message}"
            _files.value = emptyList()
        } finally {
            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }

    /**
     * Reads all files from disk in a single [File.listFiles] call, caches the raw list,
     * sorts the entire list once, then emits it to the UI in sorted chunks.
     *
     * Because every chunk is a slice of the already-sorted list, new chunks simply
     * append to the bottom — visible items never jump or reorder.
     */
    private suspend fun loadFilesInChunks(
        directory: File,
        navState: NavState,
        cacheKey: String
    ) {
        // 1. Single disk read — no double listFiles() call
        val rawFiles = withContext(Dispatchers.IO) {
            listRawFiles(directory, navState.showHidden)
        }

        // 2. Cache raw (unsorted) list immediately so sort/filter changes are instant
        rawFilesCache.put(cacheKey, rawFiles)

        // 3. Sort the ENTIRE list once — O(n log n) but CPU-only and very fast
        //    even for 1 000+ files (< 5 ms on typical Android hardware)
        val sortedFiles = withContext(Dispatchers.Default) {
            val filtered = if (navState.searchQuery.isNotBlank())
                rawFiles.filter { it.name.contains(navState.searchQuery, ignoreCase = true) }
            else rawFiles
            filtered.sortedWith(buildComparator(navState.sortState))
        }

        // 4. Display in chunks — all chunks are slices of the sorted list,
        //    so appending never causes any item to change position
        if (sortedFiles.size <= chunkConfig.initialBatchSize) {
            // Small directory: show everything at once, no chunking overhead
            _files.value = sortedFiles
            _isLoading.value = false
        } else {
            // Large directory: show initial batch immediately…
            val accumulator = sortedFiles.take(chunkConfig.initialBatchSize).toMutableList()
            _files.value = accumulator.toList()
            _isLoading.value = false
            _isLoadingMore.value = true

            // …then append remaining sorted chunks one by one
            val remainingChunks = sortedFiles.drop(chunkConfig.initialBatchSize)
                .chunked(chunkConfig.chunkSize)

            remainingChunks.forEachIndexed { index, chunk ->
                delay(0)                                  // yield for cancellation check
                if (index > 0) delay(chunkConfig.chunkDelayMs)
                accumulator.addAll(chunk)
                _files.value = accumulator.toList()
            }

            _isLoadingMore.value = false
        }
    }

    /**
     * Builds a [Comparator] for the given [SortState].
     * Folders always sort before files; within each group the chosen sort key applies.
     */
    private fun buildComparator(sortState: SortState): Comparator<File> =
        compareBy<File> { !it.isDirectory }.let { base ->
            when (sortState.option) {
                FileSortOption.NAME ->
                    if (sortState.ascending) base.thenBy { it.name.lowercase() }
                    else base.thenByDescending { it.name.lowercase() }
                FileSortOption.DATE ->
                    if (sortState.ascending) base.thenBy { it.lastModified() }
                    else base.thenByDescending { it.lastModified() }
                FileSortOption.SIZE ->
                    if (sortState.ascending) base.thenBy { if (it.isDirectory) 0L else it.length() }
                    else base.thenByDescending { if (it.isDirectory) 0L else it.length() }
                FileSortOption.TYPE ->
                    if (sortState.ascending) base.thenBy { it.extension.lowercase() }.thenBy { it.name.lowercase() }
                    else base.thenByDescending { it.extension.lowercase() }.thenBy { it.name.lowercase() }
            }
        }

    /** Heavy IO: reads directory entries from disk and applies the hidden-file filter. */
    private fun listRawFiles(directory: File, showHidden: Boolean): List<File> {
        val all = directory.listFiles() ?: return emptyList()
        return all.filter { showHidden || !it.isHidden }
    }

    /** CPU-only: applies search filter and sort (used for cache-hit fast path). */
    private fun applySearchAndSort(
        files: List<File>,
        searchQuery: String,
        sortState: SortState,
        showHidden: Boolean
    ): List<File> {
        val filtered = if (searchQuery.isNotBlank())
            files.filter { it.name.contains(searchQuery, ignoreCase = true) }
        else files
        return filtered.sortedWith(buildComparator(sortState))
    }


    // ── Navigation ────────────────────────────────────────────────────────────

    fun navigateTo(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            clearSelection()
            _searchQuery.value = ""
            _isSearchActive.value = false
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
            _searchQuery.value = ""
            _isSearchActive.value = false
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

    // ── Search ────────────────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        if (_isSearchActive.value) {
            _isSearchActive.value = false
            _searchQuery.value = ""
        } else {
            _isSearchActive.value = true
        }
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    fun setSortOption(option: FileSortOption) {
        val current = _sortState.value
        val newAscending = if (current.option == option) !current.ascending else {
            when (option) {
                FileSortOption.NAME, FileSortOption.TYPE -> true
                FileSortOption.DATE, FileSortOption.SIZE -> false
            }
        }
        _sortState.value = SortState(option, newAscending)
        
        // Save the sort preference for the current folder
        saveSortPreferenceForCurrentPath()
    }

    /**
     * Loads the sort preference for the current folder path from SharedPreferences.
     * If no preference exists, defaults to NAME/ascending.
     */
    private fun loadSortPreferenceForCurrentPath() {
        loadSortPreferenceForPath(_currentPath.value)
    }

    /**
     * Loads the sort preference for a specific folder path.
     * @param path The folder path to load preferences for
     */
    private fun loadSortPreferenceForPath(path: String) {
        val sortPref = SettingsManager.getFolderSort(getApplication(), path)
        if (sortPref != null) {
            val (optionName, ascending) = sortPref
            val option = FileSortOption.valueOf(optionName)
            _sortState.value = SortState(option, ascending)
        } else {
            // Default: NAME ascending for folders with no saved preference
            _sortState.value = SortState(FileSortOption.NAME, true)
        }
    }

    /**
     * Saves the current sort preference for the current folder path to SharedPreferences.
     */
    private fun saveSortPreferenceForCurrentPath() {
        val currentSort = _sortState.value
        SettingsManager.saveFolderSort(
            getApplication(),
            _currentPath.value,
            currentSort.option.name,
            currentSort.ascending
        )
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

    /**
     * Cuts the given [files] list into the clipboard.
     * The Screen passes its own local selectedPaths — ViewModel's internal
     * _selectionState is intentionally NOT used for this path.
     */
    fun cutSelected(files: List<File>) {
        if (files.isEmpty()) return
        _clipboard.value = ClipboardData(files, ClipboardOperation.CUT)
        _operationMessage.value = if (files.size == 1) "Cut: ${files[0].name}" else "Cut ${files.size} items"
    }

    /** Copies the given [files] list into the clipboard. */
    fun copySelected(files: List<File>) {
        if (files.isEmpty()) return
        _clipboard.value = ClipboardData(files, ClipboardOperation.COPY)
        _operationMessage.value = if (files.size == 1) "Copied: ${files[0].name}" else "Copied ${files.size} items"
    }

    fun deleteSelected() {
        val files = selectedFiles()
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    files.forEach { file -> RecycleBinManager.moveToRecycleBin(context, file) }
                }
                _operationMessage.value = "Moved ${files.size} item(s) to Recycle Bin"
                clearSelection()
                clearFolderMatchCache()
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
        val context   = getApplication<Application>()
        val total     = clipData.files.size
        val isCut     = clipData.operation == ClipboardOperation.CUT
        val opVerb    = if (isCut) "Moving" else "Copying"
        val doneVerb  = if (isCut) "Moved"  else "Copied"

        viewModelScope.launch {
            _isLoading.value = true

            // ── initial progress notification ────────────────────────────────
            FileOperationNotificationHelper.showProgress(
                context,
                title   = "$opVerb ${if (total == 1) clipData.files[0].name else "$total items"}",
                text    = "Preparing…",
                current = 0,
                total   = total
            )

            try {
                withContext(Dispatchers.IO) {
                    clipData.files.forEachIndexed { index, sourceFile ->
                        // ── per-file progress update ─────────────────────────
                        FileOperationNotificationHelper.showProgress(
                            context,
                            title   = "$opVerb ${if (total == 1) sourceFile.name else "$total items"}",
                            text    = if (total == 1) sourceFile.name
                                      else "${index + 1} / $total · ${sourceFile.name}",
                            current = index,
                            total   = total
                        )

                        val destination = File(_currentPath.value, sourceFile.name)
                        if (sourceFile.absolutePath == destination.absolutePath) return@forEachIndexed

                        when (clipData.operation) {
                            ClipboardOperation.COPY -> {
                                if (sourceFile.isDirectory)
                                    sourceFile.copyRecursively(destination, overwrite = false)
                                else
                                    sourceFile.copyTo(destination, overwrite = false)
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
                val destFolder = File(_currentPath.value).name
                val completionText = if (total == 1)
                    "${clipData.files[0].name} → $destFolder"
                else
                    "$total items → $destFolder"

                // ── persistent completion notification ───────────────────────
                FileOperationNotificationHelper.showCompletion(
                    context,
                    title = "$doneVerb${if (total == 1) "" else " $total items"}",
                    text  = completionText
                )

                _operationMessage.value =
                    if (total == 1) "Pasted: ${clipData.files[0].name}"
                    else "Pasted $total items"

                refreshFiles()

            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                FileOperationNotificationHelper.showError(
                    context,
                    title = "Paste failed",
                    text  = errMsg
                )
                _errorMessage.value = "Paste failed: $errMsg"
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
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val success = RecycleBinManager.moveToRecycleBin(context, file)
                    if (!success) throw IllegalStateException("Move to Recycle Bin failed")
                }
                _operationMessage.value = "Moved to Recycle Bin: ${file.name}"
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
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isLoading.value = true

            // Initial indeterminate progress — notification + in-app overlay
            FileOperationNotificationHelper.showProgress(
                context,
                title = "Unzipping ${zipFile.name}",
                text  = "Preparing…",
                current = 0,
                total   = 0
            )
            _unzipProgress.value = UnzipProgressState(
                archiveName  = zipFile.name,
                currentEntry = "Preparing…",
                current = 0,
                total   = 0
            )

            try {
                withContext(Dispatchers.IO) {
                    extractZip(zipFile) { current, total, entryName ->
                        FileOperationNotificationHelper.showProgress(
                            context,
                            title   = "Unzipping ${zipFile.name}",
                            text    = "$current / $total · $entryName",
                            current = current,
                            total   = total
                        )
                        _unzipProgress.value = UnzipProgressState(
                            archiveName  = zipFile.name,
                            currentEntry = entryName,
                            current = current,
                            total   = total
                        )
                    }
                }

                FileOperationNotificationHelper.showCompletion(
                    context,
                    title = "Unzip complete",
                    text  = "${zipFile.name} extracted successfully"
                )
                _operationMessage.value = "Unzipped: ${zipFile.name}"
                refreshFiles()
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                FileOperationNotificationHelper.showError(
                    context,
                    title = "Unzip failed",
                    text  = errMsg
                )
                _errorMessage.value = "Unzip failed: $errMsg"
            } finally {
                _unzipProgress.value = null   // dismiss in-app overlay
                _isLoading.value = false
            }
        }
    }

    fun unzipFiles(files: List<File>) {
        if (files.isEmpty()) return
        val context = getApplication<Application>()
        val total   = files.size
        viewModelScope.launch {
            _isLoading.value = true

            // Initial notification
            FileOperationNotificationHelper.showProgress(
                context,
                title   = if (total == 1) "Unzipping ${files[0].name}" else "Unzipping $total archives",
                text    = "Preparing…",
                current = 0,
                total   = total
            )
            _unzipProgress.value = UnzipProgressState(
                archiveName  = if (total == 1) files[0].name else "$total archives",
                currentEntry = "Preparing…",
                current      = 0,
                total        = 0,
                fileIndex    = 0,
                totalFiles   = total
            )

            try {
                withContext(Dispatchers.IO) {
                    files.forEachIndexed { fileIndex, zipFile ->
                        // Per-archive notification header
                        FileOperationNotificationHelper.showProgress(
                            context,
                            title   = if (total == 1) "Unzipping ${zipFile.name}"
                                      else "Unzipping $total archives",
                            text    = if (total == 1) zipFile.name
                                      else "${fileIndex + 1} / $total · ${zipFile.name}",
                            current = fileIndex,
                            total   = total
                        )
                        _unzipProgress.value = UnzipProgressState(
                            archiveName  = zipFile.name,
                            currentEntry = "Preparing…",
                            current      = 0,
                            total        = 0,
                            fileIndex    = fileIndex,
                            totalFiles   = total
                        )
                        extractZip(zipFile) { current, entryTotal, entryName ->
                            FileOperationNotificationHelper.showProgress(
                                context,
                                title   = if (total == 1) "Unzipping ${zipFile.name}"
                                          else "Unzipping $total archives",
                                text    = if (total == 1) "$current / $entryTotal · $entryName"
                                          else "${fileIndex + 1} / $total · $entryName",
                                current = if (total == 1) current else fileIndex,
                                total   = if (total == 1) entryTotal else total
                            )
                            _unzipProgress.value = UnzipProgressState(
                                archiveName  = zipFile.name,
                                currentEntry = entryName,
                                current      = current,
                                total        = entryTotal,
                                fileIndex    = fileIndex,
                                totalFiles   = total
                            )
                        }
                    }
                }

                val doneText = if (total == 1)
                    "${files[0].name} extracted successfully"
                else
                    "$total archives extracted successfully"
                FileOperationNotificationHelper.showCompletion(
                    context,
                    title = "Unzip complete",
                    text  = doneText
                )
                _operationMessage.value = if (total == 1)
                    "Unzipped: ${files[0].name}"
                else
                    "Unzipped $total archives"
                refreshFiles()
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown error"
                FileOperationNotificationHelper.showError(
                    context,
                    title = "Unzip failed",
                    text  = errMsg
                )
                _errorMessage.value = "Unzip failed: $errMsg"
            } finally {
                _unzipProgress.value = null   // dismiss in-app overlay
                _isLoading.value = false
            }
        }
    }

    /**
     * Extracts all entries of [zipFile] into its parent directory.
     * [onProgress] is called after every entry with (entriesDone, totalEntries, entryName).
     */
    private fun extractZip(
        zipFile: File,
        onProgress: (current: Int, total: Int, entryName: String) -> Unit = { _, _, _ -> }
    ) {
        val destination = zipFile.parentFile ?: return

        // Count entries upfront so progress is accurate
        val totalEntries = java.util.zip.ZipFile(zipFile).use { it.size() }
        var processed = 0

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val outFile   = File(destination, entryName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                processed++
                onProgress(processed, totalEntries, entryName)
                entry = zis.nextEntry
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
    fun clearClipboard() { _clipboard.value = null }

    private fun refreshFiles() {
        val currentPath = _currentPath.value
        // Evict the raw listing cache for this path so the next load reads fresh data from disk
        invalidateCacheFor(currentPath)
        _fileListTrigger.value = System.currentTimeMillis()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Clear collection-type folder match cache so newly added/removed
            // media files are picked up on the next traversal.
            clearFolderMatchCache()
            refreshFiles()
            delay(300)
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
}
