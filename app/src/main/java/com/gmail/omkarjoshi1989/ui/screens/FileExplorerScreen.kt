package com.gmail.omkarjoshi1989.ui.screens

import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gmail.omkarjoshi1989.MediaViewerActivity
import com.gmail.omkarjoshi1989.MusicPlayerActivity
import com.gmail.omkarjoshi1989.model.CollectionType
import com.gmail.omkarjoshi1989.model.FileItem
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.folderContainsMatchingFiles
import com.gmail.omkarjoshi1989.model.matchesFile
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.ui.components.FileThumbnailGrid
import com.gmail.omkarjoshi1989.ui.components.VideoProgressBar
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.MusicResumeManager
import com.gmail.omkarjoshi1989.util.SmbConnectionsManager
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.gmail.omkarjoshi1989.viewmodel.DeleteProgressState
import com.gmail.omkarjoshi1989.viewmodel.FileSortOption
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import com.gmail.omkarjoshi1989.viewmodel.PasteProgressState
import com.gmail.omkarjoshi1989.viewmodel.UnzipProgressState
import com.gmail.omkarjoshi1989.viewmodel.ViewMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    onNavigateToInternalStorage: (() -> Unit)? = null,
    onNavigateToGlobalSearch: (() -> Unit)? = null,
    onNavigateToSmbConnection: ((SmbConnectionConfig) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val unzipProgress by viewModel.unzipProgress.collectAsState()
    val pasteProgress by viewModel.pasteProgress.collectAsState()
    val deleteProgress by viewModel.deleteProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }
    var smbConnections by remember { mutableStateOf(SmbConnectionsManager.getConnections(context)) }
    var showSmbDialog by remember { mutableStateOf(false) }
    var editingSmbConnection by remember { mutableStateOf<SmbConnectionConfig?>(null) }

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
    //
    // PERFORMANCE: filtering runs on Dispatchers.IO (folderContainsMatchingFiles can
    // recurse through thousands of files on large devices like DCIM with 1000+ items).
    // Results are cached inside CollectionType so subsequent calls are instant.
    var displayFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    // Tracks whether the async collection-filter pass is still running so we can
    // show a loading indicator instead of a premature "no files found" message.
    var isFilteringFiles by remember { mutableStateOf(collectionFilter != null) }
    LaunchedEffect(uiState.files, collectionFilter) {
        if (collectionFilter != null) {
            isFilteringFiles = true
            displayFiles = withContext(Dispatchers.IO) {
                uiState.files.filter { item ->
                    if (item.isDirectory) collectionFilter.folderContainsMatchingFiles(item.file)
                    else collectionFilter.matchesFile(item.file)
                }
            }
            isFilteringFiles = false
        } else {
            isFilteringFiles = false
            displayFiles = uiState.files
        }
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
                // Lightweight freshness check — does NOT invalidate the cache.
                // Shows cached data instantly; silently reloads only if the directory
                // actually changed (e.g. camera added a new photo).  Avoids the 8-10 s
                // spinner previously triggered by returning from the photo viewer.
                viewModel.requestFreshnessCheck()
                favoritePaths = FavoritesManager.getFavorites(context)
                smbConnections = SmbConnectionsManager.getConnections(context)
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
                },
                smbConnections = smbConnections,
                onAddSmbConnection = {
                    coroutineScope.launch { drawerState.close() }
                    editingSmbConnection = null
                    showSmbDialog = true
                },
                onEditSmbConnection = { connection ->
                    coroutineScope.launch { drawerState.close() }
                    editingSmbConnection = connection
                    showSmbDialog = true
                },
                onOpenSmbConnection = { connection ->
                    coroutineScope.launch { drawerState.close() }
                    onNavigateToSmbConnection?.invoke(connection)
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
                // True when every selected item is a .zip file
                val allSelectedAreZips = selectedPaths.isNotEmpty() &&
                    selectedPaths.all { File(it).extension.equals("zip", ignoreCase = true) }

                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
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
                        // Unzip — shown only when ALL selected items are .zip files
                        if (allSelectedAreZips) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.unzipFiles(selectedFiles())
                                        selectedPaths = emptySet()
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Filled.Archive, contentDescription = "Unzip")
                                Text("Unzip", style = MaterialTheme.typography.labelSmall)
                            }
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
                        // Favorites toggle
                        val allFavorite = selectedPaths.isNotEmpty() && selectedPaths.all { it in favoritePaths }
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
                                contentDescription = if (allFavorite) "Remove from Favorites" else "Add to Favorites",
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
                        } else {
                            // Three-dots menu stays in the TopAppBar
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    if (onNavigateToGlobalSearch != null) {
                                        DropdownMenuItem(
                                            text = { Text("Search All Files") },
                                            onClick = {
                                                showMoreMenu = false
                                                onNavigateToGlobalSearch()
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                                        )
                                        HorizontalDivider()
                                    }
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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                // ── Secondary action toolbar (hidden in selection mode) ────────
                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Music player launcher
                        IconButton(onClick = {
                            val lastFile = MusicResumeManager.getLastFilePath(context)
                            val lastFolder = MusicResumeManager.getLastFolderPath(context)
                            if (lastFile != null && lastFolder != null && java.io.File(lastFile).exists()) {
                                val intent = Intent(context, MusicPlayerActivity::class.java).apply {
                                    putExtra(MusicPlayerActivity.EXTRA_FILE_PATH, lastFile)
                                    putExtra(MusicPlayerActivity.EXTRA_FOLDER_PATH, lastFolder)
                                    putExtra(MusicPlayerActivity.EXTRA_NO_AUTOPLAY, true)
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

                        // Show / hide hidden files toggle
                        IconButton(onClick = {
                            val newValue = !uiState.showHiddenFiles
                            com.gmail.omkarjoshi1989.util.SettingsManager.setShowHiddenFiles(context, newValue)
                        }) {
                            Icon(
                                if (uiState.showHiddenFiles) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (uiState.showHiddenFiles) "Hide hidden files" else "Show hidden files"
                            )
                        }

                        // Search
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = if (uiState.isSearchActive) "Close search" else "Search",
                                tint = if (uiState.isSearchActive) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // New file / folder
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Create new")
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    onClick = {
                                        createName = ""
                                        showCreateFolderDialog = true
                                        showAddMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    onClick = {
                                        createName = ""
                                        showCreateFileDialog = true
                                        showAddMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) }
                                )
                            }
                        }

                        // List / Grid toggle
                        IconButton(onClick = {
                            val next = if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            viewModel.setViewMode(next)
                        }) {
                            Icon(
                                if (uiState.viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                                contentDescription = if (uiState.viewMode == ViewMode.LIST) "Switch to grid" else "Switch to list"
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

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

            if (uiState.viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                ) {
                    items(displayFiles, key = { it.absolutePath }) { item ->
                        val isSelected = item.absolutePath in selectedPaths
                        FileGridItem(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isFavorite = item.absolutePath in favoritePaths,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedPaths = if (isSelected)
                                        selectedPaths - item.absolutePath
                                    else
                                        selectedPaths + item.absolutePath
                                } else {
                                    if (item.isDirectory) viewModel.navigateTo(item.absolutePath)
                                    else onOpenFile(item.file)
                                }
                            },
                            onLongClick = {
                                selectedPaths = selectedPaths + item.absolutePath
                            }
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayFiles, key = { it.absolutePath }) { item ->
                        val isSelected = item.absolutePath in selectedPaths
                        FileListItem(
                            item = item,
                            viewModel = viewModel,
                            showHiddenFiles = uiState.showHiddenFiles,
                            isFavorite = item.absolutePath in favoritePaths,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onSelectionToggle = {
                                selectedPaths = if (isSelected)
                                    selectedPaths - item.absolutePath
                                else
                                    selectedPaths + item.absolutePath
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedPaths = if (isSelected)
                                        selectedPaths - item.absolutePath
                                    else
                                        selectedPaths + item.absolutePath
                                } else {
                                    if (item.isDirectory) viewModel.navigateTo(item.absolutePath)
                                    else onOpenFile(item.file)
                                }
                            },
                            onLongClick = {
                                selectedPaths = selectedPaths + item.absolutePath
                            }
                        )
                    }
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

    // ── In-app unzip progress overlay ─────────────────────────────────────────
    unzipProgress?.let { progress ->
        UnzipProgressDialog(progress = progress)
    }

    // ── In-app paste progress overlay ─────────────────────────────────────────
    pasteProgress?.let { progress ->
        PasteProgressDialog(progress = progress)
    }

    // ── In-app delete progress overlay ────────────────────────────────────────
    deleteProgress?.let { progress ->
        DeleteProgressDialog(progress = progress)
    }

    if (showSmbDialog) {
        SmbConnectionDialog(
            initialValue = editingSmbConnection,
            onDismiss = {
                showSmbDialog = false
                editingSmbConnection = null
            },
            onSave = { config ->
                SmbConnectionsManager.saveConnection(context, config)
                smbConnections = SmbConnectionsManager.getConnections(context)
                showSmbDialog = false
                editingSmbConnection = null
            },
            onDelete = { config ->
                SmbConnectionsManager.deleteConnection(context, config.id)
                smbConnections = SmbConnectionsManager.getConnections(context)
                showSmbDialog = false
                editingSmbConnection = null
            }
        )
    }
}

@Composable
internal fun AppNavigationDrawer(
    currentCollectionFilter: CollectionType?,
    onCloseDrawer: () -> Unit,
    onNavigateToInternalStorage: () -> Unit,
    onNavigateToCollection: (CollectionType) -> Unit,
    onNavigateToApplications: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    smbConnections: List<SmbConnectionConfig>,
    onAddSmbConnection: () -> Unit,
    onEditSmbConnection: (SmbConnectionConfig) -> Unit,
    onOpenSmbConnection: (SmbConnectionConfig) -> Unit
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
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Lan, contentDescription = null) },
                    label = { Text("Add LAN/SMB Connection") },
                    selected = false,
                    onClick = onAddSmbConnection,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            if (smbConnections.isNotEmpty()) {
                items(smbConnections, key = { it.id }) { connection ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Dns, contentDescription = null) },
                        label = {
                            Column {
                                Text(connection.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${connection.host}:${connection.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        selected = false,
                        onClick = { onOpenSmbConnection(connection) },
                        badge = {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit SMB connection",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onEditSmbConnection(connection) }
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
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
fun FileGridItem(
    item: FileItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Card(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .alpha(if (item.isHidden) 0.5f else 1f)
            .combinedClickable(
                onClick = { if (isSelectionMode) onLongClick() else onClick() },
                onLongClick = onLongClick
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Full-bleed thumbnail ──────────────────────────────────────────
            FileThumbnailGrid(
                absolutePath = item.absolutePath,
                isDirectory  = item.isDirectory,
                extension    = item.extension,
                lastModified = item.lastModified,
                modifier     = Modifier.fillMaxSize()
            )

            // ── Selection tint overlay ────────────────────────────────────────
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(selectionColor)
                )
            }

            // ── Bottom gradient scrim + file name ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text     = item.name,
                    color    = Color.White,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Favorite star badge (top-end) ─────────────────────────────────
            if (!isSelectionMode && isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                )
            }

            // ── Selection check badge (top-start) ─────────────────────────────
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .align(Alignment.TopStart)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    viewModel: FileExplorerViewModel,
    showHiddenFiles: Boolean = false,
    isFavorite: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // All metadata comes from pre-fetched FileItem fields — zero filesystem calls on main thread
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) selectionColor else androidx.compose.ui.graphics.Color.Transparent)
            .alpha(if (item.isHidden) 0.5f else 1f)
            .combinedClickable(onClick = { if (isSelectionMode) onSelectionToggle() else onClick() }, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pass pre-fetched metadata so FileThumbnail never calls stat() on main thread
            FileThumbnail(
                absolutePath = item.absolutePath,
                isDirectory  = item.isDirectory,
                extension    = item.extension,
                lastModified = item.lastModified,
                size         = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // ── Video progress bar (only for video files) ─────────────────
                if (!item.isDirectory && item.extension in videoExtensionsSet) {
                    VideoProgressBar(file = item.file)
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!item.isDirectory) {
                        // size is pre-fetched — no stat() call here
                        Text(
                            text = viewModel.formatFileSize(item.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Folder item-count + direct-file size: computed lazily off main thread
                        data class FolderInfo(val itemCount: Int, val directSize: Long)
                        val folderInfo by produceState<FolderInfo?>(
                            initialValue = null,
                            key1 = item.absolutePath,
                            key2 = showHiddenFiles
                        ) {
                            value = withContext(Dispatchers.IO) {
                                val entries = item.file.listFiles()
                                    ?.filter { showHiddenFiles || !it.name.startsWith(".") }
                                    ?: emptyList()
                                val count = entries.size
                                val size = entries.filter { it.isFile }.sumOf { it.length() }
                                FolderInfo(count, size)
                            }
                        }
                        Text(
                            text = if (folderInfo != null)
                                if (folderInfo!!.directSize > 0)
                                    "${folderInfo!!.itemCount} items · ${FileUtils.formatFileSize(folderInfo!!.directSize)}"
                                else
                                    "${folderInfo!!.itemCount} items"
                            else "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── PDF reading progress % ────────────────────────────────
                    if (!item.isDirectory && item.extension == "pdf") {
                        val pdfCtx = LocalContext.current
                        var pdfResumeTick by remember(item.absolutePath) { mutableStateOf(0) }
                        val pdfLifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(pdfLifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) pdfResumeTick++
                            }
                            pdfLifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { pdfLifecycleOwner.lifecycle.removeObserver(observer) }
                        }
                        val pdfPercent by produceState<Int?>(
                            initialValue = null,
                            key1 = item.absolutePath,
                            key2 = pdfResumeTick
                        ) {
                            value = withContext(Dispatchers.IO) {
                                val prefs = pdfCtx.getSharedPreferences(
                                    "pdf_scroll_positions",
                                    android.content.Context.MODE_PRIVATE
                                )
                                val prefKey = "${item.absolutePath.length}_${item.absolutePath.hashCode()}"
                                val page  = prefs.getInt("${prefKey}_p", -1)
                                val total = prefs.getInt("${prefKey}_total", 0)
                                if (total <= 0 || page < 0) null
                                else ((page + 1) * 100 / total).coerceIn(1, 99)
                            }
                        }
                        pdfPercent?.let { pct ->
                            Text(
                                text  = "$pct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Star badge
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

// Video extensions used in FileListItem to decide whether to show the progress bar
private val videoExtensionsSet = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"
)

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

/**
 * Non-dismissible in-app overlay shown while a paste (cut/copy) operation is running.
 * Mirrors the style of [UnzipProgressDialog] for a consistent UX.
 */
@Composable
fun PasteProgressDialog(progress: PasteProgressState) {
    val fraction = if (progress.total > 0)
        progress.current.toFloat() / progress.total.toFloat()
    else 0f

    AlertDialog(
        onDismissRequest = { /* not dismissible while running */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text(
                    text = "${progress.operationVerb}…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Current file name
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress bar + counters
                if (progress.total > 1) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${progress.current} / ${progress.total} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Single file — indeterminate since we don't track byte-level progress
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        // No buttons — dialog auto-dismisses when _pasteProgress becomes null
        confirmButton = {}
    )
}

/**
 * Non-dismissible in-app overlay shown while a ZIP extraction is running.
 * Mirrors the notification-panel progress but displayed right inside the activity.
 */
@Composable
fun UnzipProgressDialog(progress: UnzipProgressState) {
    val fraction = if (progress.total > 0)
        progress.current.toFloat() / progress.total.toFloat()
    else 0f

    AlertDialog(
        onDismissRequest = { /* not dismissible while running */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text(
                    text = if (progress.totalFiles > 1)
                        "Unzipping ${progress.fileIndex + 1} of ${progress.totalFiles}"
                    else
                        "Unzipping…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Archive name
                Text(
                    text = progress.archiveName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress bar + counters
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${progress.current} / ${progress.total} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Indeterminate while counting entries
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Current entry name
                if (progress.currentEntry.isNotBlank() && progress.currentEntry != "Preparing…") {
                    Text(
                        text = progress.currentEntry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        // No buttons — dialog auto-dismisses when _unzipProgress becomes null
        confirmButton = {}
    )
}

/**
 * Non-dismissible in-app overlay shown while files/folders are being moved to the Recycle Bin.
 * Mirrors the style of [UnzipProgressDialog] and [PasteProgressDialog] for consistent UX.
 */
@Composable
fun DeleteProgressDialog(progress: DeleteProgressState) {
    val fraction = if (progress.total > 0)
        progress.current.toFloat() / progress.total.toFloat()
    else 0f

    AlertDialog(
        onDismissRequest = { /* not dismissible while running */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Text(
                    text = "Moving to Recycle Bin…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Current file/folder name
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress bar + counters
                if (progress.total > 1) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${progress.current} / ${progress.total} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Single file/folder — indeterminate (no byte-level tracking)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        // No buttons — dialog auto-dismisses when _deleteProgress becomes null
        confirmButton = {}
    )
}



