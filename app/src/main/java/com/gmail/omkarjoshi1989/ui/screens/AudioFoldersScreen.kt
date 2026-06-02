package com.gmail.omkarjoshi1989.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gmail.omkarjoshi1989.MediaViewerActivity
import com.gmail.omkarjoshi1989.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A single item shown in the explorer: either a navigable folder or an audio file. */
private sealed class AudioExplorerItem {
    data class Folder(val dir: File, val audioCount: Int) : AudioExplorerItem()
    data class AudioFile(val file: File) : AudioExplorerItem()
}

/**
 * Returns true if [dir] or any of its subdirectories (recursively)
 * contains at least one audio file.
 */
private fun folderHasAudio(dir: File): Boolean {
    val children = dir.listFiles() ?: return false
    for (child in children) {
        if (child.isFile && FileUtils.isAudioFile(child)) return true
        if (child.isDirectory && !child.name.startsWith(".") && folderHasAudio(child)) return true
    }
    return false
}

/**
 * Counts audio files recursively under [dir].
 */
private fun countAudioFilesRecursive(dir: File): Int {
    val children = dir.listFiles() ?: return 0
    var count = 0
    for (child in children) {
        if (child.isFile && FileUtils.isAudioFile(child)) count++
        else if (child.isDirectory && !child.name.startsWith(".")) count += countAudioFilesRecursive(child)
    }
    return count
}

/**
 * Returns the direct contents of [dir]:
 * - Sub-folders that contain audio files anywhere in their subtree (sorted by name)
 * - Audio files directly inside [dir] (sorted by name)
 */
private suspend fun getExplorerItems(dir: File): List<AudioExplorerItem> =
    withContext(Dispatchers.IO) {
        val children = dir.listFiles() ?: return@withContext emptyList()
        val folders = mutableListOf<AudioExplorerItem.Folder>()
        val audioFiles = mutableListOf<AudioExplorerItem.AudioFile>()

        for (child in children) {
            when {
                child.isDirectory && !child.name.startsWith(".") -> {
                    if (folderHasAudio(child)) {
                        folders.add(AudioExplorerItem.Folder(child, countAudioFilesRecursive(child)))
                    }
                }
                child.isFile && FileUtils.isAudioFile(child) -> {
                    audioFiles.add(AudioExplorerItem.AudioFile(child))
                }
            }
        }

        folders.sortedBy { it.dir.name.lowercase() } +
                audioFiles.sortedBy { it.file.name.lowercase() }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFoldersScreen(
    onNavigateBack: () -> Unit
) {
    val storageRoot = remember { android.os.Environment.getExternalStorageDirectory() }

    // Navigation stack: list of directories the user has entered.
    // The last entry is the current directory.
    var directoryStack by remember { mutableStateOf(listOf(storageRoot)) }
    val currentDir = directoryStack.last()

    var isLoading by remember { mutableStateOf(true) }
    var explorerItems by remember { mutableStateOf<List<AudioExplorerItem>>(emptyList()) }

    // Reload whenever the current directory changes
    LaunchedEffect(currentDir.absolutePath) {
        isLoading = true
        explorerItems = getExplorerItems(currentDir)
        isLoading = false
    }

    val canGoUp = directoryStack.size > 1

    if (canGoUp) {
        BackHandler {
            directoryStack = directoryStack.dropLast(1)
        }
    }

    val context = LocalContext.current

    // Compute a friendly title
    val title = if (directoryStack.size == 1) "Music" else currentDir.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (canGoUp) {
                            directoryStack = directoryStack.dropLast(1)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                explorerItems.isEmpty() -> {
                    Text(
                        text = if (directoryStack.size == 1)
                            "No audio files found on your device"
                        else
                            "No audio files found in this folder",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(explorerItems, key = { item ->
                            when (item) {
                                is AudioExplorerItem.Folder -> "dir:${item.dir.absolutePath}"
                                is AudioExplorerItem.AudioFile -> "file:${item.file.absolutePath}"
                            }
                        }) { item ->
                            when (item) {
                                is AudioExplorerItem.Folder -> {
                                    ExplorerFolderItem(
                                        dir = item.dir,
                                        audioCount = item.audioCount,
                                        onClick = {
                                            directoryStack = directoryStack + item.dir
                                        }
                                    )
                                }
                                is AudioExplorerItem.AudioFile -> {
                                    AudioFileItem(
                                        file = item.file,
                                        onClick = {
                                            val intent = Intent(context, MediaViewerActivity::class.java).apply {
                                                putExtra(
                                                    MediaViewerActivity.EXTRA_FOLDER_PATH,
                                                    currentDir.absolutePath
                                                )
                                                putExtra(
                                                    MediaViewerActivity.EXTRA_FILE_PATH,
                                                    item.file.absolutePath
                                                )
                                            }
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Row item for a navigable sub-folder ──────────────────────────────────────

@Composable
private fun ExplorerFolderItem(
    dir: File,
    audioCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dir.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$audioCount song${if (audioCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Row item for a single audio file ─────────────────────────────────────────

@Composable
private fun AudioFileItem(
    file: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.nameWithoutExtension,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${file.extension.uppercase()} · ${FileUtils.formatFileSize(file.length())}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
