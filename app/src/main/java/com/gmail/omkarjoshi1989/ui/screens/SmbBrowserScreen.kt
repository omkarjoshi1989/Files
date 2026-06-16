package com.gmail.omkarjoshi1989.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.SmbRemoteItem
import com.gmail.omkarjoshi1989.util.SmbClientManager
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.gmail.omkarjoshi1989.viewmodel.ClipboardRepository
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbBrowserScreen(
    connection: SmbConnectionConfig,
    onNavigateBack: () -> Unit
) {
    var currentShare by remember(connection.id) {
        mutableStateOf(connection.defaultShareName.takeIf { it.isNotBlank() })
    }
    var currentPath by remember(connection.id) { mutableStateOf("") }
    var shares by remember(connection.id) { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember(connection.id, currentShare, currentPath) { mutableStateOf<List<SmbRemoteItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isPasting by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard by ClipboardRepository.clipboard.collectAsState()
    fun refresh() {
        scope.launch {
            isLoading = true
            loadError = null
            runCatching {
                val loadedShares = SmbClientManager.listShares(connection)
                shares = loadedShares
                if (currentShare == null && loadedShares.isNotEmpty()) {
                    currentShare = loadedShares.first()
                }
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
    LaunchedEffect(connection.id, currentShare, currentPath) {
        refresh()
    }
    LaunchedEffect(loadError) {
        loadError?.let { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val clipboardData = clipboard
            if (clipboardData != null && currentShare != null && !isPasting) {
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
                                isPasting = true
                                try {
                                    SmbClientManager.pasteLocalClipboardToRemote(
                                        config = connection,
                                        shareName = share,
                                        remoteDirectoryPath = currentPath,
                                        clipboardData = clipboardData
                                    )
                                    ClipboardRepository.clipboard.value = null
                                    val message = if (clipboardData.files.size == 1) {
                                        "Pasted ${clipboardData.files[0].name}"
                                    } else {
                                        "Pasted ${clipboardData.files.size} items"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                    refresh()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Paste to SMB failed")
                                }
                                isPasting = false
                            }
                        },
                        icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                        text = {
                            val actionText = if (clipboardData.operation == ClipboardOperation.CUT) {
                                "Move to SMB"
                            } else {
                                "Paste to SMB"
                            }
                            val count = clipboardData.files.size
                            Text(if (count == 1) "$actionText - ${clipboardData.files[0].name}" else "$actionText ($count items)")
                        }
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = connection.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${connection.host}:${connection.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                isLoading || isPasting -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                currentShare == null -> {
                    if (shares.isEmpty()) {
                        Text(
                            text = "No SMB shares found",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ShareList(shares = shares, onSelect = {
                            currentShare = it
                            currentPath = ""
                        })
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ShareHeader(
                            share = currentShare!!,
                            path = if (currentPath.isBlank()) "\\" else "\\$currentPath",
                            onBackInShare = {
                                if (currentPath.isBlank()) {
                                    currentShare = null
                                } else {
                                    currentPath = currentPath.substringBeforeLast("\\", "")
                                }
                            }
                        )
                        HorizontalDivider()
                        if (entries.isEmpty()) {
                            Text(
                                text = "Folder is empty",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(entries, key = { it.path }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (item.isDirectory) {
                                                    currentPath = item.path
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (!item.isDirectory) {
                                                Text(
                                                    text = "${item.size} bytes",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ShareList(shares: List<String>, onSelect: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(shares, key = { it }) { share ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(share) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = share, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
        }
    }
}
@Composable
private fun ShareHeader(
    share: String,
    path: String,
    onBackInShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBackInShare) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back in share")
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = share, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
