package com.gmail.omkarjoshi1989.ui.screens

import android.text.format.DateFormat
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.RecycleBinItem
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<RecycleBinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<RecycleBinItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEmptyDialog by remember { mutableStateOf(false) }

    fun reload() {
        items = RecycleBinManager.listItems(context)
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showEmptyDialog = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Empty Recycle Bin")
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Recycle Bin is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deleted files will appear here",
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
                items(items, key = { it.id }) { item ->
                    RecycleBinItemRow(
                        item = item,
                        onRestore = {
                            scope.launch {
                                isLoading = true
                                val success = withContext(Dispatchers.IO) {
                                    RecycleBinManager.restoreItem(context, item)
                                }
                                reload()
                                isLoading = false
                                snackbarHostState.showSnackbar(
                                    if (success) "Restored: ${item.name}"
                                    else "Restore failed — a file already exists at the original location"
                                )
                            }
                        },
                        onDelete = { itemToDelete = item; showDeleteDialog = true }
                    )
                }
            }
        }
    }

    // ── Permanent-delete single item dialog ───────────────────────────────────
    if (showDeleteDialog && itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; itemToDelete = null },
            title = { Text("Delete Permanently") },
            text = {
                Text(
                    "\"${target.name}\" will be permanently deleted and cannot be recovered.\n\n" +
                    "Are you sure?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            RecycleBinManager.permanentlyDelete(context, target)
                        }
                        reload()
                        snackbarHostState.showSnackbar("Permanently deleted: ${target.name}")
                    }
                    showDeleteDialog = false; itemToDelete = null
                }) { Text("Delete Forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Empty recycle bin dialog ──────────────────────────────────────────────
    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("Empty Recycle Bin") },
            text = {
                Text(
                    "All ${items.size} item(s) in the Recycle Bin will be permanently deleted. " +
                    "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        isLoading = true
                        withContext(Dispatchers.IO) { RecycleBinManager.emptyRecycleBin(context) }
                        reload()
                        isLoading = false
                        snackbarHostState.showSnackbar("Recycle Bin emptied")
                    }
                    showEmptyDialog = false
                }) { Text("Empty All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecycleBinItemRow(
    item: RecycleBinItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "From: ${FileUtils.toDisplayPath(item.originalPath.substringBeforeLast("/"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Deleted: ${DateFormat.format("MMM dd, yyyy HH:mm", Date(item.deletedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Restore button
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Permanent delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = "Delete Permanently",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
    }
}

