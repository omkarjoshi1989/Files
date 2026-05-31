package com.gmail.omkarjoshi1989.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.viewmodel.FileFilter
import com.gmail.omkarjoshi1989.viewmodel.RecentFilesViewModel
import com.gmail.omkarjoshi1989.viewmodel.SortOption
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentFilesScreen(
    viewModel: RecentFilesViewModel,
    onOpenFile: (File) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Show operation/error messages via snackbar
    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
            favoritePaths = FavoritesManager.getFavorites(context)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Filter by file name…") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Column {
                            Text("All Files", fontWeight = FontWeight.Bold)
                            if (!uiState.isLoading) {
                                val filterLabel = if (uiState.fileFilter == FileFilter.ALL) ""
                                                  else " • ${uiState.fileFilter.label}"
                                Text(
                                    text = "${uiState.filteredFiles.size}/${uiState.totalFilesScanned} files • ${uiState.sortOption.label}$filterLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearchActive) viewModel.toggleSearch()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isSearchActive) {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    } else {
                        // Filter button
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "Filter by type",
                                    tint = if (uiState.fileFilter != FileFilter.ALL)
                                               MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                data class FilterEntry(val filter: FileFilter, val icon: ImageVector)
                                val filterEntries = listOf(
                                    FilterEntry(FileFilter.ALL, Icons.Filled.Apps),
                                    FilterEntry(FileFilter.IMAGES, Icons.Filled.Image),
                                    FilterEntry(FileFilter.VIDEOS, Icons.Filled.VideoLibrary),
                                    FilterEntry(FileFilter.IMAGES_AND_VIDEOS, Icons.Filled.Image),
                                    FilterEntry(FileFilter.AUDIO, Icons.Filled.MusicNote),
                                    FilterEntry(FileFilter.PDF, Icons.Filled.Description)
                                )
                                filterEntries.forEach { entry ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = entry.filter.label,
                                                fontWeight = if (uiState.fileFilter == entry.filter) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = entry.icon,
                                                contentDescription = null,
                                                tint = if (uiState.fileFilter == entry.filter)
                                                           MaterialTheme.colorScheme.primary
                                                       else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.setFileFilter(entry.filter)
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        // Sort button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = if (uiState.sortAscending) Icons.Filled.KeyboardArrowUp
                                                  else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Sort"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (uiState.sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (uiState.sortOption == option) {
                                                    Icon(
                                                        imageVector = if (uiState.sortAscending) Icons.Filled.KeyboardArrowUp
                                                                      else Icons.Filled.KeyboardArrowDown,
                                                        contentDescription = if (uiState.sortAscending) "Ascending" else "Descending",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        // Search button
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.filteredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                                uiState.searchQuery.isNotBlank() && uiState.fileFilter != FileFilter.ALL ->
                                    "No ${uiState.fileFilter.label} matching \"${uiState.searchQuery}\""
                                uiState.searchQuery.isNotBlank() ->
                                    "No files matching \"${uiState.searchQuery}\""
                                uiState.fileFilter != FileFilter.ALL ->
                                    "No ${uiState.fileFilter.label} found"
                                else -> "No files found"
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredFiles, key = { it.absolutePath }) { file ->
                        RecentFileItem(
                            file = file,
                            isFavorite = file.absolutePath in favoritePaths,
                            onClick = { onOpenFile(file) },
                            onLongClick = {
                                selectedFile = file
                                showBottomSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Bottom sheet ──────────────────────────────────────────────────────────
    if (showBottomSheet && selectedFile != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false; selectedFile = null },
            sheetState = sheetState
        ) {
            FileOperationsSheet(
                file = selectedFile!!,
                isFavorite = selectedFile!!.absolutePath in favoritePaths,
                onToggleFavorite = {
                    FavoritesManager.toggleFavorite(context, selectedFile!!.absolutePath)
                    favoritePaths = FavoritesManager.getFavorites(context)
                    showBottomSheet = false
                    selectedFile = null
                },
                onRename = {
                    renameText = selectedFile!!.name
                    showRenameDialog = true
                    showBottomSheet = false
                },
                onDelete = {
                    showDeleteDialog = true
                    showBottomSheet = false
                },
                onShare = {
                    try {
                        val intent = FileUtils.getShareFileIntent(context, selectedFile!!)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Unable to share this file", Toast.LENGTH_SHORT).show()
                    }
                    showBottomSheet = false
                    selectedFile = null
                }
            )
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    if (showRenameDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; selectedFile = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        val oldPath = selectedFile!!.absolutePath
                        // Update favorite path if this file is favorited
                        if (oldPath in favoritePaths) {
                            val newPath = "${selectedFile!!.parent}/$renameText"
                            FavoritesManager.removeFavorite(context, oldPath)
                            FavoritesManager.toggleFavorite(context, newPath)
                            favoritePaths = FavoritesManager.getFavorites(context)
                        }
                        viewModel.renameFile(selectedFile!!, renameText)
                    }
                    showRenameDialog = false; selectedFile = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedFile = null },
            title = { Text("Delete") },
            text = {
                Text("Are you sure you want to delete \"${selectedFile!!.name}\"?")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Remove from favorites if present
                    FavoritesManager.removeFavorite(context, selectedFile!!.absolutePath)
                    favoritePaths = FavoritesManager.getFavorites(context)
                    viewModel.deleteFile(selectedFile!!)
                    showDeleteDialog = false; selectedFile = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileItem(
    file: File,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isHidden = file.name.startsWith(".")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isHidden) 0.5f else 1f)
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
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = file.parent ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = FileUtils.formatFileSize(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateFormat.format("MMM dd, yyyy HH:mm:ss", Date(file.lastModified())).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Star badge — visible when file is a favorite
            if (isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
