package com.gmail.omkarjoshi1989.ui.screens

import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gmail.omkarjoshi1989.MediaViewerActivity
import com.gmail.omkarjoshi1989.model.CollectionType
import com.gmail.omkarjoshi1989.model.folderContainsMatchingFiles
import com.gmail.omkarjoshi1989.model.matchesFile
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.MusicResumeManager
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.gmail.omkarjoshi1989.viewmodel.FileSortOption
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel,
    onOpenFile: (File) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToApplications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onShowToast: (String) -> Unit,
    collectionFilter: CollectionType? = null,
    collectionTitle: String? = null,
    onNavigateToCollection: ((CollectionType) -> Unit)? = null,
    onNavigateToInternalStorage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }

    // selectedFile  → file whose bottom-sheet is currently open (single-file ops)
    var selectedFile by remember { mutableStateOf<File?>(null) }
    // selectedPaths → multi-select set (drives selection mode when non-empty)
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedPaths.isNotEmpty()

    var showBottomSheet by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }

    // Converts selectedPaths to File list (used for batch clipboard ops)
    fun selectedFiles(): List<File> = selectedPaths.map { File(it) }

    // Apply collection filter: show only matching files; for directories, only show them
    // if they (or any descendant) contain at least one matching file.
    // This folder-hiding behaviour is NOT applied for Applications and Recycle Bin.
    val displayFiles = if (collectionFilter != null) {
        uiState.files.filter { file ->
            if (file.isDirectory) collectionFilter.folderContainsMatchingFiles(file)
            else collectionFilter.matchesFile(file)
        }
    } else {
        uiState.files
    }

    // Whether the hamburger (drawer) icon should be shown instead of back arrow
    val isAtRootLevel = File(uiState.currentPath).parentFile?.canRead() != true

    // ── Back handler: when in selection mode, clear selection + clipboard ────
    // Placed here so it takes priority over the Activity-level BackHandler.
    // Rule:
    //   • Still in selection mode (no cut/copy done yet) → clear selection AND clipboard.
    //   • NOT in selection mode (cut/copy already committed, selectedPaths was emptied) →
    //     Activity-level handler fires → navigates up, clipboard is preserved.
    BackHandler(enabled = isSelectionMode) {
        selectedPaths = emptySet()
        selectedFile = null
        viewModel.clearClipboard()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearOperationMessage() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                favoritePaths = FavoritesManager.getFavorites(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode && !uiState.isSearchActive,
        drawerContent = {
            AppNavigationDrawer(
                currentCollectionFilter = collectionFilter,
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                onNavigateToInternalStorage = {
                    coroutineScope.launch { drawerState.close() }
                    if (collectionFilter != null) {
                        onNavigateToInternalStorage?.invoke()
                    } else {
                        viewModel.navigateTo(Environment.getExternalStorageDirectory().absolutePath)
                    }
                },
                onNavigateToCollection = { type ->
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToCollection?.invoke(type)
                },
                onNavigateToApplications = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToApplications()
                },
                onNavigateToRecycleBin = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToRecycleBin()
                },
                onNavigateToFavorites = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToFavorites()
                },
                onNavigateToSettings = {
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToSettings()
                }
            )
        }
    ) {

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },

        // ── Paste FAB (always shown when clipboard has files) ─────────────────
        floatingActionButton = {
            val clipData = uiState.clipboard
            if (clipData != null && !isSelectionMode) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { viewModel.clearClipboard() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel clipboard")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.paste() },
                        icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                        text = {
                            val isMove = clipData.operation == ClipboardOperation.CUT
                            val base = if (isMove) "Move here" else "Paste here"
                            val count = clipData.files.size
                            Text(if (count == 1) "$base — ${clipData.files[0].name}" else "$base ($count items)")
                        }
                    )
                }
            }
        },

        // ── Bottom bar: appears in selection mode with batch operations ────────
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
                        // Zip
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    zipName = if (selectedPaths.size == 1)
                                        File(selectedPaths.first()).nameWithoutExtension
                                    else "Archive"
                                    showZipDialog = true
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.FolderZip, contentDescription = "Zip")
                            Text("Zip", style = MaterialTheme.typography.labelSmall)
                        }
                        // Delete
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showDeleteDialog = true }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", f
                                            )
                                            uris.add(uri)
                                        }
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "*/*"
                                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share files"))
                                    } catch (e: Exception) {
                                        onShowToast("Share failed: ${e.localizedMessage ?: e.message}")
                                    }
                                    selectedPaths = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                            Text("Share", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },

        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedPaths.size} selected", fontWeight = FontWeight.Bold)
                        } else if (uiState.isSearchActive) {
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    Box {
                                        if (uiState.searchQuery.isEmpty()) {
                                            Text(
                                                "Search in this folder…",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        } else {
                            Text(collectionTitle ?: "Files", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        val showHamburger = !isSelectionMode && !uiState.isSearchActive && isAtRootLevel && collectionFilter == null
                        IconButton(onClick = {
                            when {
                                isSelectionMode -> {
                                    selectedPaths = emptySet(); selectedFile = null
                                    viewModel.clearClipboard()
                                }
                                uiState.isSearchActive -> viewModel.toggleSearch()
                                showHamburger -> coroutineScope.launch { drawerState.open() }
                                else -> if (!viewModel.navigateUp()) onNavigateBack()
                            }
                        }) {
                            Icon(
                                when {
                                    isSelectionMode || uiState.isSearchActive -> Icons.Filled.Close
                                    showHamburger -> Icons.Filled.Menu
                                    else -> Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = if (showHamburger) "Open menu" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            // Select all toggle
                            IconButton(onClick = {
                                selectedPaths = if (selectedPaths.size < displayFiles.size)
                                    displayFiles.map { it.absolutePath }.toSet()
                                else emptySet()
                            }) {
                                Icon(Icons.Filled.Apps, contentDescription = "Select all")
                            }
                            // More options for selection mode
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                // Open bottom-sheet for single selected file
                                if (selectedPaths.size == 1) {
                                    DropdownMenuItem(
                                        text = { Text("Options for this file") },
                                        onClick = {
                                            selectedFile = File(selectedPaths.first())
                                            showBottomSheet = true
                                            showMoreMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("Toggle Favorites") },
                                    onClick = {
                                        selectedPaths.forEach { path -> FavoritesManager.toggleFavorite(context, path) }
                                        favoritePaths = FavoritesManager.getFavorites(context)
                                        selectedPaths = emptySet()
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) }
                                )
                            }
                        } else {
                            // Normal mode actions
                            // Music button — opens recently played track in the music player
                            IconButton(onClick = {
                                val lastFile = MusicResumeManager.getLastFilePath(context)
                                val lastFolder = MusicResumeManager.getLastFolderPath(context)
                                if (lastFile != null && lastFolder != null && java.io.File(lastFile).exists()) {
                                    val intent = Intent(context, MediaViewerActivity::class.java).apply {
                                        putExtra(MediaViewerActivity.EXTRA_FILE_PATH, lastFile)
                                        putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, lastFolder)
                                        putExtra(MediaViewerActivity.EXTRA_NO_AUTOPLAY, true)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    onShowToast("No recently played music")
                                }
                            }) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = "Music",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Create new")
                            }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
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
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                FileSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("Sort by ${option.label}") },
                                        onClick = { viewModel.setSortOption(option); showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) },
                                        trailingIcon = {
                                            if (uiState.sortOption == option) Icon(
                                                if (uiState.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                BreadcrumbBar(
                    currentPath = uiState.currentPath,
                    onPathClick = { selectedPaths = emptySet(); viewModel.navigateTo(it) }
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            if (displayFiles.isEmpty() && !uiState.isLoading) {
                Text(
                    text = if (collectionFilter != null) "No ${collectionTitle ?: "files"} found here"
                           else "This folder is empty",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(displayFiles, key = { it.absolutePath }) { file ->
                    val isSelected = file.absolutePath in selectedPaths
                    FileListItem(
                        file = file,
                        viewModel = viewModel,
                        isFavorite = file.absolutePath in favoritePaths,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onSelectionToggle = {
                            selectedPaths = if (isSelected)
                                selectedPaths - file.absolutePath
                            else
                                selectedPaths + file.absolutePath
                        },
                        onClick = {
                            if (isSelectionMode) {
                                // In selection mode: tap toggles selection
                                selectedPaths = if (isSelected)
                                    selectedPaths - file.absolutePath
                                else
                                    selectedPaths + file.absolutePath
                            } else {
                                if (file.isDirectory) viewModel.navigateTo(file.absolutePath)
                                else onOpenFile(file)
                            }
                        },
                        onLongClick = {
                            // Long-press ALWAYS enters / extends selection mode
                            selectedPaths = selectedPaths + file.absolutePath
                        }
                    )
                }
            }
        }
    }

    } // end ModalNavigationDrawer

    // ── Single-file bottom sheet (long-press in selection mode ⋮ → Options) ───
    if (showBottomSheet && selectedFile != null) {
        val sheetFile = selectedFile!!
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false; selectedFile = null },
            sheetState = sheetState
        ) {
            FileOperationsSheet(
                file = sheetFile,
                isFavorite = sheetFile.absolutePath in favoritePaths,
                onCut = {
                    viewModel.cutFile(sheetFile)
                    showBottomSheet = false; selectedFile = null; selectedPaths = emptySet()
                },
                onCopy = {
                    viewModel.copyFile(sheetFile)
                    showBottomSheet = false; selectedFile = null; selectedPaths = emptySet()
                },
                onToggleFavorite = {
                    FavoritesManager.toggleFavorite(context, sheetFile.absolutePath)
                    favoritePaths = FavoritesManager.getFavorites(context)
                    showBottomSheet = false; selectedFile = null
                },
                onRename = {
                    renameText = sheetFile.name
                    showRenameDialog = true
                    showBottomSheet = false
                },
                onDelete = {
                    showDeleteDialog = true
                    showBottomSheet = false
                },
                onUnzip = if (sheetFile.extension.equals("zip", ignoreCase = true)) {
                    { viewModel.unzipFile(sheetFile); showBottomSheet = false; selectedFile = null }
                } else null,
                onShare = {
                    try {
                        context.startActivity(FileUtils.getShareFileIntent(context, sheetFile))
                    } catch (e: Exception) {
                        onShowToast("Unable to share: ${e.localizedMessage ?: e.message}")
                    }
                    showBottomSheet = false; selectedFile = null
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
                    if (renameText.isNotBlank()) viewModel.renameFile(selectedFile!!, renameText)
                    showRenameDialog = false; selectedFile = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete / Move-to-bin dialog ────────────────────────────────────────────
    if (showDeleteDialog) {
        val isMulti = selectedPaths.isNotEmpty()
        val singleTarget = if (!isMulti) selectedFile else null
        if (isMulti || singleTarget != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; if (!isMulti) selectedFile = null },
                title = { Text("Move to Recycle Bin") },
                text = {
                    if (isMulti) {
                        Text("Move ${selectedPaths.size} item(s) to the Recycle Bin?\n\nYou can restore them later.")
                    } else {
                        Text(
                            "Move \"${singleTarget!!.name}\" to the Recycle Bin?" +
                            (if (singleTarget.isDirectory) "\n\nAll contents inside will also be moved." else "") +
                            "\n\nYou can restore it later."
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (isMulti) {
                            selectedPaths.forEach { path -> viewModel.deleteFile(File(path)) }
                            selectedPaths = emptySet()
                        } else {
                            viewModel.deleteFile(singleTarget!!)
                            selectedFile = null
                        }
                        showDeleteDialog = false
                    }) { Text("Move to Bin", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; if (!isMulti) selectedFile = null }) { Text("Cancel") }
                }
            )
        }
    }

    // ── Zip name dialog ───────────────────────────────────────────────────────
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Create ZIP Archive") },
            text = {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (zipName.isNotBlank()) {
                        viewModel.zipFiles(selectedFiles(), zipName)
                        selectedPaths = emptySet()
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
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
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
            dismissButton = { TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AppNavigationDrawer(
    currentCollectionFilter: CollectionType?,
    onCloseDrawer: () -> Unit,
    onNavigateToInternalStorage: () -> Unit,
    onNavigateToCollection: (CollectionType) -> Unit,
    onNavigateToApplications: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val storageStats = remember {
        try {
            val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val total = statFs.blockCountLong * statFs.blockSizeLong
            val avail = statFs.availableBlocksLong * statFs.blockSizeLong
            val used = total - avail
            Triple(used, total, if (total > 0) used.toFloat() / total else 0f)
        } catch (_: Exception) {
            Triple(0L, 0L, 0f)
        }
    }
    val (usedBytes, totalBytes, usedFraction) = storageStats
    val usedPercent = (usedFraction * 100).toInt()

    fun formatStorageSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Files",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider()

        LazyColumn {
            // ── STORAGE SECTION ──────────────────────────────────────────────
            item {
                Text(
                    text = "STORAGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    label = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Internal Storage", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { usedFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (usedFraction > 0.9f) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatStorageSize(usedBytes)} used of ${formatStorageSize(totalBytes)} · $usedPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = currentCollectionFilter == null,
                    onClick = onNavigateToInternalStorage,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            // ── COLLECTIONS SECTION ──────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "COLLECTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                    label = { Text("Music") },
                    selected = currentCollectionFilter == CollectionType.MUSIC,
                    onClick = { onNavigateToCollection(CollectionType.MUSIC) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    label = { Text("Images") },
                    selected = currentCollectionFilter == CollectionType.IMAGES,
                    onClick = { onNavigateToCollection(CollectionType.IMAGES) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    label = { Text("Videos") },
                    selected = currentCollectionFilter == CollectionType.VIDEOS,
                    onClick = { onNavigateToCollection(CollectionType.VIDEOS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    label = { Text("Images & Videos") },
                    selected = currentCollectionFilter == CollectionType.IMAGES_VIDEOS,
                    onClick = { onNavigateToCollection(CollectionType.IMAGES_VIDEOS) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                    label = { Text("PDF") },
                    selected = currentCollectionFilter == CollectionType.PDF,
                    onClick = { onNavigateToCollection(CollectionType.PDF) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Android, contentDescription = null) },
                    label = { Text("Applications") },
                    selected = false,
                    onClick = onNavigateToApplications,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    label = { Text("Recycle Bin") },
                    selected = false,
                    onClick = onNavigateToRecycleBin,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            // ── BOOKMARK SECTION ─────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "BOOKMARK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    label = { Text("Favorites") },
                    selected = false,
                    onClick = onNavigateToFavorites,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            // ── SETTINGS SECTION ─────────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
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
    isFavorite: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isHidden = file.name.startsWith(".")
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) selectionColor else androidx.compose.ui.graphics.Color.Transparent)
            .alpha(if (isHidden) 0.5f else 1f)
            .combinedClickable(onClick = { if (isSelectionMode) onSelectionToggle() else onClick() }, onLongClick = onLongClick)
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
                        val showHidden = viewModel.uiState.collectAsState().value.showHiddenFiles
                        val itemCount = file.listFiles()?.count { showHidden || !it.isHidden } ?: 0
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

            // Star badge — visible when file is a favorite (only when not in selection mode)
            if (!isSelectionMode && isFavorite) {
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

@Composable
fun FileOperationsSheet(
    file: File,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onCut: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onUnzip: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
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

        // Favorite toggle
        FileOperationItem(
            icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            onClick = onToggleFavorite,
            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        // Share option (only for files)
        if (onShare != null && file.isFile) {
            FileOperationItem(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        // Cut / Copy
        if (onCut != null) {
            FileOperationItem(icon = Icons.Filled.ContentCut, label = "Cut", onClick = onCut)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (onCopy != null) {
            FileOperationItem(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        FileOperationItem(icon = Icons.Filled.DriveFileRenameOutline, label = "Rename", onClick = onRename)
        if (onUnzip != null) {
            FileOperationItem(icon = Icons.Filled.Archive, label = "Unzip here", onClick = onUnzip)
        }
        FileOperationItem(
            icon = Icons.Filled.Delete,
            label = "Move to Recycle Bin",
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

