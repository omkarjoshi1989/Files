package com.gmail.omkarjoshi1989

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmail.omkarjoshi1989.ui.screens.FileExplorerScreen
import com.gmail.omkarjoshi1989.ui.screens.HomeDestination
import com.gmail.omkarjoshi1989.ui.screens.HomeScreen
import com.gmail.omkarjoshi1989.ui.screens.PinLockScreen
import com.gmail.omkarjoshi1989.ui.screens.RecentFilesScreen
import com.gmail.omkarjoshi1989.ui.screens.SettingsScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.SettingsManager
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import com.gmail.omkarjoshi1989.viewmodel.RecentFilesViewModel

enum class Screen {
    HOME, FILE_EXPLORER, RECENT_FILES, SETTINGS
}

class MainActivity : ComponentActivity() {

    private var hasStoragePermission by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)
    private var masterPasswordEnabled by mutableStateOf(true)
    private var currentScreen by mutableStateOf(Screen.HOME)

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermission()
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()
        masterPasswordEnabled = SettingsManager.isMasterPasswordEnabled(this)
        if (!masterPasswordEnabled) {
            isAuthenticated = true
        }

        setContent {
            FilesTheme {
                if (!isAuthenticated) {
                    BackHandler { finish() }
                    PinLockScreen(
                        onPinCorrect = { isAuthenticated = true }
                    )
                } else if (hasStoragePermission) {
                    when (currentScreen) {
                        Screen.HOME -> {
                            BackHandler { finish() }
                            HomeScreen(
                                onNavigate = { destination ->
                                    when (destination) {
                                        HomeDestination.FILE_EXPLORER -> currentScreen = Screen.FILE_EXPLORER
                                        HomeDestination.RECENT_FILES -> currentScreen = Screen.RECENT_FILES
                                        HomeDestination.SETTINGS -> currentScreen = Screen.SETTINGS
                                        else -> {
                                            Toast.makeText(this, "${destination.label} - Coming soon!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        Screen.FILE_EXPLORER -> {
                            val fileViewModel: FileExplorerViewModel = viewModel()
                            BackHandler {
                                if (!fileViewModel.navigateUp()) {
                                    currentScreen = Screen.HOME
                                }
                            }
                            FileExplorerScreen(
                                viewModel = fileViewModel,
                                onOpenFile = { file -> openFile(file) },
                                onNavigateBack = {
                                    if (!fileViewModel.navigateUp()) {
                                        currentScreen = Screen.HOME
                                    }
                                }
                            )
                        }
                        Screen.RECENT_FILES -> {
                            val recentViewModel: RecentFilesViewModel = viewModel()
                            BackHandler { currentScreen = Screen.HOME }
                            RecentFilesScreen(
                                viewModel = recentViewModel,
                                onOpenFile = { file -> openFile(file) },
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                        }
                        Screen.SETTINGS -> {
                            BackHandler { currentScreen = Screen.HOME }
                            SettingsScreen(
                                onNavigateBack = {
                                    // Re-read setting when leaving settings
                                    masterPasswordEnabled = SettingsManager.isMasterPasswordEnabled(this@MainActivity)
                                    currentScreen = Screen.HOME
                                }
                            )
                        }
                    }
                } else {
                    PermissionScreen(
                        onRequestPermission = { requestStoragePermission() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    private fun checkPermission() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun openFile(file: java.io.File) {
        if (FileUtils.isMediaFile(file) && file.parentFile != null) {
            // Open in built-in media viewer with swipeable pager
            val intent = Intent(this, MediaViewerActivity::class.java).apply {
                putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, file.parentFile!!.absolutePath)
                putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
            }
            startActivity(intent)
        } else {
            try {
                val intent = FileUtils.getOpenFileIntent(this, file)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Storage Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app needs access to your device storage to browse and manage files. Please grant the \"All files access\" permission.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}