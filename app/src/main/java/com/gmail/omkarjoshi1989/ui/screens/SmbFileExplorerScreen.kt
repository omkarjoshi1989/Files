package com.gmail.omkarjoshi1989.ui.screens
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AssignmentReturn
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
import androidx.core.content.ContextCompat
import com.gmail.omkarjoshi1989.model.CollectionType
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.SmbRemoteItem
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.SmbClientManager
import com.gmail.omkarjoshi1989.util.SmbConnectionsManager
import com.gmail.omkarjoshi1989.util.SmbStreamRegistry
import com.gmail.omkarjoshi1989.service.SmbDownloadService
import com.gmail.omkarjoshi1989.service.SmbUploadService
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.gmail.omkarjoshi1989.viewmodel.ClipboardRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
/**
 * Full-featured SMB/LAN file explorer -- same drawer/top-bar style as the local explorer.
 *
 * Supported operations:
 *  - Browse shares and directories
 *  - Open files (download to cache, then open with system/in-app viewer)
 *  - Long-press -> select -> Delete / Download / Rename
 *  - Create new folder on the remote share
 *  - Paste local clipboard files to current SMB directory (phone -> laptop)
 *  - Breadcrumb navigation bar
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmbFileExplorerScreen(
    connection: SmbConnectionConfig,
    onNavigateBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToApplications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToBackgroundOperations: () -> Unit,
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
    var smbConnections by remember { mutableStateOf(SmbConnectionsManager.getConnections(context)) }
    var currentShare by remember(connection.id) {
        mutableStateOf(connection.defaultShareName.takeIf { it.isNotBlank() })
    }
    var currentPath by remember(connection.id) { mutableStateOf("") }
    var shares by remember(connection.id) { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<SmbRemoteItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isOperating by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedPaths.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SmbRemoteItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }
    val clipboard by ClipboardRepository.clipboard.collectAsState()
    fun refresh() {
        scope.launch {
            isLoading = true; loadError = null
            runCatching {
                shares = SmbClientManager.listShares(connection)
                entries = if (currentShare != null)
                    SmbClientManager.listDirectory(connection, currentShare!!, currentPath)
                else emptyList()
            }.onFailure { loadError = it.localizedMessage ?: "Unable to load SMB connection" }
            isLoading = false
        }
    }
    LaunchedEffect(connection.id, currentShare, currentPath) { refresh() }
    LaunchedEffect(loadError) { loadError?.let { snackbarHostState.showSnackbar(it) } }
    fun navigateUp(): Boolean = when {
        currentPath.isNotBlank() -> { currentPath = currentPath.substringBeforeLast("\\", ""); true }
        currentShare != null -> { currentShare = null; currentPath = ""; true }
        else -> false
    }
    BackHandler(enabled = isSelectionMode) { selectedPaths = emptySet() }
    BackHandler(enabled = isOperating) {}
    BackHandler(enabled = !isSelectionMode && !isOperating) { if (!navigateUp()) onNavigateBack() }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode,
        drawerContent = {
            AppNavigationDrawer(
                currentCollectionFilter = null,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onNavigateToInternalStorage = { scope.launch { drawerState.close() }; onNavigateBack() },
                onNavigateToCollection = { type ->
                    scope.launch { drawerState.close() }; onNavigateToCollection?.invoke(type)
                },
                onNavigateToApplications = { scope.launch { drawerState.close() }; onNavigateToApplications() },
                onNavigateToRecycleBin  = { scope.launch { drawerState.close() }; onNavigateToRecycleBin() },
                onNavigateToBackgroundOperations = { scope.launch { drawerState.close() }; onNavigateToBackgroundOperations() },
                onNavigateToFavorites   = { scope.launch { drawerState.close() }; onNavigateToFavorites() },
                onNavigateToSettings    = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                smbConnections = smbConnections,
                onAddSmbConnection = {
                    scope.launch { drawerState.close() }
                    smbConnections = SmbConnectionsManager.getConnections(context)
                    onAddSmbConnection()
                },
                onEditSmbConnection = { conn ->
                    scope.launch { drawerState.close() }; onEditSmbConnection(conn)
                },
                onOpenSmbConnection = { conn ->
                    scope.launch { drawerState.close() }; onNavigateToSmbConnection?.invoke(conn)
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // -- Paste FAB: upload local clipboard files to current SMB directory --
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
                                val localPaths = ArrayList(clipData.files.map { it.absolutePath })
                                val uploadIntent = Intent(context, SmbUploadService::class.java).apply {
                                    action = SmbUploadService.ACTION_START_UPLOAD
                                    putExtra(SmbUploadService.EXTRA_CONNECTION_ID, connection.id)
                                    putExtra(SmbUploadService.EXTRA_SHARE_NAME, share)
                                    putExtra(SmbUploadService.EXTRA_REMOTE_DIRECTORY_PATH, currentPath)
                                    putStringArrayListExtra(SmbUploadService.EXTRA_LOCAL_PATHS, localPaths)
                                    putExtra(SmbUploadService.EXTRA_CLIPBOARD_OPERATION, clipData.operation.name)
                                }
                                ContextCompat.startForegroundService(context, uploadIntent)
                                ClipboardRepository.clipboard.value = null
                                scope.launch {
                                    val action = if (clipData.operation == ClipboardOperation.CUT) "move" else "upload"
                                    snackbarHostState.showSnackbar("Background SMB $action started")
                                }
                            },
                            icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            text = {
                                val action = if (clipData.operation == ClipboardOperation.CUT) "Move to SMB" else "Paste to SMB"
                                val count = clipData.files.size
                                Text(if (count == 1) "$action - ${clipData.files[0].name}" else "$action ($count items)")
                            }
                        )
                    }
                }
            },
            // -- Bottom bar: selection mode actions --
            bottomBar = {
                if (isSelectionMode) {
                    val selectedItems = entries.filter { it.path in selectedPaths }
                    val hasSelectedItems = selectedItems.isNotEmpty()
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasSelectedItems) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val share = currentShare ?: return@clickable
                                        val paths = ArrayList(selectedItems.map { it.path })
                                        val names = ArrayList(selectedItems.map { it.name })
                                        val dirs = BooleanArray(selectedItems.size) { idx -> selectedItems[idx].isDirectory }

                                        val svcIntent = Intent(context, SmbDownloadService::class.java).apply {
                                            action = SmbDownloadService.ACTION_START_DOWNLOAD
                                            putExtra(SmbDownloadService.EXTRA_CONNECTION_ID, connection.id)
                                            putExtra(SmbDownloadService.EXTRA_SHARE_NAME, share)
                                            putStringArrayListExtra(SmbDownloadService.EXTRA_REMOTE_PATHS, paths)
                                            putStringArrayListExtra(SmbDownloadService.EXTRA_REMOTE_NAMES, names)
                                            putExtra(SmbDownloadService.EXTRA_REMOTE_IS_DIR, dirs)
                                        }
                                        ContextCompat.startForegroundService(context, svcIntent)
                                        selectedPaths = emptySet()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("SMB download started in background")
                                        }
                                    }.padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download")
                                    Text("Download", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (selectedPaths.size == 1) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val item = selectedItems.firstOrNull() ?: return@clickable
                                        renameTarget = item; renameText = item.name; showRenameDialog = true
                                    }.padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                                    Text("Rename", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { showDeleteDialog = true }.padding(8.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            // -- Top bar --
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (isSelectionMode) {
                                Text("${selectedPaths.size} selected", fontWeight = FontWeight.Bold)
                            } else {
                                Column {
                                    Text(text = connection.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    if (isSelectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isSelectionMode) "Clear selection"
                                                        else if (canNavigateUpInSmb) "Back" else "Back to files"
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
                                IconButton(onClick = {
                                    selectedPaths = if (selectedPaths.size < entries.size)
                                        entries.map { it.path }.toSet() else emptySet()
                                }) {
                                    Icon(Icons.Filled.Apps, contentDescription = "Select all")
                                }
                            } else if (currentShare != null) {
                                IconButton(onClick = { createFolderName = ""; showCreateFolderDialog = true }) {
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
                    if (currentShare != null) {
                        SmbBreadcrumbBar(
                            connectionName = connection.displayName,
                            shareName = currentShare!!,
                            remotePath = currentPath,
                            onConnectionClick = { currentShare = null; currentPath = "" },
                            onShareClick = { currentPath = "" },
                            onSegmentClick = { segPath -> currentPath = segPath }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    isLoading || isOperating -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    // -- Share picker --
                    currentShare == null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Clipboard-pending banner: visible before a share is selected
                            val pendingClip = clipboard
                            if (pendingClip != null) {
                                val verb = if (pendingClip.operation == ClipboardOperation.CUT) "move" else "paste"
                                val label = if (pendingClip.files.size == 1)
                                    "${pendingClip.files[0].name} ready to $verb -- select a share"
                                else
                                    "${pendingClip.files.size} items ready to $verb -- select a share"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AssignmentReturn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { ClipboardRepository.clipboard.value = null }) {
                                        Text("Clear", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                            if (shares.isEmpty()) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No SMB shares found on ${connection.host}",
                                        modifier = Modifier.padding(32.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(shares, key = { it }) { share ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { currentShare = share; currentPath = "" }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp).padding(4.dp))
                                            Text(text = share, style = MaterialTheme.typography.bodyLarge)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                                    }
                                }
                            }
                        }
                    }
                    // -- Directory contents --
                    else -> {
                        if (entries.isEmpty()) {
                            Text(
                                text = "This folder is empty",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
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
                                                selectedPaths = if (isSelected) selectedPaths - item.path else selectedPaths + item.path
                                            } else {
                                                if (item.isDirectory) {
                                                    currentPath = item.path
                                                } else {
                                                    val share = currentShare ?: return@SmbFileListItem
                                                    scope.launch {
                                                        isOperating = true
                                                        operationMessage = "Preparing SMB file download..."
                                                        runCatching {
                                                            val cacheFile = File(context.cacheDir, "smb_open_${item.name}")
                                                            val mediaTypeProbe = File(item.name)
                                                            val isVideo = FileUtils.isVideoFile(mediaTypeProbe)
                                                            val isLargeMedia = item.size >= largeMediaThresholdBytes
                                                            val isLargeAudio = isLargeMedia && FileUtils.isAudioFile(mediaTypeProbe)
                                                            if (isVideo) {
                                                                val streamMarkerFile = SmbStreamRegistry.registerVideoStream(context = context, connection = connection, shareName = share, remotePath = item.path, displayName = item.name)
                                                                onShowToast("Opening SMB video with seekable streaming")
                                                                onOpenFile(streamMarkerFile); return@runCatching
                                                            }
                                                            if (!isLargeAudio) {
                                                                SmbClientManager.downloadFile(connection, share, item.path, cacheFile)
                                                                onOpenFile(cacheFile); return@runCatching
                                                            }
                                                            val initialTargetBytes = minOf(initialStreamBufferBytes, item.size.coerceAtLeast(0L))
                                                            val downloadJob = launch { SmbClientManager.downloadFile(connection, share, item.path, cacheFile) }
                                                            val startedAt = System.currentTimeMillis()
                                                            while (downloadJob.isActive && cacheFile.length() < initialTargetBytes && System.currentTimeMillis() - startedAt < maxInitialBufferWaitMs) { delay(150) }
                                                            if (cacheFile.length() > 0L) {
                                                                onOpenFile(cacheFile)
                                                            } else {
                                                                downloadJob.join()
                                                                if (cacheFile.length() > 0L) onOpenFile(cacheFile)
                                                                else error("Buffered playback could not start")
                                                            }
                                                        }.onFailure { onShowToast("Cannot open file: ${it.localizedMessage ?: "Unknown error"}") }
                                                        operationMessage = null; isOperating = false
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = { selectedPaths = selectedPaths + item.path }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } // end ModalNavigationDrawer
    // -- Delete confirmation dialog --
    if (showDeleteDialog) {
        val toDelete = entries.filter { it.path in selectedPaths }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete") },
            text = {
                Text(
                    if (toDelete.size == 1) "Permanently delete \"${toDelete[0].name}\" from the SMB share?\nThis cannot be undone."
                    else "Permanently delete ${toDelete.size} item(s) from the SMB share?\nThis cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    val share = currentShare ?: return@TextButton
                    scope.launch {
                        isOperating = true
                        val failedDeletes = mutableListOf<String>()
                        toDelete.forEach { item ->
                            runCatching {
                                SmbClientManager.deleteEntry(connection, share, item.path, item.isDirectory)
                            }.onFailure { err ->
                                val reason = err.localizedMessage ?: "Unknown SMB error"
                                failedDeletes += "${item.name}: $reason"
                            }
                        }
                        selectedPaths = emptySet()
                        val failCount = failedDeletes.size
                        val successCount = toDelete.size - failCount
                        val message = when {
                            failCount == 0 && toDelete.size == 1 -> "Deleted ${toDelete[0].name}"
                            failCount == 0 -> "Deleted ${toDelete.size} items"
                            successCount == 0 -> "Delete failed: ${failedDeletes.first()}"
                            else -> "Deleted $successCount, failed $failCount (first: ${failedDeletes.first()})"
                        }
                        snackbarHostState.showSnackbar(message)
                        refresh(); isOperating = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
    // -- Rename dialog --
    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    val share = currentShare ?: return@TextButton
                    if (renameText.isBlank()) return@TextButton
                    showRenameDialog = false; renameTarget = null
                    scope.launch {
                        isOperating = true
                        runCatching {
                            SmbClientManager.renameEntry(connection, share, target.path, renameText)
                            selectedPaths = emptySet(); refresh()
                        }.onFailure { snackbarHostState.showSnackbar("Rename failed: ${it.localizedMessage ?: "Unknown error"}") }
                        isOperating = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false; renameTarget = null }) { Text("Cancel") } }
        )
    }
    // -- Create folder dialog --
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = createFolderName, onValueChange = { createFolderName = it }, label = { Text("Folder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = currentShare ?: return@TextButton
                    if (createFolderName.isBlank()) return@TextButton
                    showCreateFolderDialog = false
                    scope.launch {
                        isOperating = true
                        val newPath = if (currentPath.isBlank()) createFolderName else "$currentPath\\$createFolderName"
                        runCatching { SmbClientManager.createDirectory(connection, share, newPath); refresh() }
                            .onFailure { snackbarHostState.showSnackbar("Create folder failed: ${it.localizedMessage ?: "Unknown error"}") }
                        isOperating = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
        )
    }
    // -- "Please wait" overlay during SMB operations --
    if (isOperating) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Please wait") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text(operationMessage ?: "Working on SMB operation...")
                }
            }
        )
    }
}
// -- SMB breadcrumb navigation bar --
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
    val segments = if (remotePath.isBlank()) emptyList() else remotePath.split("\\").filter { it.isNotEmpty() }
    LaunchedEffect(remotePath) { scrollState.animateScrollTo(scrollState.maxValue) }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onConnectionClick) {
            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = connectionName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
        }
        Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onShareClick) {
            Text(text = shareName, style = MaterialTheme.typography.bodyMedium, fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal)
        }
        segments.forEachIndexed { index, segment ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val segPath = segments.subList(0, index + 1).joinToString("\\")
            TextButton(onClick = { onSegmentClick(segPath) }) {
                Text(text = segment, style = MaterialTheme.typography.bodyMedium, fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
// -- SMB file list item --
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmbFileListItem(
    item: SmbRemoteItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (isSelectionMode) onLongClick() else onClick() }, onLongClick = onLongClick)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp).padding(4.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!item.isDirectory && item.size >= 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = FileUtils.formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isSelected) {
                Icon(Icons.Filled.Close, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
