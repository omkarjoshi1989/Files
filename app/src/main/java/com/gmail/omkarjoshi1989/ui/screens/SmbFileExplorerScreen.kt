package com.gmail.omkarjoshi1989.ui.screens

import android.os.Environment
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.model.CollectionType
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.SmbRemoteItem
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.SmbClientManager
import com.gmail.omkarjoshi1989.util.SmbConnectionsManager
import com.gmail.omkarjoshi1989.util.SmbStreamRegistry
import com.gmail.omkarjoshi1989.util.SubtitleSidecarResolver
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.gmail.omkarjoshi1989.viewmodel.ClipboardRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * A full-featured SMB/LAN file explorer that integrates into the existing
 * FileExplorerScreen layout â€” same drawer, same top-bar style, same file list
 * look, but operating over an SMB connection via [SmbClientManager].
 *
 * Supported operations:
 *  â€¢ Browse shares and directories
 *  â€¢ Open files (download to cache, then open with system/in-app viewer)
 *  â€¢ Long-press â†’ select â†’ Delete / Download / Rename
 *  â€¢ Create new folder on the remote share
 *  â€¢ Paste local clipboard files to the current SMB directory
 *  â€¢ Breadcrumb navigation bar (same style as local explorer)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmbFileExplorerScreen(
    connection: SmbConnectionConfig,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,                              // called with a locally-cached temp file
    onNavigateToFavorites: () -> Unit,
    onNavigateToApplications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToCollection: ((CollectionType) -> Unit)?,
    onNavigateToSmbConnection: ((SmbConnectionConfig) -> Unit)?,
    onAddSmbConnection: () -> Unit,
    onEditSmbConnection: (SmbConnectionConfig) -> Unit,
    onShowToast: (String) -> Unit
) {
    val largeMediaThresholdBytes = 128L * 1024L * 1024L
    val initialStreamBufferBytes = 16L * 1024L * 1024L
    val maxInitialBufferWaitMs = 20_000L

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Refresh SMB connections whenever the drawer is opened / activity resumes
    var smbConnections by remember { mutableStateOf(SmbConnectionsManager.getConnections(context)) }

    // â”€â”€ SMB navigation state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var currentShare by remember(connection.id) {
        mutableStateOf(connection.defaultShareName.takeIf { it.isNotBlank() })
    }
    var currentPath by remember(connection.id) { mutableStateOf("") }
    var shares by remember(connection.id) { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<SmbRemoteItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isOperating by remember { mutableStateOf(false) }   // true during delete/rename/paste/download
    var loadError by remember { mutableStateOf<String?>(null) }

    // â”€â”€ Selection mode â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedPaths.isNotEmpty()

    // â”€â”€ Dialog state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SmbRemoteItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }

    val clipboard by ClipboardRepository.clipboard.collectAsState()

    // â”€â”€ Load / refresh current location â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun refresh() {
        scope.launch {
            isLoading = true
            loadError = null
            runCatching {
                val loadedShares = SmbClientManager.listShares(connection)
                shares = loadedShares
                entries = if (currentShare != null) {
                    SmbClientManager.listDirectory(connection, currentShare!!, currentPath)
                } else {
                    emptyList()
                }
            }.onFailure {
                loadError = it.localizedMessage ?: "Unable to load SMB connection"
            }
            isLoading = false
        }
    }

    LaunchedEffect(connection.id, currentShare, currentPath) { refresh() }
    LaunchedEffect(loadError) { loadError?.let { snackbarHostState.showSnackbar(it) } }

    // â”€â”€ Navigate up (within SMB hierarchy) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    fun navigateUp(): Boolean {
        return when {
            currentPath.isNotBlank() -> {
                currentPath = currentPath.substringBeforeLast("\\", "")
                true
            }
            currentShare != null -> {
                currentShare = null
                currentPath = ""
                true
            }
            else -> false
        }
    }

    // â”€â”€ Back-press: clear selection first, then navigate up, then exit SMB â”€
    BackHandler(enabled = isSelectionMode) { selectedPaths = emptySet() }

    BackHandler(enabled = !isSelectionMode) {
        if (!navigateUp()) onNavigateBack()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode,
        drawerContent = {
            AppNavigationDrawer(
                currentCollectionFilter = null,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onNavigateToInternalStorage = {
                    scope.launch { drawerState.close() }
                    onNavigateBack()
                },
                onNavigateToCollection = { type ->
                    scope.launch { drawerState.close() }
                    onNavigateToCollection?.invoke(type)
                },
                onNavigateToApplications = {
                    scope.launch { drawerState.close() }
                    onNavigateToApplications()
                },
                onNavigateToRecycleBin = {
                    scope.launch { drawerState.close() }
                    onNavigateToRecycleBin()
                },
                onNavigateToFavorites = {
                    scope.launch { drawerState.close() }
                    onNavigateToFavorites()
                },
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                smbConnections = smbConnections,
                onAddSmbConnection = {
                    scope.launch { drawerState.close() }
                    smbConnections = SmbConnectionsManager.getConnections(context)
                    onAddSmbConnection()
                },
                onEditSmbConnection = { conn ->
                    scope.launch { drawerState.close() }
                    onEditSmbConnection(conn)
                },
                onOpenSmbConnection = { conn ->
                    scope.launch { drawerState.close() }
                    onNavigateToSmbConnection?.invoke(conn)
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },

            // â”€â”€ Paste FAB: upload local clipboard files to the current SMB directory
            floatingActionButton = {
                val clipData = clipboard
                if (clipData != null && currentShare != null && !isOperating && !isSelectionMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = { ClipboardRepository.clipboard.value = null },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear clipboard")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ExtendedFloatingActionButton(
                            onClick = {
                                val share = currentShare ?: return@ExtendedFloatingActionButton
                                scope.launch {
                                    isOperating = true
                                    try {
                                        SmbClientManager.pasteLocalClipboardToRemote(
                                            config = connection,
                                            shareName = share,
                                            remoteDirectoryPath = currentPath,
                                            clipboardData = clipData
                                        )
                                        ClipboardRepository.clipboard.value = null
                                        val msg = if (clipData.files.size == 1)
                                            "Pasted ${clipData.files[0].name}"
                                        else
                                            "Pasted ${clipData.files.size} items"
                                        snackbarHostState.showSnackbar(msg)
                                        refresh()
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(
                                            e.localizedMessage ?: "Paste to SMB failed"
                                        )
                                    }
                                    isOperating = false
                                }
                            },
                            icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            text = {
                                val action = if (clipData.operation == ClipboardOperation.CUT)
                                    "Move to SMB" else "Paste to SMB"
                                val count = clipData.files.size
                                Text(
                                    if (count == 1) "$action â€” ${clipData.files[0].name}"
                                    else "$action ($count items)"
                                )
                            }
                        )
                    }
                }
            },

            // â”€â”€ Bottom bar: selection mode actions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            bottomBar = {
                if (isSelectionMode) {
                    val selectedItems = entries.filter { it.path in selectedPaths }
                    val hasFiles = selectedItems.any { !it.isDirectory }
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
                            // Download (files only)
                            if (hasFiles) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            val share = currentShare ?: return@clickable
                                            scope.launch {
                                                isOperating = true
                                                val downloadDir = Environment.getExternalStoragePublicDirectory(
                                                    Environment.DIRECTORY_DOWNLOADS
                                                )
                                                var successCount = 0
                                                selectedItems.filter { !it.isDirectory }.forEach { item ->
                                                    runCatching {
                                                        val dest = File(downloadDir, item.name)
                                                        SmbClientManager.downloadFile(
                                                            connection, share, item.path, dest
                                                        )
                                                        successCount++
                                                    }.onFailure {
                                                        snackbarHostState.showSnackbar(
                                                            "Download failed: ${it.localizedMessage}"
                                                        )
                                                    }
                                                }
                                                if (successCount > 0) {
                                                    snackbarHostState.showSnackbar(
                                                        "Downloaded $successCount file(s) to Downloads"
                                                    )
                                                }
                                                selectedPaths = emptySet()
                                                isOperating = false
                                            }
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download")
                                    Text("Download", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Rename (only when exactly one item selected)
                            if (selectedPaths.size == 1) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            val item = selectedItems.firstOrNull() ?: return@clickable
                                            renameTarget = item
                                            renameText = item.name
                                            showRenameDialog = true
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                                    Text("Rename", style = MaterialTheme.typography.labelSmall)
                                }
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
                        }
                    }
                }
            },

            // â”€â”€ Top bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (isSelectionMode) {
                                Text("${selectedPaths.size} selected", fontWeight = FontWeight.Bold)
                            } else {
                                Column {
                                    Text(
                                        text = connection.displayName,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${connection.host}:${connection.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            val canNavigateUpInSmb = currentShare != null || currentPath.isNotBlank()
                            IconButton(onClick = {
                                when {
                                    isSelectionMode -> selectedPaths = emptySet()
                                    canNavigateUpInSmb -> navigateUp()
                                    else -> onNavigateBack()
                                }
                            }) {
                                Icon(
                                    when {
                                        isSelectionMode -> Icons.Filled.Close
                                        else -> Icons.AutoMirrored.Filled.ArrowBack
                                    },
                                    contentDescription = when {
                                        isSelectionMode -> "Clear selection"
                                        canNavigateUpInSmb -> "Back"
                                        else -> "Back to files"
                                    }
                                )
                            }
                        },
                        actions = {
                            if (!isSelectionMode) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                                }
                            }

                            if (isSelectionMode) {
                                // Select all toggle
                                IconButton(onClick = {
                                    selectedPaths = if (selectedPaths.size < entries.size)
                                        entries.map { it.path }.toSet()
                                    else emptySet()
                                }) {
                                    Icon(Icons.Filled.Apps, contentDescription = "Select all")
                                }
                            } else if (currentShare != null) {
                                // Create new folder on the remote share
                                IconButton(onClick = {
                                    createFolderName = ""
                                    showCreateFolderDialog = true
                                }) {
                                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    // Breadcrumb bar for SMB navigation
                    if (currentShare != null) {
                        SmbBreadcrumbBar(
                            connectionName = connection.displayName,
                            shareName = currentShare!!,
                            remotePath = currentPath,
                            onConnectionClick = {
                                currentShare = null
                                currentPath = ""
                            },
                            onShareClick = { currentPath = "" },
                            onSegmentClick = { segPath -> currentPath = segPath }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isLoading || isOperating -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    // â”€â”€ Share picker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    currentShare == null -> {
                        if (shares.isEmpty()) {
                            Text(
                                text = "No SMB shares found on ${connection.host}",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(shares, key = { it }) { share ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentShare = share
                                                currentPath = ""
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp).padding(4.dp)
                                        )
                                        Text(
                                            text = share,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                                }
                            }
                        }
                    }

                    // â”€â”€ Directory contents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    else -> {
                        if (entries.isEmpty()) {
                            Text(
                                text = "This folder is empty",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(entries, key = { it.path }) { item ->
                                    val isSelected = item.path in selectedPaths
                                    SmbFileListItem(
                                        item = item,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected)
                                                    selectedPaths - item.path
                                                else
                                                    selectedPaths + item.path
                                            } else {
                                                if (item.isDirectory) {
                                                    currentPath = item.path
                                                } else {
                                                    // Seek-safe behavior:
                                                    // - large videos: open via SMB random-access stream
                                                    // - large audio: start after initial buffered chunk
                                                    val share = currentShare ?: return@SmbFileListItem
                                                    scope.launch {
                                                        isOperating = true
                                                        runCatching {
                                                            val cacheFile = File(context.cacheDir, "smb_open_${item.name}")
                                                            val mediaTypeProbe = File(item.name)
                                                            val subtitleCandidate = if (FileUtils.isVideoFile(mediaTypeProbe)) {
                                                                SubtitleSidecarResolver.findBestMatchingSrt(
                                                                    videoName = item.name,
                                                                    candidates = entries.filter { !it.isDirectory },
                                                                    nameSelector = { it.name }
                                                                )
                                                            } else {
                                                                null
                                                            }
                                                            val isLargeMedia = item.size >= largeMediaThresholdBytes
                                                            val isLargeVideo = isLargeMedia && FileUtils.isVideoFile(mediaTypeProbe)
                                                            val isLargeAudio = isLargeMedia && FileUtils.isAudioFile(mediaTypeProbe)

                                                            if (isLargeVideo) {
                                                                val streamMarkerFile = SmbStreamRegistry.registerVideoStream(
                                                                    context = context,
                                                                    connection = connection,
                                                                    shareName = share,
                                                                    remotePath = item.path,
                                                                    displayName = item.name
                                                                )
                                                                subtitleCandidate?.let { subtitleItem ->
                                                                    val subtitleFile = File(
                                                                        streamMarkerFile.parentFile ?: context.cacheDir,
                                                                        "${streamMarkerFile.nameWithoutExtension}.srt"
                                                                    )
                                                                    runCatching {
                                                                        SmbClientManager.downloadFile(
                                                                            connection,
                                                                            share,
                                                                            subtitleItem.path,
                                                                            subtitleFile
                                                                        )
                                                                    }.onFailure {
                                                                        Log.w("SmbFileExplorer", "Failed to download SMB subtitle", it)
                                                                    }
                                                                }
                                                                onShowToast("Streaming video from SMB with random-seek support")
                                                                onOpenFile(streamMarkerFile)
                                                                return@runCatching
                                                            }

                                                            if (!isLargeAudio) {
                                                                SmbClientManager.downloadFile(
                                                                    connection,
                                                                    share,
                                                                    item.path,
                                                                    cacheFile
                                                                )
                                                                subtitleCandidate?.let { subtitleItem ->
                                                                    val subtitleFile = File(
                                                                        cacheFile.parentFile ?: context.cacheDir,
                                                                        "${cacheFile.nameWithoutExtension}.srt"
                                                                    )
                                                                    runCatching {
                                                                        SmbClientManager.downloadFile(
                                                                            connection,
                                                                            share,
                                                                            subtitleItem.path,
                                                                            subtitleFile
                                                                        )
                                                                    }.onFailure {
                                                                        Log.w("SmbFileExplorer", "Failed to download SMB subtitle", it)
                                                                    }
                                                                }
                                                                onOpenFile(cacheFile)
                                                                return@runCatching
                                                            }

                                                            val initialTargetBytes = minOf(
                                                                initialStreamBufferBytes,
                                                                item.size.coerceAtLeast(0L)
                                                            )
                                                            val downloadJob = launch {
                                                                SmbClientManager.downloadFile(
                                                                    connection,
                                                                    share,
                                                                    item.path,
                                                                    cacheFile
                                                                )
                                                            }

                                                            val startedAt = System.currentTimeMillis()
                                                            while (
                                                                downloadJob.isActive &&
                                                                cacheFile.length() < initialTargetBytes &&
                                                                System.currentTimeMillis() - startedAt < maxInitialBufferWaitMs
                                                            ) {
                                                                delay(150)
                                                            }

                                                            if (cacheFile.length() > 0L) {
                                                                onOpenFile(cacheFile)
                                                            } else {
                                                                downloadJob.join()
                                                                if (cacheFile.length() > 0L) {
                                                                    onOpenFile(cacheFile)
                                                                } else {
                                                                    error("Buffered playback could not start")
                                                                }
                                                            }
                                                        }.onFailure {
                                                            onShowToast(
                                                                "Cannot open file: ${it.localizedMessage ?: "Unknown error"}"
                                                            )
                                                        }
                                                        isOperating = false
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            selectedPaths = selectedPaths + item.path
                                        }
                                    )
                                }
                            }

                        }
                    }
                }
            }
        }
    } // end ModalNavigationDrawer

    // â”€â”€ Delete confirmation dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (showDeleteDialog) {
        val toDelete = entries.filter { it.path in selectedPaths }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete") },
            text = {
                Text(
                    if (toDelete.size == 1)
                        "Permanently delete \"${toDelete[0].name}\" from the SMB share?\nThis cannot be undone."
                    else
                        "Permanently delete ${toDelete.size} item(s) from the SMB share?\nThis cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    val share = currentShare ?: return@TextButton
                    scope.launch {
                        isOperating = true
                        var failCount = 0
                        toDelete.forEach { item ->
                            runCatching {
                                SmbClientManager.deleteEntry(
                                    connection, share, item.path, item.isDirectory
                                )
                            }.onFailure { failCount++ }
                        }
                        selectedPaths = emptySet()
                        if (failCount > 0) {
                            snackbarHostState.showSnackbar("$failCount item(s) could not be deleted")
                        } else {
                            snackbarHostState.showSnackbar(
                                if (toDelete.size == 1) "Deleted ${toDelete[0].name}"
                                else "Deleted ${toDelete.size} items"
                            )
                        }
                        refresh()
                        isOperating = false
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // â”€â”€ Rename dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renameTarget = null },
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
                    val target = renameTarget ?: return@TextButton
                    val share = currentShare ?: return@TextButton
                    if (renameText.isBlank()) return@TextButton
                    showRenameDialog = false
                    renameTarget = null
                    scope.launch {
                        isOperating = true
                        runCatching {
                            SmbClientManager.renameEntry(connection, share, target.path, renameText)
                            selectedPaths = emptySet()
                            refresh()
                        }.onFailure {
                            snackbarHostState.showSnackbar(
                                "Rename failed: ${it.localizedMessage ?: "Unknown error"}"
                            )
                        }
                        isOperating = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // â”€â”€ Create folder dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = currentShare ?: return@TextButton
                    if (createFolderName.isBlank()) return@TextButton
                    showCreateFolderDialog = false
                    scope.launch {
                        isOperating = true
                        val newPath = if (currentPath.isBlank()) createFolderName
                                      else "$currentPath\\$createFolderName"
                        runCatching {
                            SmbClientManager.createDirectory(connection, share, newPath)
                            refresh()
                        }.onFailure {
                            snackbarHostState.showSnackbar(
                                "Create folder failed: ${it.localizedMessage ?: "Unknown error"}"
                            )
                        }
                        isOperating = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// â”€â”€ SMB breadcrumb navigation bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SmbBreadcrumbBar(
    connectionName: String,
    shareName: String,
    remotePath: String,
    onConnectionClick: () -> Unit,
    onShareClick: () -> Unit,
    onSegmentClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val segments = if (remotePath.isBlank()) emptyList()
                   else remotePath.split("\\").filter { it.isNotEmpty() }

    LaunchedEffect(remotePath) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection
        TextButton(onClick = onConnectionClick) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = connectionName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
        }
        Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Share
        TextButton(onClick = onShareClick) {
            Text(
                text = shareName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal
            )
        }

        // Sub-path segments
        segments.forEachIndexed { index, segment ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val segPath = segments.subList(0, index + 1).joinToString("\\")
            TextButton(onClick = { onSegmentClick(segPath) }) {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// â”€â”€ SMB file list item (same look as local FileListItem) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmbFileListItem(
    item: SmbRemoteItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.then(
                    androidx.compose.ui.Modifier
                ) else Modifier
            )
            .combinedClickable(onClick = { if (isSelectionMode) onLongClick() else onClick() }, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected)
                        Modifier.then(androidx.compose.ui.Modifier.then(
                            object : androidx.compose.ui.Modifier.Element {}
                        ))
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File icon
            Icon(
                imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp).padding(4.dp)
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
                if (!item.isDirectory && item.size >= 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = FileUtils.formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Selected",
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
