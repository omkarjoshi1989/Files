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
import com.gmail.omkarjoshi1989.model.FileItem
import com.gmail.omkarjoshi1989.util.DirectoryCacheManager
import com.gmail.omkarjoshi1989.util.BackgroundOperationsManager
import com.gmail.omkarjoshi1989.util.FileOperationNotificationHelper
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import com.gmail.omkarjoshi1989.util.SettingsManager
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
    NAME("Name"), DATE("Date Created"), SIZE("Size"), TYPE("Type")
}

enum class ViewMode { LIST, GRID }

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

/**
 * Live progress state emitted during a paste (cut/copy) operation.
 * Null when no paste is running.
 *
 * @param operationVerb  "Moving" or "Copying"
 * @param currentFile    name of the file currently being processed
 * @param current        files processed so far
 * @param total          total files to process
 */
data class PasteProgressState(
    val operationVerb: String,
    val currentFile: String,
    val current: Int,
    val total: Int
)

/**
 * Live progress state emitted while files are being moved to the Recycle Bin.
 * Null when no delete operation is running.
 *
 * @param currentFile  name of the file/folder currently being processed
 * @param current      items processed so far
 * @param total        total items to process
 */
data class DeleteProgressState(
    val currentFile: String,
    val current: Int,
    val total: Int
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
    val files: List<FileItem> = emptyList(),
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
    val sortAscending: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST
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
    private val _viewMode = MutableStateFlow(ViewMode.LIST)

    /** Emits live unzip progress; null when no extraction is running. */
    private val _unzipProgress = MutableStateFlow<UnzipProgressState?>(null)
    val unzipProgress: StateFlow<UnzipProgressState?> = _unzipProgress.asStateFlow()

    /** Emits live paste (cut/copy) progress; null when no paste is running. */
    private val _pasteProgress = MutableStateFlow<PasteProgressState?>(null)
    val pasteProgress: StateFlow<PasteProgressState?> = _pasteProgress.asStateFlow()

    /** Emits live delete progress; null when no delete operation is running. */
    private val _deleteProgress = MutableStateFlow<DeleteProgressState?>(null)
    val deleteProgress: StateFlow<DeleteProgressState?> = _deleteProgress.asStateFlow()

    /** Async-loaded file list (FileItem carries pre-fetched metadata — zero stat() in UI). */
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())

    // NOTE: Directory listing cache has been moved to DirectoryCacheManager (app-level singleton).
    // It provides a two-level memory + disk cache that survives both ViewModel recreation
    // and full app restarts, so DCIM/Camera with 1 000+ files is always served instantly.

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "show_hidden_files") {
            _showHiddenFiles.value = SettingsManager.isShowHiddenFiles(application)
        }
    }

    // FileObserver to detect external changes (deletions, creations, renames)
    private var fileObserver: FileObserver? = null
    private var debounceJob: Job? = null

    private val observerMask = FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO
            // NOTE: CLOSE_WRITE intentionally omitted — it fires on every camera photo/video
            // write and would constantly evict the DCIM/Camera cache even though the
            // directory *listing* hasn't changed.  CREATE handles new files perfectly.

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
            // 1 500 ms debounce — prevents spamming reloads when the camera app
            // rapidly creates multiple shot files (burst mode, etc.)
            delay(1_500)
            // Only reload if the directory's lastModified actually changed,
            // meaning entries were really added / removed.
            val path = _currentPath.value
            val hidden = _showHiddenFiles.value
            val key = DirectoryCacheManager.key(path, hidden)
            val cached = DirectoryCacheManager.getMemory(key)
            val currentLastMod = withContext(Dispatchers.IO) { File(path).lastModified() }
            if (cached == null || currentLastMod != cached.folderLastModified) {
                refreshFiles()
            }
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
        combine(_files, _viewMode) { files, vm -> files to vm }
    ) { navState, clipboard, loadingSel, errOp, filesVm ->
        val (loadingStates, selState, _) = loadingSel
        val (loading, loadingMore, refreshing) = loadingStates
        val (error, opMsg) = errOp
        val (files, viewMode) = filesVm
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
            sortAscending = navState.sortState.ascending,
            viewMode = viewMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileExplorerUiState())

    /**
     * ══════════════════════════════════════════════════════════════════════════
     *  STALE-WHILE-REVALIDATE loading strategy
     * ══════════════════════════════════════════════════════════════════════════
     *
     *  The goal is ZERO loading-indicator experience for any folder the user
     *  has visited before — including monster folders like DCIM/Camera with
     *  1 000+ files and 7+ GB of content.
     *
     *  ┌─────────────────────────────────────────────────────────────────────┐
     *  │  Level 0 – Memory cache hit (fastest)                               │
     *  │    • DirectoryCacheManager.getMemory() — HashMap lookup, <1 µs.    │
     *  │    • Survives ViewModel recreation within the same process.         │
     *  │    • Show instantly, no spinner.                                    │
     *  │    • Background: stat() the directory; if lastModified changed,     │
     *  │      reload silently (no spinner, list refreshes quietly).          │
     *  │                                                                     │
     *  │  Level 1 – Disk cache hit (fast)                                   │
     *  │    • Sequential file read — vastly cheaper than listFiles() for     │
     *  │      large folders (no per-file stat() calls).                      │
     *  │    • Survives app restarts.                                         │
     *  │    • Promoted to memory, then same stale-check as Level 0.         │
     *  │                                                                     │
     *  │  Level 2 – True cache miss (first-ever visit, unavoidable I/O)     │
     *  │    • Show spinner, call listFiles() once, cache the result,         │
     *  │      then display in sorted chunks — spinner disappears after        │
     *  │      the first [initialBatchSize] files are ready.                  │
     *  │    • This path is only hit ONCE per folder per install.             │
     *  └─────────────────────────────────────────────────────────────────────┘
     *
     *  FileObserver events (CREATE / DELETE / MOVED_*) that signal a real
     *  directory-structure change call refreshFiles(), which marks the memory
     *  cache as stale.  The next loadFilesAsync() sees the stale flag and
     *  automatically triggers a silent background reload.
     *
     *  CLOSE_WRITE has been removed from the observer mask so that camera
     *  apps writing to DCIM/Camera never trigger spurious cache evictions.
     */
    private suspend fun loadFilesAsync(navState: NavState) {
        val key = DirectoryCacheManager.key(navState.path, navState.showHidden)

        // ── Level 0: Memory cache ───────────────────────────────────────────
        val memCached = DirectoryCacheManager.getMemory(key)
        if (memCached != null) {
            // Show immediately — no spinner, even if the entry is stale
            _files.value = withContext(Dispatchers.Default) {
                applySearchAndSort(memCached.files, navState.searchQuery, navState.sortState, navState.showHidden)
            }
            _isLoading.value = false

            // Background freshness check: compare directory's lastModified with cached value
            val stale = memCached.isStale || withContext(Dispatchers.IO) {
                File(navState.path).lastModified() != memCached.folderLastModified
            }
            if (stale) {
                // Silent background reload — the user sees the old data, list updates quietly
                try {
                    reloadSilently(navState, key)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    /* Keep showing stale data — better than nothing */
                }
            }
            return
        }

        // ── Level 1: Disk cache ─────────────────────────────────────────────
        val diskCached = withContext(Dispatchers.IO) { DirectoryCacheManager.getDisk(key) }
        if (diskCached != null) {
            // Promote from disk → show instantly, same stale-check as memory path
            _files.value = withContext(Dispatchers.Default) {
                applySearchAndSort(diskCached.files, navState.searchQuery, navState.sortState, navState.showHidden)
            }
            _isLoading.value = false

            val stale = diskCached.isStale || withContext(Dispatchers.IO) {
                File(navState.path).lastModified() != diskCached.folderLastModified
            }
            if (stale) {
                try {
                    reloadSilently(navState, key)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { /* Keep showing disk-cached data */ }
            }
            return
        }

        // ── Level 2: True cache miss — first-ever visit ─────────────────────
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
     * Silently reloads the directory from disk and updates [_files] with no spinner.
     * Called when the cache is stale or [File.lastModified] has changed.
     */
    private suspend fun reloadSilently(navState: NavState, key: String) {
        val directory = File(navState.path)
        val dirLastModified: Long
        val rawItems: List<FileItem>
        withContext(Dispatchers.IO) {
            dirLastModified = directory.lastModified()
            rawItems = listRawFileItems(directory, navState.showHidden)
            DirectoryCacheManager.put(key, rawItems, dirLastModified)
        }
        val sorted = withContext(Dispatchers.Default) {
            applySearchAndSort(rawItems, navState.searchQuery, navState.sortState, navState.showHidden)
        }
        _files.value = sorted
    }

    /**
     * First-visit loader: reads directory + pre-fetches all file metadata in one IO pass,
     * stores in [DirectoryCacheManager], then displays in sorted chunks so the spinner
     * disappears after [ChunkConfig.initialBatchSize] items are ready.
     *
     * This path is only taken on the **very first visit** to a folder.
     * Every subsequent visit is served from the two-level cache (zero IO).
     */
    private suspend fun loadFilesInChunks(
        directory: File,
        navState: NavState,
        cacheKey: String
    ) {
        val dirLastModified: Long
        val rawItems: List<FileItem>
        withContext(Dispatchers.IO) {
            dirLastModified = directory.lastModified()
            // listRawFileItems pre-fetches isDirectory, size, lastModified, extension
            // for each entry — zero IO on main thread or during sort
            rawItems = listRawFileItems(directory, navState.showHidden)
            // Cache immediately — subsequent visits (incl. sort/filter changes) are instant
            DirectoryCacheManager.put(cacheKey, rawItems, dirLastModified)
        }

        // Sort is purely in-memory: compares FileItem fields, zero stat() calls
        val sortedItems = withContext(Dispatchers.Default) {
            val filtered = if (navState.searchQuery.isNotBlank())
                rawItems.filter { it.name.contains(navState.searchQuery, ignoreCase = true) }
            else rawItems
            filtered.sortedWith(buildComparator(navState.sortState))
        }

        if (sortedItems.size <= chunkConfig.initialBatchSize) {
            _files.value = sortedItems
            _isLoading.value = false
        } else {
            // Show first batch → hide spinner → append remaining in sorted order
            val accumulator = sortedItems.take(chunkConfig.initialBatchSize).toMutableList()
            _files.value = accumulator.toList()
            _isLoading.value = false
            _isLoadingMore.value = true

            sortedItems.drop(chunkConfig.initialBatchSize)
                .chunked(chunkConfig.chunkSize)
                .forEachIndexed { index, chunk ->
                    delay(0)                             // cooperative cancellation checkpoint
                    if (index > 0) delay(chunkConfig.chunkDelayMs)
                    accumulator.addAll(chunk)
                    _files.value = accumulator.toList()
                }

            _isLoadingMore.value = false
        }
    }

    /**
     * Builds a zero-IO [Comparator] for [FileItem].
     *
     * All comparisons use pre-fetched fields — no stat() calls whatsoever.
     * Folders always sort before files; the chosen sort key applies within each group.
     */
    private fun buildComparator(sortState: SortState): Comparator<FileItem> =
        compareBy<FileItem> { !it.isDirectory }.let { base ->
            when (sortState.option) {
                FileSortOption.NAME ->
                    if (sortState.ascending) base.thenBy { it.name.lowercase() }
                    else base.thenByDescending { it.name.lowercase() }
                FileSortOption.DATE ->
                    if (sortState.ascending) base.thenBy { it.creationTime }
                    else base.thenByDescending { it.creationTime }
                FileSortOption.SIZE ->
                    if (sortState.ascending) base.thenBy { it.size.coerceAtLeast(0L) }
                    else base.thenByDescending { it.size.coerceAtLeast(0L) }
                FileSortOption.TYPE ->
                    if (sortState.ascending) base.thenBy { it.extension }.thenBy { it.name.lowercase() }
                    else base.thenByDescending { it.extension }.thenBy { it.name.lowercase() }
            }
        }

    /**
     * Reads all directory entries from disk, pre-fetching every metadata field.
     * Must be called on Dispatchers.IO.
     *
     * Uses [FileItem.fromFile] which issues a single stat() per entry on API 26+
     * (via BasicFileAttributes) or three separate stat() calls on API 24–25.
     * Either way, ALL metadata is read here so the main thread never touches the FS.
     */
    private fun listRawFileItems(directory: File, showHidden: Boolean): List<FileItem> {
        val entries = directory.listFiles() ?: return emptyList()
        return entries.mapNotNull { file ->
            val item = FileItem.fromFile(file)   // stat() happens here, on IO thread
            if (!showHidden && item.isHidden) null else item
        }
    }

    /** CPU-only: applies search filter + sort on pre-fetched FileItem data (zero IO). */
    private fun applySearchAndSort(
        items: List<FileItem>,
        searchQuery: String,
        sortState: SortState,
        @Suppress("UNUSED_PARAMETER") showHidden: Boolean   // kept for signature parity
    ): List<FileItem> {
        val filtered = if (searchQuery.isNotBlank())
            items.filter { it.name.contains(searchQuery, ignoreCase = true) }
        else items
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
        // Load view mode for this folder (default = LIST)
        val savedMode = SettingsManager.getFolderViewMode(getApplication(), path)
        _viewMode.value = if (savedMode == "GRID") ViewMode.GRID else ViewMode.LIST
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

    /** Toggles or sets the view mode for the current folder and persists it. */
    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        SettingsManager.saveFolderViewMode(getApplication(), _currentPath.value, mode.name)
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
        val total = files.size
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isLoading.value = true
            _deleteProgress.value = DeleteProgressState(
                currentFile = "Preparing…",
                current     = 0,
                total       = total
            )
            try {
                withContext(Dispatchers.IO) {
                    files.forEachIndexed { index, file ->
                        _deleteProgress.value = DeleteProgressState(
                            currentFile = file.name,
                            current     = index + 1,
                            total       = total
                        )
                        RecycleBinManager.moveToRecycleBin(context, file)
                    }
                }
                _operationMessage.value = "Moved ${files.size} item(s) to Recycle Bin"
                clearSelection()
                clearFolderMatchCache()
                refreshFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            } finally {
                _deleteProgress.value = null
                _isLoading.value = false
            }
        }
    }

    private fun selectedFiles(): List<File> {
        val paths = _selectionState.value.paths
        return uiState.value.files.filter { it.absolutePath in paths }.map { it.file }
    }

    // ── Paste ─────────────────────────────────────────────────────────────────

    fun paste() {
        val clipData = _clipboard.value ?: return
        val context   = getApplication<Application>()
        val total     = clipData.files.size
        val isCut     = clipData.operation == ClipboardOperation.CUT
        val opVerb    = if (isCut) "Moving" else "Copying"
        val doneVerb  = if (isCut) "Moved"  else "Copied"

        // ── Dismiss the clipboard FAB immediately so the bottom UI disappears ──
        _clipboard.value = null

        viewModelScope.launch {
            _isLoading.value = true

            // ── Show paste progress dialog immediately ────────────────────────
            _pasteProgress.value = PasteProgressState(
                operationVerb = opVerb,
                currentFile   = "Preparing…",
                current       = 0,
                total         = total
            )

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
                    // Create individual operation items for each file
                    val operationIds = mutableMapOf<String, String>() // fileName -> operationId
                    for (sourceFile in clipData.files) {
                        val operationId = "paste_${System.currentTimeMillis()}_${sourceFile.name}_${if (isCut) "cut" else "copy"}"
                        operationIds[sourceFile.name] = operationId
                        BackgroundOperationsManager.start(
                            id = operationId,
                            title = "$opVerb item",
                            detail = "Preparing...",
                            total = 1,
                            fileName = sourceFile.name,
                            sourcePath = sourceFile.parentFile?.absolutePath.orEmpty(),
                            destinationPath = _currentPath.value
                        )
                    }

                    clipData.files.forEachIndexed { index, sourceFile ->
                        // ── per-file progress update ─────────────────────────
                        _pasteProgress.value = PasteProgressState(
                            operationVerb = opVerb,
                            currentFile   = sourceFile.name,
                            current       = index + 1,
                            total         = total
                        )

                        val operationId = operationIds[sourceFile.name]
                        if (operationId != null) {
                            BackgroundOperationsManager.progress(
                                id = operationId,
                                title = "$opVerb item",
                                detail = "In progress...",
                                current = 1,
                                total = 1,
                                fileName = sourceFile.name,
                                sourcePath = sourceFile.parentFile?.absolutePath.orEmpty(),
                                destinationPath = _currentPath.value
                            )
                        }

                        FileOperationNotificationHelper.showProgress(
                            context,
                            title   = "$opVerb ${if (total == 1) sourceFile.name else "$total items"}",
                            text    = if (total == 1) sourceFile.name
                                      else "${index + 1} / $total · ${sourceFile.name}",
                            current = index,
                            total   = total
                        )

                        val destination = File(_currentPath.value, sourceFile.name)
                        if (sourceFile.absolutePath == destination.absolutePath) {
                            if (operationId != null) {
                                BackgroundOperationsManager.complete(
                                    id = operationId,
                                    title = "$doneVerb item",
                                    detail = "Skipped (same location)",
                                    current = 1,
                                    total = 1,
                                    fileName = sourceFile.name,
                                    sourcePath = sourceFile.parentFile?.absolutePath.orEmpty(),
                                    destinationPath = _currentPath.value
                                )
                            }
                            return@forEachIndexed
                        }

                        runCatching {
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
                        }.onSuccess {
                            if (operationId != null) {
                                val destFolder = File(_currentPath.value).name
                                BackgroundOperationsManager.complete(
                                    id = operationId,
                                    title = "$doneVerb item",
                                    detail = "${sourceFile.name} → $destFolder",
                                    current = 1,
                                    total = 1,
                                    fileName = sourceFile.name,
                                    sourcePath = sourceFile.parentFile?.absolutePath.orEmpty(),
                                    destinationPath = _currentPath.value
                                )
                            }
                        }.onFailure { err ->
                            if (operationId != null) {
                                BackgroundOperationsManager.fail(
                                    id = operationId,
                                    title = "$opVerb failed",
                                    detail = err.message ?: "Unknown error",
                                    current = 0,
                                    total = 1,
                                    fileName = sourceFile.name,
                                    sourcePath = sourceFile.parentFile?.absolutePath.orEmpty(),
                                    destinationPath = _currentPath.value
                                )
                            }
                        }
                    }
                }

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
                _pasteProgress.value = null   // dismiss in-app progress dialog
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
            _deleteProgress.value = DeleteProgressState(
                currentFile = file.name,
                current     = 1,
                total       = 1
            )
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
                _deleteProgress.value = null
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
        // Soft-invalidate: mark the cached listing as stale so the next loadFilesAsync()
        // shows the old data instantly and then silently reloads in the background.
        // This means paste/delete/rename never show an 8-10 s spinner for large folders.
        DirectoryCacheManager.invalidate(currentPath)
        _fileListTrigger.value = System.currentTimeMillis()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Soft-invalidate: mark stale so the next load shows cached data instantly
            // then silently reloads.  This prevents an 8-10 s blank screen on pull-to-refresh.
            clearFolderMatchCache()
            refreshFiles()
            delay(300)
            _isRefreshing.value = false
        }
    }

    /**
     * Lightweight freshness check called on [Lifecycle.Event.ON_RESUME].
     *
     * Re-runs [loadFilesAsync] WITHOUT invalidating the cache.  The stale-while-revalidate
     * logic will compare [File.lastModified] against the cached value:
     * • If unchanged  → do nothing (user just returned from viewing a photo, etc.)
     * • If changed    → silent background reload (new files added by camera, etc.)
     *
     * This never shows a spinner — it is purely a background correctness check.
     */
    fun requestFreshnessCheck() {
        // Trigger loadFilesAsync re-run without touching the cache
        _fileListTrigger.value = System.currentTimeMillis()
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
