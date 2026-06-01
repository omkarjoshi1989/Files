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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.ui.components.FileThumbnail
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import com.gmail.omkarjoshi1989.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onOpenFile: (File) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var favoritePaths by remember { mutableStateOf(FavoritesManager.getFavorites(context)) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val showHidden = remember { SettingsManager.isShowHiddenFiles(context) }

    val favoriteFiles = remember(favoritePaths, showHidden) {
        favoritePaths
            .map { File(it) }
            .filter { it.exists() && it.isFile }
            .filter { showHidden || !it.isHidden }
            .sortedBy { it.name.lowercase() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Favorites", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${favoriteFiles.size} file${if (favoriteFiles.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (favoriteFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No favorites yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Long-press any file to add it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(favoriteFiles, key = { it.absolutePath }) { file ->
                    FavoriteFileItem(
                        file = file,
                        onClick = { onOpenFile(file) },
                        onLongClick = {
                            selectedFile = file
                            showBottomSheet = true
                        },
                        onRemove = {
                            FavoritesManager.removeFavorite(context, file.absolutePath)
                            favoritePaths = FavoritesManager.getFavorites(context)
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
            // Always isFavorite = true in this screen
            FileOperationsSheet(
                file = selectedFile!!,
                isFavorite = true,
                onToggleFavorite = {
                    // Remove from favorites
                    FavoritesManager.removeFavorite(context, selectedFile!!.absolutePath)
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
                        val file = selectedFile!!
                        scope.launch {
                            try {
                                val oldPath = file.absolutePath
                                val newFile = File(file.parent, renameText)
                                withContext(Dispatchers.IO) {
                                    if (newFile.exists()) throw IllegalStateException("A file named '$renameText' already exists")
                                    if (!file.renameTo(newFile)) throw IllegalStateException("Rename failed")
                                }
                                // Update favorites: remove old path, add new path
                                FavoritesManager.removeFavorite(context, oldPath)
                                FavoritesManager.toggleFavorite(context, newFile.absolutePath)
                                favoritePaths = FavoritesManager.getFavorites(context)
                                snackbarHostState.showSnackbar("Renamed to: $renameText")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Rename failed: ${e.message}")
                            }
                        }
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
            title = { Text("Move to Recycle Bin") },
            text = {
                Text(
                    "Move \"${selectedFile!!.name}\" to the Recycle Bin?\n\n" +
                    "You can restore it later from the Recycle Bin."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = selectedFile!!
                    scope.launch {
                        try {
                            val success = withContext(Dispatchers.IO) {
                                RecycleBinManager.moveToRecycleBin(context, file)
                            }
                            if (success) {
                                // Remove from favorites
                                FavoritesManager.removeFavorite(context, file.absolutePath)
                                favoritePaths = FavoritesManager.getFavorites(context)
                                snackbarHostState.showSnackbar("Moved to Recycle Bin: ${file.name}")
                            } else {
                                snackbarHostState.showSnackbar("Move to Recycle Bin failed")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Delete failed: ${e.message}")
                        }
                    }
                    showDeleteDialog = false; selectedFile = null
                }) { Text("Move to Bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedFile = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteFileItem(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
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
                    text = FileUtils.toDisplayPath(file.parent ?: ""),
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
                        text = DateFormat.format("MMM dd, yyyy HH:mm", Date(file.lastModified())).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Star button — tap to remove from favorites
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Remove from favorites",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
