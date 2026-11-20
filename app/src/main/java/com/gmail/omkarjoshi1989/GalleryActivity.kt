package com.gmail.omkarjoshi1989

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gmail.omkarjoshi1989.ui.components.FileThumbnailGrid
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.BackgroundOperationsManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.HiddenGalleryFoldersManager
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import com.gmail.omkarjoshi1989.util.SettingsManager
import com.gmail.omkarjoshi1989.util.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat


class GalleryActivity : ComponentActivity() {

    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_mode") themeMode = SettingsManager.getThemeMode(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        themeMode = SettingsManager.getThemeMode(this)
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        setContent {
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FilesTheme(darkTheme = isDark) {
                GalleryScreen(
                    onNavigateBack = { finish() },
                    onOpenMedia = { mediaFile -> openMedia(mediaFile) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun openMedia(file: File) {
        val folder = file.parentFile ?: return
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, folder.absolutePath)
            putExtra(MediaViewerActivity.EXTRA_MIX_IMAGES_VIDEOS, true)
        }
        startActivity(intent)
    }
}

private data class GalleryFolder(
    val absolutePath: String,
    val name: String,
    val itemCount: Int,
    val coverPath: String,
    val coverIsDirectory: Boolean,
    val coverExtension: String,
    val coverLastModified: Long,
    val isHidden: Boolean
)

private data class GalleryMediaItem(
    val file: File,
    val extension: String,
    val lastModified: Long
)

private sealed interface GalleryFoldersState {
    data object Loading : GalleryFoldersState
    data class Ready(val folders: List<GalleryFolder>) : GalleryFoldersState
    data class Error(val message: String) : GalleryFoldersState
}

private sealed interface GalleryMediaState {
    data object Idle : GalleryMediaState
    data object Loading : GalleryMediaState
    data class Ready(val files: List<GalleryMediaItem>) : GalleryMediaState
    data class Error(val message: String) : GalleryMediaState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    onNavigateBack: () -> Unit,
    onOpenMedia: (File) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderPaths by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedMediaPaths by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showHiddenFolders by rememberSaveable { mutableStateOf(false) }
    var hiddenFolderPaths by remember { mutableStateOf(HiddenGalleryFoldersManager.getHiddenFolders(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val isFolderSelectionMode = selectedFolderPath == null && selectedFolderPaths.isNotEmpty()
    val isMediaSelectionMode = selectedFolderPath != null && selectedMediaPaths.isNotEmpty()
    val isSelectionMode = isFolderSelectionMode || isMediaSelectionMode

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hiddenFolderPaths = HiddenGalleryFoldersManager.getHiddenFolders(context)
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val foldersState by produceState<GalleryFoldersState>(
        initialValue = GalleryFoldersState.Loading,
        key1 = refreshTick,
        key2 = showHiddenFolders,
        key3 = hiddenFolderPaths
    ) {
        value = GalleryFoldersState.Loading
        value = withContext(Dispatchers.IO) {
            runCatching {
                GalleryFoldersState.Ready(
                    loadGalleryFolders(
                        context = context,
                        hiddenFolderPaths = hiddenFolderPaths,
                        includeHiddenFolders = showHiddenFolders
                    )
                )
            }
                .getOrElse { GalleryFoldersState.Error(it.localizedMessage ?: "Unable to load gallery folders") }
        }
    }

    val mediaState by produceState<GalleryMediaState>(
        initialValue = GalleryMediaState.Idle,
        key1 = selectedFolderPath,
        key2 = refreshTick
    ) {
        val folderPath = selectedFolderPath
        if (folderPath == null) {
            value = GalleryMediaState.Idle
        } else {
            value = GalleryMediaState.Loading
            value = withContext(Dispatchers.IO) {
                runCatching { GalleryMediaState.Ready(loadGalleryMediaFiles(context, folderPath)) }
                    .getOrElse { GalleryMediaState.Error(it.localizedMessage ?: "Unable to load this folder") }
            }
        }
    }

    BackHandler(enabled = selectedFolderPath != null) {
        selectedFolderPath = null
        selectedMediaPaths = emptySet()
    }

    BackHandler(enabled = isFolderSelectionMode) {
        selectedFolderPaths = emptySet()
    }

    BackHandler(enabled = isMediaSelectionMode) {
        selectedMediaPaths = emptySet()
    }

    val title = remember(selectedFolderPath, foldersState) {
        if (selectedFolderPath == null) {
            "Gallery"
        } else {
            val folders = (foldersState as? GalleryFoldersState.Ready)?.folders.orEmpty()
            folders.firstOrNull { it.absolutePath == selectedFolderPath }?.name ?: File(selectedFolderPath!!).name
        }
    }

    val currentFolders = (foldersState as? GalleryFoldersState.Ready)?.folders.orEmpty()
    val currentMediaFiles = (mediaState as? GalleryMediaState.Ready)?.files.orEmpty()
    val selectedFolders = currentFolders.filter { it.absolutePath in selectedFolderPaths }
    val selectedMediaFiles = currentMediaFiles.filter { it.file.absolutePath in selectedMediaPaths }
    val selectedVisibleFolderPaths = selectedFolders.filterNot { it.isHidden }.map { it.absolutePath }
    val selectedHiddenFolderPaths = selectedFolders.filter { it.isHidden }.map { it.absolutePath }
    val hiddenFolderCount = hiddenFolderPaths.size
    val selectedDeleteTargets = if (isFolderSelectionMode) {
        selectedFolders.map { File(it.absolutePath) }
    } else {
        selectedMediaFiles.map { it.file }
    }
    val deleteDialogTitle = if (isFolderSelectionMode) "Move folders to Recycle Bin" else "Move files to Recycle Bin"
    val deleteDialogMessage = when {
        selectedDeleteTargets.isEmpty() -> ""
        selectedDeleteTargets.size == 1 -> {
            val target = selectedDeleteTargets.first()
            val extra = if (target.isDirectory) "\n\nAll contents inside will also be moved." else ""
            "Move \"${target.name}\" to the Recycle Bin?$extra\n\nYou can restore it later."
        }
        isFolderSelectionMode -> "Move ${selectedDeleteTargets.size} folder(s) to the Recycle Bin?\n\nYou can restore them later."
        else -> "Move ${selectedDeleteTargets.size} file(s) to the Recycle Bin?\n\nYou can restore them later."
    }

    fun hideSelectedFolders() {
        if (selectedVisibleFolderPaths.isEmpty()) return
        HiddenGalleryFoldersManager.hideFolders(context, selectedVisibleFolderPaths)
        hiddenFolderPaths = HiddenGalleryFoldersManager.getHiddenFolders(context)
        selectedFolderPaths = emptySet()
        refreshTick++
    }

    fun unhideSelectedFolders() {
        if (selectedHiddenFolderPaths.isEmpty()) return
        HiddenGalleryFoldersManager.unhideFolders(context, selectedHiddenFolderPaths)
        hiddenFolderPaths = HiddenGalleryFoldersManager.getHiddenFolders(context)
        selectedFolderPaths = emptySet()
        refreshTick++
    }

    fun deleteSelectedItems() {
        if (selectedDeleteTargets.isEmpty() || isDeleting) return
        val targets = selectedDeleteTargets.toList()
        val deletingFolders = isFolderSelectionMode
        val opId = "gallery_delete_${System.currentTimeMillis()}_${targets.first().name}"
        showDeleteDialog = false
        isDeleting = true

        coroutineScope.launch {
            BackgroundOperationsManager.start(
                id = opId,
                title = if (deletingFolders) "Deleting gallery folders" else "Deleting gallery files",
                detail = "Preparing...",
                total = targets.size,
                fileName = targets.first().name,
                sourcePath = targets.first().absolutePath
            )

            val failedPaths = mutableListOf<String>()
            val deletedFolderPaths = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                targets.forEachIndexed { index, target ->
                    BackgroundOperationsManager.progress(
                        id = opId,
                        title = if (deletingFolders) "Deleting gallery folders" else "Deleting gallery files",
                        detail = "${index + 1} of ${targets.size}",
                        current = index + 1,
                        total = targets.size,
                        fileName = target.name,
                        sourcePath = target.absolutePath
                    )

                    val success = RecycleBinManager.moveToRecycleBin(context, target)
                    if (success) {
                        if (target.isDirectory) deletedFolderPaths += target.absolutePath
                    } else {
                        failedPaths += target.absolutePath
                    }
                }
            }

            if (deletedFolderPaths.isNotEmpty()) {
                HiddenGalleryFoldersManager.unhideFolders(context, deletedFolderPaths)
            }
            hiddenFolderPaths = HiddenGalleryFoldersManager.getHiddenFolders(context)
            selectedFolderPaths = emptySet()
            selectedMediaPaths = emptySet()
            refreshTick++
            isDeleting = false

            val successCount = targets.size - failedPaths.size
            if (failedPaths.isEmpty()) {
                BackgroundOperationsManager.complete(
                    id = opId,
                    title = "Deleted",
                    detail = "Done",
                    current = targets.size,
                    total = targets.size,
                    fileName = if (targets.size == 1) targets.first().name else "${targets.size} items"
                )
                snackbarHostState.showSnackbar(
                    if (targets.size == 1) "Moved to Recycle Bin: ${targets.first().name}"
                    else "Moved $successCount item(s) to Recycle Bin"
                )
            } else {
                BackgroundOperationsManager.fail(
                    id = opId,
                    title = "Delete failed",
                    detail = "$successCount succeeded, ${failedPaths.size} failed",
                    current = successCount,
                    total = targets.size,
                    fileName = if (failedPaths.size == 1) File(failedPaths.first()).name else "${failedPaths.size} items"
                )
                snackbarHostState.showSnackbar(
                    if (successCount > 0) "Moved $successCount item(s); ${failedPaths.size} failed"
                    else "Move to Recycle Bin failed"
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isFolderSelectionMode -> "${selectedFolderPaths.size} selected"
                            isMediaSelectionMode -> "${selectedMediaPaths.size} selected"
                            else -> title
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            isFolderSelectionMode -> selectedFolderPaths = emptySet()
                            isMediaSelectionMode -> selectedMediaPaths = emptySet()
                            selectedFolderPath == null -> onNavigateBack()
                            else -> {
                                selectedFolderPath = null
                                selectedMediaPaths = emptySet()
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (isFolderSelectionMode && selectedVisibleFolderPaths.isNotEmpty()) {
                            IconButton(onClick = { if (!isDeleting) hideSelectedFolders() }) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide selected folders")
                            }
                        }
                        if (isFolderSelectionMode && selectedHiddenFolderPaths.isNotEmpty()) {
                            IconButton(onClick = { if (!isDeleting) unhideSelectedFolders() }) {
                                Icon(Icons.Filled.Visibility, contentDescription = "Unhide selected folders")
                            }
                        }
                        if (selectedDeleteTargets.isNotEmpty()) {
                            IconButton(onClick = { if (!isDeleting) showDeleteDialog = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Gallery options")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                if (selectedFolderPath == null && hiddenFolderCount > 0) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (showHiddenFolders) "Hide hidden folders"
                                                else "Show hidden folders (${NumberFormat.getIntegerInstance().format(hiddenFolderCount)})"
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            selectedFolderPaths = emptySet()
                                            showHiddenFolders = !showHiddenFolders
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (showHiddenFolders) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    HorizontalDivider()
                                }
                                if (selectedFolderPath != null) {
                                    DropdownMenuItem(
                                        text = { Text("All folders") },
                                        onClick = {
                                            showMoreMenu = false
                                            selectedFolderPath = null
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Collections, contentDescription = null)
                                        }
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        showMoreMenu = false
                                        hiddenFolderPaths = HiddenGalleryFoldersManager.getHiddenFolders(context)
                                        refreshTick++
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        when (val folderPath = selectedFolderPath) {
            null -> GalleryFolderGrid(
                state = foldersState,
                showingHiddenFolders = showHiddenFolders,
                selectedFolderPaths = selectedFolderPaths,
                onFolderClick = { folder ->
                    if (isFolderSelectionMode) {
                        selectedFolderPaths = if (folder.absolutePath in selectedFolderPaths) {
                            selectedFolderPaths - folder.absolutePath
                        } else {
                            selectedFolderPaths + folder.absolutePath
                        }
                    } else {
                        selectedMediaPaths = emptySet()
                        selectedFolderPath = folder.absolutePath
                    }
                },
                onFolderLongClick = { folder ->
                    selectedFolderPaths = if (folder.absolutePath in selectedFolderPaths) {
                        selectedFolderPaths - folder.absolutePath
                    } else {
                        selectedFolderPaths + folder.absolutePath
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            else -> key(folderPath) {
                GalleryMediaGrid(
                    folderName = title,
                    state = mediaState,
                    selectedMediaPaths = selectedMediaPaths,
                    onMediaClick = { item ->
                        if (isMediaSelectionMode) {
                            selectedMediaPaths = if (item.file.absolutePath in selectedMediaPaths) {
                                selectedMediaPaths - item.file.absolutePath
                            } else {
                                selectedMediaPaths + item.file.absolutePath
                            }
                        } else {
                            onOpenMedia(item.file)
                        }
                    },
                    onMediaLongClick = { item ->
                        selectedMediaPaths = if (item.file.absolutePath in selectedMediaPaths) {
                            selectedMediaPaths - item.file.absolutePath
                        } else {
                            selectedMediaPaths + item.file.absolutePath
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showDeleteDialog && selectedDeleteTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(deleteDialogTitle) },
            text = { Text(deleteDialogMessage) },
            confirmButton = {
                TextButton(onClick = { deleteSelectedItems() }) {
                    Text("Move to Bin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GalleryFolderGrid(
    state: GalleryFoldersState,
    showingHiddenFolders: Boolean,
    selectedFolderPaths: Set<String>,
    onFolderClick: (GalleryFolder) -> Unit,
    onFolderLongClick: (GalleryFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        GalleryFoldersState.Loading -> GalleryLoadingState(modifier)
        is GalleryFoldersState.Error -> GalleryMessageState(state.message, modifier)
        is GalleryFoldersState.Ready -> {
            if (state.folders.isEmpty()) {
                GalleryMessageState(
                    if (showingHiddenFolders) "No hidden folders found"
                    else "No image or video folders found",
                    modifier
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier,
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(state.folders, key = { it.absolutePath }) { folder ->
                        GalleryFolderCard(
                            folder = folder,
                            isSelectionMode = selectedFolderPaths.isNotEmpty(),
                            isSelected = folder.absolutePath in selectedFolderPaths,
                            onClick = { onFolderClick(folder) },
                            onLongClick = { onFolderLongClick(folder) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryMediaGrid(
    folderName: String,
    state: GalleryMediaState,
    selectedMediaPaths: Set<String>,
    onMediaClick: (GalleryMediaItem) -> Unit,
    onMediaLongClick: (GalleryMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        GalleryMediaState.Idle,
        GalleryMediaState.Loading -> GalleryLoadingState(modifier)

        is GalleryMediaState.Error -> GalleryMessageState(state.message, modifier)
        is GalleryMediaState.Ready -> {
            if (state.files.isEmpty()) {
                GalleryMessageState("No images or videos found in $folderName", modifier)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier,
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(state.files, key = { it.file.absolutePath }) { item ->
                        GalleryMediaCard(
                            item = item,
                            isSelectionMode = selectedMediaPaths.isNotEmpty(),
                            isSelected = item.file.absolutePath in selectedMediaPaths,
                            onClick = { onMediaClick(item) },
                            onLongClick = { onMediaLongClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryFolderCard(
    folder: GalleryFolder,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .alpha(if (folder.isHidden && !isSelected) 0.72f else 1f)
            .combinedClickable(
                onClick = { if (isSelectionMode) onLongClick() else onClick() },
                onLongClick = onLongClick
            )
    ) {
        FileThumbnailGrid(
            absolutePath = folder.coverPath,
            isDirectory = folder.coverIsDirectory,
            extension = folder.coverExtension,
            lastModified = folder.coverLastModified,
            modifier = Modifier.fillMaxSize()
        )

        if (folder.isHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Hidden",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = NumberFormat.getIntegerInstance().format(folder.itemCount),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            )
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
                    .align(Alignment.TopStart)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryMediaCard(
    item: GalleryMediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { if (isSelectionMode) onLongClick() else onClick() },
                onLongClick = onLongClick
            )
    ) {
        FileThumbnailGrid(
            absolutePath = item.file.absolutePath,
            isDirectory = false,
            extension = item.extension,
            lastModified = item.lastModified,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.file.name,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }

        if (item.extension in galleryVideoExtensions) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            )
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
                    .align(Alignment.TopStart)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GalleryMessageState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class MutableGalleryFolder(
    val absolutePath: String,
    val name: String,
    var itemCount: Int,
    var coverPath: String,
    var coverIsDirectory: Boolean,
    var coverExtension: String,
    var coverLastModified: Long,
    var isHidden: Boolean
)

private fun loadGalleryFolders(
    context: Context,
    hiddenFolderPaths: Set<String>,
    includeHiddenFolders: Boolean
): List<GalleryFolder> {
    val folders = linkedMapOf<String, MutableGalleryFolder>()
    val showHiddenFiles = SettingsManager.isShowHiddenFiles(context)
    val contentUri = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )

    context.contentResolver.query(
        contentUri,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
    )?.use { cursor ->
        val pathIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val modifiedIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        if (pathIndex < 0 || modifiedIndex < 0) return emptyList()

        while (cursor.moveToNext()) {
            val mediaPath = cursor.getString(pathIndex) ?: continue
            val mediaFile = File(mediaPath)
            val parent = mediaFile.parentFile ?: continue
            if (!mediaFile.exists() || !mediaFile.isFile) continue
            if (!showHiddenFiles && isHiddenPath(mediaFile)) continue
            val isAppHiddenFolder = parent.absolutePath in hiddenFolderPaths
            if (isAppHiddenFolder && !includeHiddenFolders) continue

            val modified = normalizeTimestamp(cursor.getLong(modifiedIndex), mediaFile.lastModified())
            val existing = folders[parent.absolutePath]
            if (existing == null) {
                folders[parent.absolutePath] = MutableGalleryFolder(
                    absolutePath = parent.absolutePath,
                    name = parent.name.ifBlank { FileUtils.toDisplayPath(parent.absolutePath) },
                    itemCount = 1,
                    coverPath = mediaFile.absolutePath,
                    coverIsDirectory = false,
                    coverExtension = mediaFile.extension.lowercase(),
                    coverLastModified = modified,
                    isHidden = isAppHiddenFolder
                )
            } else {
                existing.itemCount += 1
                if (modified >= existing.coverLastModified) {
                    existing.coverPath = mediaFile.absolutePath
                    existing.coverIsDirectory = false
                    existing.coverExtension = mediaFile.extension.lowercase()
                    existing.coverLastModified = modified
                }
                existing.isHidden = isAppHiddenFolder
            }
        }
    }

    if (includeHiddenFolders) {
        hiddenFolderPaths.forEach { hiddenPath ->
            if (folders.containsKey(hiddenPath)) return@forEach
            val folder = File(hiddenPath)
            if (!folder.exists() || !folder.isDirectory) return@forEach
            val directMediaCount = folder.listFiles().orEmpty()
                .count { file ->
                    file.isFile &&
                        FileUtils.isVisualMediaFile(file) &&
                        (showHiddenFiles || !isHiddenPath(file))
                }
            folders[hiddenPath] = MutableGalleryFolder(
                absolutePath = hiddenPath,
                name = folder.name.ifBlank { FileUtils.toDisplayPath(hiddenPath) },
                itemCount = directMediaCount,
                coverPath = hiddenPath,
                coverIsDirectory = true,
                coverExtension = "",
                coverLastModified = folder.lastModified(),
                isHidden = true
            )
        }
    }

    return folders.values
        .map {
            GalleryFolder(
                absolutePath = it.absolutePath,
                name = it.name,
                itemCount = it.itemCount,
                coverPath = it.coverPath,
                coverIsDirectory = it.coverIsDirectory,
                coverExtension = it.coverExtension,
                coverLastModified = it.coverLastModified,
                isHidden = it.isHidden
            )
        }
        .sortedWith(compareByDescending<GalleryFolder> { it.coverLastModified }.thenBy { it.name.lowercase() })
}

private fun loadGalleryMediaFiles(context: Context, folderPath: String): List<GalleryMediaItem> {
    val showHiddenFiles = SettingsManager.isShowHiddenFiles(context)
    val folder = File(folderPath)
    val files = folder.listFiles().orEmpty()
    return files.asSequence()
        .filter { it.isFile && FileUtils.isVisualMediaFile(it) }
        .filter { showHiddenFiles || !isHiddenPath(it) }
        .sortedWith(compareByDescending<File> { getDateCreated(it) }.thenBy { it.name.lowercase() })
        .map {
            GalleryMediaItem(
                file = it,
                extension = it.extension.lowercase(),
                lastModified = it.lastModified()
            )
        }
        .toList()
}

private fun normalizeTimestamp(mediaStoreValue: Long, fallback: Long): Long {
    if (mediaStoreValue <= 0L) return fallback
    return if (mediaStoreValue < 100_000_000_000L) mediaStoreValue * 1000L else mediaStoreValue
}

private fun isHiddenPath(file: File): Boolean {
    if (file.name.startsWith('.')) return true
    var current: File? = file.parentFile
    while (current != null) {
        if (current.name.startsWith('.')) return true
        current = current.parentFile
    }
    return false
}

private fun getDateCreated(file: File): Long {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
            java.nio.file.Files.readAttributes(
                file.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            ).creationTime().toMillis()
        } catch (_: Exception) {
            file.lastModified()
        }
    } else {
        file.lastModified()
    }
}

private val galleryVideoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mts", "m2ts", "vob", "rmvb"
)

