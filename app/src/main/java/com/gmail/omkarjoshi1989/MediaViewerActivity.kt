package com.gmail.omkarjoshi1989

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gmail.omkarjoshi1989.ui.screens.MediaViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import java.io.File

class MediaViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_AUDIO_ONLY = "audio_only"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }

    private var mediaFiles by mutableStateOf<List<File>>(emptyList())
    private var initialIndex by mutableIntStateOf(0)
    // Incremented on every new intent so the Composable tree reinitialises
    // (rememberPagerState picks up the new initialIndex).
    private var screenKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        if (!loadFromIntent(intent)) {
            finish(); return
        }

        // Handle new intents when activity is relaunched (e.g. from notification click)
        addOnNewIntentListener { newIntent ->
            setIntent(newIntent)
            if (loadFromIntent(newIntent)) {
                screenKey++  // force full recomposition with new initialIndex
            }
        }

        setContent {
            FilesTheme {
                key(screenKey) {
                    MediaViewerScreen(
                        mediaFiles = mediaFiles,
                        initialIndex = initialIndex,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun loadFromIntent(intent: android.content.Intent?): Boolean {
        val folderPath = intent?.getStringExtra(EXTRA_FOLDER_PATH) ?: return false
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: return false
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)

        val folder = File(folderPath)
        val files = if (audioOnly) {
            FileUtils.getAudioFilesInFolder(folder)
        } else {
            FileUtils.getMediaFilesInFolder(folder)
        }

        if (files.isEmpty()) return false

        mediaFiles = files
        initialIndex = files.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
        return true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}

