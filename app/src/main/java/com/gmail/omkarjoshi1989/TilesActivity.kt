package com.gmail.omkarjoshi1989

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.gmail.omkarjoshi1989.ui.screens.ApplicationsScreen
import com.gmail.omkarjoshi1989.ui.screens.FavoritesScreen
import com.gmail.omkarjoshi1989.ui.screens.FileExplorerScreen
import com.gmail.omkarjoshi1989.ui.screens.PinLockScreen
import com.gmail.omkarjoshi1989.ui.screens.RecentFilesScreen
import com.gmail.omkarjoshi1989.ui.screens.SettingsScreen
import com.gmail.omkarjoshi1989.ui.screens.ZipViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.SettingsManager
import com.gmail.omkarjoshi1989.util.ThemeMode
import com.gmail.omkarjoshi1989.viewmodel.FileExplorerViewModel
import com.gmail.omkarjoshi1989.viewmodel.RecentFilesViewModel
import com.gmail.omkarjoshi1989.viewmodel.ZipViewModel

enum class Screen {
    FILE_EXPLORER, RECENT_FILES, FAVORITES, APPLICATIONS, SETTINGS, ZIP_VIEWER
}

class TilesActivity : ComponentActivity() {

    private var hasStoragePermission by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)
    private var masterPasswordEnabled by mutableStateOf(true)
    private var currentScreen by mutableStateOf(Screen.FILE_EXPLORER)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var zipFileToView by mutableStateOf<java.io.File?>(null)

    /** Timestamp of the last back-press when at the explorer root (for double-back-to-exit). */
    private var lastBackPressTime = 0L

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_mode") themeMode = SettingsManager.getThemeMode(this)
    }

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
        themeMode = SettingsManager.getThemeMode(this)
        if (!masterPasswordEnabled) {
            isAuthenticated = true
        }
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        setContent {
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
            }
            FilesTheme(darkTheme = isDark) {
                if (!isAuthenticated) {
                    BackHandler { finish() }
                    PinLockScreen(
                        onPinCorrect = { isAuthenticated = true }
                    )
                } else if (hasStoragePermission) {
                    when (currentScreen) {
                        Screen.FILE_EXPLORER -> {
                            val fileViewModel: FileExplorerViewModel = viewModel()
                            BackHandler {
                                val state = fileViewModel.uiState.value
                                when {
                                    state.isSearchActive -> fileViewModel.toggleSearch()
                                    state.isSelectionMode -> fileViewModel.clearSelection()
                                    !fileViewModel.navigateUp() -> handleExitBackPress()
                                }
                            }
                            FileExplorerScreen(
                                viewModel = fileViewModel,
                                onOpenFile = { file -> openFile(file) },
                                onNavigateBack = {
                                    if (!fileViewModel.navigateUp()) {
                                        handleExitBackPress()
                                    }
                                },
                                onNavigateToRecentFiles = { currentScreen = Screen.RECENT_FILES },
                                onNavigateToFavorites = { currentScreen = Screen.FAVORITES },
                                onNavigateToApplications = { currentScreen = Screen.APPLICATIONS },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onShowToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                            )
                        }
                        Screen.RECENT_FILES -> {
                            val recentViewModel: RecentFilesViewModel = viewModel()
                            BackHandler { currentScreen = Screen.FILE_EXPLORER }
                            RecentFilesScreen(
                                viewModel = recentViewModel,
                                onOpenFile = { file -> openFile(file) },
                                onNavigateBack = { currentScreen = Screen.FILE_EXPLORER }
                            )
                        }
                        Screen.FAVORITES -> {
                            BackHandler { currentScreen = Screen.FILE_EXPLORER }
                            FavoritesScreen(
                                onOpenFile = { file -> openFile(file) },
                                onNavigateBack = { currentScreen = Screen.FILE_EXPLORER }
                            )
                        }
                        Screen.APPLICATIONS -> {
                            BackHandler { currentScreen = Screen.FILE_EXPLORER }
                            ApplicationsScreen(
                                onNavigateBack = { currentScreen = Screen.FILE_EXPLORER }
                            )
                        }
                        Screen.SETTINGS -> {
                            BackHandler { currentScreen = Screen.FILE_EXPLORER }
                            SettingsScreen(
                                onNavigateBack = {
                                    masterPasswordEnabled = SettingsManager.isMasterPasswordEnabled(this@TilesActivity)
                                    currentScreen = Screen.FILE_EXPLORER
                                }
                            )
                        }
                        Screen.ZIP_VIEWER -> {
                            val zipViewModel: ZipViewModel = viewModel()
                            zipFileToView?.let { zipViewModel.loadZipFile(it) }
                            BackHandler {
                                if (!zipViewModel.navigateUp()) {
                                    currentScreen = Screen.FILE_EXPLORER
                                }
                            }
                            ZipViewerScreen(
                                viewModel = zipViewModel,
                                onNavigateBack = {
                                    if (!zipViewModel.navigateUp()) {
                                        currentScreen = Screen.FILE_EXPLORER
                                    }
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

    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
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
            val appPackage = applicationContext.packageName
            val packagesForUid = packageManager.getPackagesForUid(Process.myUid()).orEmpty()
            val packageBelongsToUid = packagesForUid.contains(appPackage)

            val packageSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", appPackage, null)
            }
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

            val intentToLaunch = when {
                packageBelongsToUid && packageSpecificIntent.resolveActivity(packageManager) != null -> packageSpecificIntent
                fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
                else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", appPackage, null)
                }
            }

            try {
                manageStorageLauncher.launch(intentToLaunch)
            } catch (_: SecurityException) {
                manageStorageLauncher.launch(fallbackIntent)
            } catch (_: ActivityNotFoundException) {
                manageStorageLauncher.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", appPackage, null)
                    }
                )
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
        if (FileUtils.isZipFile(file)) {
            zipFileToView = file
            currentScreen = Screen.ZIP_VIEWER
        } else if (FileUtils.isMediaFile(file)) {
            // Open in built-in media viewer
            val intent = Intent(this, MediaViewerActivity::class.java).apply {
                putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
                if (file.parentFile != null) {
                    // Load all same-type files in the folder
                    putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, file.parentFile!!.absolutePath)
                }
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

    /**
     * Double-back-press to exit: first press shows a toast hint,
     * a second press within 2 seconds exits the app.
     */
    private fun handleExitBackPress() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000) {
            finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
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
