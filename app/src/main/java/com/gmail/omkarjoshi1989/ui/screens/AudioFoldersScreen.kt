package com.gmail.omkarjoshi1989.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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

/**
 * Data class representing a folder that contains at least one audio file.
 */
private data class AudioFolder(
    val folder: File,
    val audioFileCount: Int,
    val firstAudioFile: File
)

/**
 * Recursively scans [root] and collects every unique folder that
 * directly contains at least one audio file.  Sub-folders are visited
 * for discovery, but only the immediate parent of each audio file is
 * recorded (no "subfolder strategy").
 */
private suspend fun findAudioFolders(root: File): List<AudioFolder> =
    withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, MutableList<File>>()

        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                when {
                    child.isFile && FileUtils.isAudioFile(child) -> {
                        folderMap.getOrPut(dir.absolutePath) { mutableListOf() }.add(child)
                    }
                    child.isDirectory && !child.name.startsWith(".") -> scan(child)
                }
            }
        }

        scan(root)

        folderMap.map { (path, files) ->
            val sortedFiles = files.sortedBy { it.name.lowercase() }
            AudioFolder(
                folder = File(path),
                audioFileCount = sortedFiles.size,
                firstAudioFile = sortedFiles.first()
            )
        }.sortedBy { it.folder.name.lowercase() }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFoldersScreen(
    onNavigateBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var audioFolders by remember { mutableStateOf<List<AudioFolder>>(emptyList()) }
    // Tracks which folder the user has drilled into (null = show folder grid)
    var selectedFolder by remember { mutableStateOf<AudioFolder?>(null) }

    // Scan storage on first composition
    LaunchedEffect(Unit) {
        val internalStorage = android.os.Environment.getExternalStorageDirectory()
        audioFolders = findAudioFolders(internalStorage)
        isLoading = false
    }

    if (selectedFolder != null) {
        // Hardware-back from folder contents → return to folder list
        BackHandler { selectedFolder = null }
        AudioFolderContentsScreen(
            audioFolder = selectedFolder!!,
            onNavigateBack = { selectedFolder = null }
        )
    } else {
        // ── Folder grid ──────────────────────────────────────────────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Music",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
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
                    audioFolders.isEmpty() -> {
                        Text(
                            text = "No audio files found on your device",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            gridItems(audioFolders, key = { it.folder.absolutePath }) { audioFolder ->
                                AudioFolderItem(
                                    audioFolder = audioFolder,
                                    onClick = { selectedFolder = audioFolder }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Folder contents screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioFolderContentsScreen(
    audioFolder: AudioFolder,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var audioFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(audioFolder.folder.absolutePath) {
        audioFiles = withContext(Dispatchers.IO) {
            FileUtils.getAudioFilesInFolder(context, audioFolder.folder)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = audioFolder.folder.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLoading) {
                            Text(
                                text = "${audioFiles.size} song${if (audioFiles.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                audioFiles.isEmpty() -> {
                    Text(
                        text = "No audio files found in this folder",
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
                        items(audioFiles, key = { it.absolutePath }) { file ->
                            AudioFileItem(
                                file = file,
                                onClick = {
                                    val intent = Intent(context, MediaViewerActivity::class.java).apply {
                                        putExtra(
                                            MediaViewerActivity.EXTRA_FOLDER_PATH,
                                            audioFolder.folder.absolutePath
                                        )
                                        putExtra(
                                            MediaViewerActivity.EXTRA_FILE_PATH,
                                            file.absolutePath
                                        )
                                    }
                                    context.startActivity(intent)
                                }
                            )
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
        // Music note icon with orange tinted background (matching audio style)
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

@Composable
private fun AudioFolderItem(
    audioFolder: AudioFolder,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Folder icon box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Folder name
        Text(
            text = audioFolder.folder.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Audio file count
        Text(
            text = "${audioFolder.audioFileCount} song${if (audioFolder.audioFileCount != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

