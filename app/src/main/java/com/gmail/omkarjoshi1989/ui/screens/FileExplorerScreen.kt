package com.gmail.omkarjoshi1989.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel,
    onOpenFile: (File) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToRecentFiles: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteMultipleDialog by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }

    // Show error messages via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Show operation messages via snackbar
    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }

    // Refresh when the user returns to the app from an external app (safety net in
    // addition to the FileObserver in the ViewModel)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (uiState.isSelectionMode) {
                            Text("${uiState.selectedFilePaths.size} selected", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Files", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.clearSelection()
                            } else if (!viewModel.navigateUp()) {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                if (uiState.isSelectionMode) Icons.Filled.Close
                                else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (uiState.isSelectionMode) "Cancel selection" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (uiState.isSelectionMode) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("All", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        } else {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Create new")
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    onClick = { createName = ""; showCreateFolderDialog = true; showAddMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    onClick = { createName = ""; showCreateFileDialog = true; showAddMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) }
                                )
                            }
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Files") },
                                    onClick = { onNavigateToRecentFiles(); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.AccessTime, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Favorites") },
                                    onClick = { onNavigateToFavorites(); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Photos") },
                                    onClick = { onShowToast("Photos - Coming soon!"); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Music") },
                                    onClick = { onShowToast("Music - Coming soon!"); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Videos") },
                                    onClick = { onShowToast("Videos - Coming soon!"); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Documents") },
                                    onClick = { onShowToast("Documents - Coming soon!"); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Applications") },
                                    onClick = { onShowToast("Applications - Coming soon!"); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { onNavigateToSettings(); showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                if (!uiState.isSelectionMode) {
                    // Breadcrumb path bar
                    BreadcrumbBar(
                        currentPath = uiState.currentPath,
                        onPathClick = { viewModel.navigateTo(it) }
                    )
                }
            }
        },
        bottomBar = {
            // Batch-action bar shown during multi-select
            AnimatedVisibility(visible = uiState.isSelectionMode) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    val hasSelection = uiState.selectedFilePaths.isNotEmpty()
                    IconButton(
                        onClick = { if (hasSelection) showDeleteMultipleDialog = true },
                        enabled = hasSelection
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete selected",
                            tint = if (hasSelection) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { if (hasSelection) viewModel.cutSelected() },
                        enabled = hasSelection
                    ) {
                        Icon(Icons.Filled.ContentCut, contentDescription = "Cut selected")
                    }
                    IconButton(
                        onClick = { if (hasSelection) viewModel.copySelected() },
                        enabled = hasSelection
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selected")
                    }
                    IconButton(
                        onClick = { if (hasSelection) { zipName = ""; showZipDialog = true } },
                        enabled = hasSelection
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = "Zip selected")
                    }
                }
            }
        },
        floatingActionButton = {
            // Paste FAB — hidden in selection mode
            AnimatedVisibility(visible = uiState.clipboard != null && !uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.paste() },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = "Paste",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (uiState.files.isEmpty() && !uiState.isLoading) {
                Text(
                    text = "This folder is empty",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.files, key = { it.absolutePath }) { file ->
                    FileListItem(
                        file = file,
                        viewModel = viewModel,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = file.absolutePath in uiState.selectedFilePaths,
                        isFavorite = file.absolutePath in favoritePaths,
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(file)
                            } else if (file.isDirectory) {
                                viewModel.navigateTo(file.absolutePath)
                            } else {
                                onOpenFile(file)
                            }
                        },
                        onLongClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(file)
                            } else {
                                selectedFile = file
                                showBottomSheet = true
                            }
                        }
                    )
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
                onSelect = {
                    viewModel.enterSelectionMode(selectedFile!!)
                    showBottomSheet = false
                    selectedFile = null
                },
                onCut = { viewModel.cutFile(selectedFile!!); showBottomSheet = false },
                onCopy = { viewModel.copyFile(selectedFile!!); showBottomSheet = false },
                onRename = { renameText = selectedFile!!.name; showRenameDialog = true; showBottomSheet = false },
                onDelete = { showDeleteDialog = true; showBottomSheet = false },
                onUnzip = if (selectedFile!!.extension.equals("zip", ignoreCase = true)) {
                    {
                        viewModel.unzipFile(selectedFile!!)
                        showBottomSheet = false
                        selectedFile = null
                    }
                } else null
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
                    if (renameText.isNotBlank()) viewModel.renameFile(selectedFile!!, renameText)
                    showRenameDialog = false; selectedFile = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }

    // ── Single delete dialog ──────────────────────────────────────────────────
    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedFile = null },
            title = { Text("Delete") },
            text = {
                Text("Are you sure you want to delete \"${selectedFile!!.name}\"?${if (selectedFile!!.isDirectory) "\nThis will delete all contents inside." else ""}")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(selectedFile!!)
                    showDeleteDialog = false; selectedFile = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }

    // ── Batch delete dialog ───────────────────────────────────────────────────
    if (showDeleteMultipleDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMultipleDialog = false },
            title = { Text("Delete ${uiState.selectedFilePaths.size} item(s)?") },
            text = { Text("This action cannot be undone. All selected files and folders will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); showDeleteMultipleDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMultipleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Zip name dialog ───────────────────────────────────────────────────────
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Create Zip Archive") },
            text = {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive name") },
                    placeholder = { Text("archive") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (zipName.isNotBlank()) {
                        val selectedFiles = uiState.files.filter { it.absolutePath in uiState.selectedFilePaths }
                        viewModel.zipFiles(selectedFiles, zipName)
                    }
                    showZipDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Create folder dialog ──────────────────────────────────────────────────
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (createName.isNotBlank()) viewModel.createNewFolder(createName)
                    showCreateFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Create file dialog ────────────────────────────────────────────────────
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("File name (with extension)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (createName.isNotBlank()) viewModel.createNewFile(createName)
                    showCreateFileDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onPathClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val internalStoragePrefix = "/storage/emulated/0"
    val isUnderInternalStorage = currentPath.startsWith(internalStoragePrefix)

    // Build display segments: collapse /storage/emulated/0 into "Internal Storage"
    val displaySegments: List<Pair<String, String>> = if (isUnderInternalStorage) {
        val remainder = currentPath.removePrefix(internalStoragePrefix)
        val subSegments = remainder.split("/").filter { it.isNotEmpty() }
        val result = mutableListOf<Pair<String, String>>()
        result.add("Internal Storage" to internalStoragePrefix)
        subSegments.forEachIndexed { index, segment ->
            val path = internalStoragePrefix + "/" + subSegments.subList(0, index + 1).joinToString("/")
            result.add(segment to path)
        }
        result
    } else {
        val segments = currentPath.split("/").filter { it.isNotEmpty() }
        val result = mutableListOf<Pair<String, String>>()
        result.add("/" to "/")
        segments.forEachIndexed { index, segment ->
            val path = "/" + segments.subList(0, index + 1).joinToString("/")
            result.add(segment to path)
        }
        result
    }

    // Auto-scroll to the end when path changes
    LaunchedEffect(currentPath) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        displaySegments.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onPathClick(path) }) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == displaySegments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: File,
    viewModel: FileExplorerViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null   // row click handles toggle
                    )
                }
            } else {
                FileThumbnail(file = file, size = 40.dp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!file.isDirectory) {
                        Text(
                            text = viewModel.formatFileSize(file.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val itemCount = file.listFiles()?.size ?: 0
                        val folderSize = viewModel.getFolderSize(file)
                        Text(
                            text = if (folderSize != null) "$itemCount items • $folderSize"
                                   else "$itemCount items • Calculating…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = DateFormat.format("MMM dd, yyyy HH:mm", Date(file.lastModified())).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Star badge — only visible when file is a favorite and not in selection mode
            if (isFavorite && !isSelectionMode) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun FileOperationsSheet(
    file: File,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onSelect: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onUnzip: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        HorizontalDivider()

        FileOperationItem(icon = Icons.Filled.CheckBox, label = "Select", onClick = onSelect)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        // Favorite toggle — only for files, not folders
        if (!file.isDirectory) {
            FileOperationItem(
                icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                onClick = onToggleFavorite,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface
            )
        }
        FileOperationItem(icon = Icons.Filled.ContentCut, label = "Cut", onClick = onCut)
        FileOperationItem(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
        FileOperationItem(icon = Icons.Filled.DriveFileRenameOutline, label = "Rename", onClick = onRename)
        if (onUnzip != null) {
            FileOperationItem(icon = Icons.Filled.Archive, label = "Unzip here", onClick = onUnzip)
        }
        FileOperationItem(
            icon = Icons.Filled.Delete,
            label = "Delete",
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun FileOperationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
