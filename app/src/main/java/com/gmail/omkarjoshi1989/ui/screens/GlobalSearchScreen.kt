package com.gmail.omkarjoshi1989.ui.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.ui.components.VideoProgressBar
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GlobalSearchScreen(
    viewModel: FileExplorerViewModel,
    onOpenFile: (File) -> Unit,
    onOpenFolder: (File) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    var recentFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoadingRecent by remember { mutableStateOf(true) }
    var isRefreshingRecent by remember { mutableStateOf(false) }
    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }

    // Selection mode state
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedPaths.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun selectedFiles(): List<File> = selectedPaths.map { File(it) }

    // Clear selection on back press when in selection mode
    BackHandler(enabled = isSelectionMode) {
        selectedPaths = emptySet()
    }

    // Load recent 100 files once on first composition
    LaunchedEffect(Unit) {
        isLoadingRecent = true
        recentFiles = withContext(Dispatchers.IO) {
            FileUtils.getRecentMediaFiles(context, limit = 100)
        }
        isLoadingRecent = false
    }

    // Start search whenever query changes (debounced via job cancellation)
    LaunchedEffect(query) {
        searchJob?.cancel()
        results = emptyList()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            isSearching = false
            return@LaunchedEffect
        }
        searchJob = coroutineScope.launch {
            isSearching = true
            val found = withContext(Dispatchers.IO) {
                val root = Environment.getExternalStorageDirectory()
                val accumulator = mutableListOf<File>()
                searchFilesRecursive(root, trimmed.lowercase(), accumulator, maxResults = 300)
                accumulator.sortedWith(compareBy({ !it.isFile }, { it.name.lowercase() }))
            }
            results = found
            isSearching = false
        }
    }

    Scaffold(
        // ── Bottom bar: appears in selection mode ─────────────────────────────
        bottomBar = {
            if (isSelectionMode) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cut
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    viewModel.cutSelected(selectedFiles())
                                    selectedPaths = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.ContentCut, contentDescription = "Cut")
                            Text("Cut", style = MaterialTheme.typography.labelSmall)
                        }
                        // Copy
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    viewModel.copySelected(selectedFiles())
                                    selectedPaths = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                        // Delete
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showDeleteDialog = true }
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        // Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    try {
                                        val uris = ArrayList<android.net.Uri>()
                                        selectedPaths.forEach { p ->
                                            val f = File(p)
                                            val uri = FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", f
                                            )
                                            uris.add(uri)
                                        }
                                        val shareIntent = android.content.Intent(
                                            android.content.Intent.ACTION_SEND_MULTIPLE
                                        ).apply {
                                            type = "*/*"
                                            putParcelableArrayListExtra(
                                                android.content.Intent.EXTRA_STREAM, uris
                                            )
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(shareIntent, "Share files")
                                        )
                                    } catch (_: Exception) { }
                                    selectedPaths = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                            Text("Share", style = MaterialTheme.typography.labelSmall)
                        }
                        // Favorites toggle
                        val allFavorite = selectedPaths.isNotEmpty() &&
                                selectedPaths.all { it in favoritePaths }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    selectedPaths.forEach { path ->
                                        FavoritesManager.toggleFavorite(context, path)
                                    }
                                    favoritePaths = FavoritesManager.getFavorites(context)
                                    selectedPaths = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(
                                if (allFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (allFavorite) "Remove from Favorites"
                                                     else "Add to Favorites",
                                tint = if (allFavorite) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                if (allFavorite) "Unfavorite" else "Favorite",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedPaths.size} selected", fontWeight = FontWeight.Bold)
                    } else {
                        val fr = focusRequester
                        LaunchedEffect(Unit) { fr.requestFocus() }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fr),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search all files…",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) selectedPaths = emptySet()
                        else onNavigateBack()
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelectionMode) "Clear selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (!isSelectionMode) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        } else {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                query.trim().length < 2 && query.isEmpty() -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshingRecent,
                        onRefresh = {
                            if (!isLoadingRecent && !isRefreshingRecent) {
                                coroutineScope.launch {
                                    isRefreshingRecent = true
                                    recentFiles = withContext(Dispatchers.IO) {
                                        FileUtils.getRecentMediaFiles(context, limit = 100)
                                    }
                                    isRefreshingRecent = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Show recent files before user starts typing
                        when {
                            isLoadingRecent -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            recentFiles.isEmpty() -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Type to search all files",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Enter at least 2 characters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Text(
                                            "Recent files",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(recentFiles, key = { it.absolutePath }) { file ->
                                        val isSelected = file.absolutePath in selectedPaths
                                        GlobalSearchResultItem(
                                            file = file,
                                            isFavorite = file.absolutePath in favoritePaths,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = isSelected,
                                            onToggleFavorite = {
                                                FavoritesManager.toggleFavorite(context, file.absolutePath)
                                                favoritePaths = FavoritesManager.getFavorites(context)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedPaths = if (isSelected)
                                                        selectedPaths - file.absolutePath
                                                    else selectedPaths + file.absolutePath
                                                } else {
                                                    if (file.isDirectory) onOpenFolder(file)
                                                    else onOpenFile(file)
                                                }
                                            },
                                            onLongClick = {
                                                selectedPaths = selectedPaths + file.absolutePath
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                query.trim().length == 1 -> {
                    Text(
                        "Enter at least 2 characters to search",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                isSearching -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Searching…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                results.isEmpty() -> {
                    Text(
                        "No files found for \"${query.trim()}\"",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "${results.size}${if (results.size >= 300) "+" else ""} result(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(results, key = { it.absolutePath }) { file ->
                            val isSelected = file.absolutePath in selectedPaths
                            GlobalSearchResultItem(
                                file = file,
                                isFavorite = file.absolutePath in favoritePaths,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onToggleFavorite = {
                                    FavoritesManager.toggleFavorite(context, file.absolutePath)
                                    favoritePaths = FavoritesManager.getFavorites(context)
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedPaths = if (isSelected)
                                            selectedPaths - file.absolutePath
                                        else selectedPaths + file.absolutePath
                                    } else {
                                        if (file.isDirectory) onOpenFolder(file)
                                        else onOpenFile(file)
                                    }
                                },
                                onLongClick = {
                                    selectedPaths = selectedPaths + file.absolutePath
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteDialog && selectedPaths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Move to Recycle Bin") },
            text = {
                Text(
                    "Move ${selectedPaths.size} item(s) to the Recycle Bin?\n\nYou can restore them later."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedPaths.forEach { path -> viewModel.deleteFile(File(path)) }
                    selectedPaths = emptySet()
                    showDeleteDialog = false
                }) {
                    Text("Move to Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalSearchResultItem(
    file: File,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) selectionColor else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(file = file, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // ── Video playback progress bar (only for video files) ────────
                if (!file.isDirectory && FileUtils.isVideoFile(file)) {
                    VideoProgressBar(file = file)
                }
                // Show parent folder path as subtitle
                val parentPath = file.parentFile?.let { parent ->
                    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
                    parent.absolutePath.removePrefix(storageRoot).ifEmpty { "/" }
                } ?: ""
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!file.isDirectory) {
                        Text(
                            text = FileUtils.formatFileSize(file.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Show filled star only for favorited files; no icon for non-favorites
            if (!isSelectionMode && isFavorite) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Remove from favorites",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Recursively walks [dir] and collects files (and directories) whose
 * name contains [queryLower] (case-insensitive). Stops once [maxResults] are found.
 */
private fun searchFilesRecursive(
    dir: File,
    queryLower: String,
    accumulator: MutableList<File>,
    maxResults: Int
) {
    if (accumulator.size >= maxResults) return
    val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
    for (child in children) {
        if (accumulator.size >= maxResults) return
        if (child.name.startsWith(".")) continue  // skip hidden
        if (child.name.lowercase().contains(queryLower)) {
            accumulator.add(child)
        }
        if (child.isDirectory) {
            searchFilesRecursive(child, queryLower, accumulator, maxResults)
        }
    }
}
