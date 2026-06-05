package com.gmail.omkarjoshi1989

import android.Manifest
import android.content.Intent
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
import com.gmail.omkarjoshi1989.ui.screens.MusicPlayerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import java.io.File

/**
 * A dedicated Activity for music (audio) playback.
 *
 * Accepts the same Intent extras as [MediaViewerActivity] so that
 * [service.MusicPlaybackService] and other launchers can point here
 * without any API changes.
 *
 * All UI is rendered via [MusicPlayerScreen] — a Jetpack Compose screen
 * that contains the complete audio-player logic (looping pager, immersive
 * mode, portrait/landscape layouts, service binding, etc.).
 */
class MusicPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_FILE_PATH = "file_path"
        /** When true, only the single tapped file is shown — no swiping to siblings. */
        const val EXTRA_SINGLE_FILE_MODE = "single_file_mode"
        /** When true, the music player opens without auto-playing — waits for user to tap play. */
        const val EXTRA_NO_AUTOPLAY = "no_autoplay"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    private var mediaFiles by mutableStateOf<List<File>>(emptyList())
    private var initialIndex by mutableIntStateOf(0)
    private var loopEnabled by mutableStateOf(false)
    private var autoPlay by mutableStateOf(true)
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
                    MusicPlayerScreen(
                        mediaFiles = mediaFiles,
                        initialIndex = initialIndex,
                        loopEnabled = loopEnabled,
                        autoPlay = autoPlay,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun loadFromIntent(intent: Intent?): Boolean {
        intent ?: return false

        // ── External ACTION_VIEW intent (opened from outside our app) ──────────
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val resolvedFile = FileUtils.resolveUriToFile(this, uri) ?: return false
            if (!FileUtils.isAudioFile(resolvedFile)) return false

            // Try to load all sibling audio files from the same folder
            val folder = resolvedFile.parentFile
            val files: List<File> = if (
                folder != null &&
                folder.canRead() &&
                !resolvedFile.absolutePath.startsWith(cacheDir.absolutePath)
            ) {
                // Load every audio track in the same folder
                FileUtils.getAudioFilesInFolder(this, folder)
                    .ifEmpty { listOf(resolvedFile) }
            } else {
                // Fallback: file came from a temp/cache location — show it alone
                listOf(resolvedFile)
            }

            mediaFiles = files
            initialIndex = files.indexOfFirst {
                it.absolutePath == resolvedFile.absolutePath
            }.coerceAtLeast(0)
            loopEnabled = files.size > 1
            autoPlay = true
            return true
        }

        // ── Internal intent (launched from within our app) ────────────────────
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: return false
        val targetFile = File(filePath)
        val singleFileMode = intent.getBooleanExtra(EXTRA_SINGLE_FILE_MODE, false)

        val files: List<File> = if (singleFileMode) {
            // Only open the one tapped file — no sibling swiping
            if (targetFile.exists() && FileUtils.isAudioFile(targetFile)) listOf(targetFile)
            else emptyList()
        } else {
            val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: return false
            val folder = File(folderPath)
            // Load all audio files in the folder (same type as the tapped file)
            FileUtils.getAudioFilesInFolder(this, folder).ifEmpty {
                // Folder gone / unreadable — fall back to the single tapped file
                if (targetFile.exists() && FileUtils.isAudioFile(targetFile)) listOf(targetFile)
                else emptyList()
            }
        }

        if (files.isEmpty()) return false

        mediaFiles = files
        initialIndex = files.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
        // Enable loop swiping so the user can swipe endlessly through tracks
        loopEnabled = true
        // Respect no-autoplay flag (e.g. when launched from file explorer toolbar)
        autoPlay = !intent.getBooleanExtra(EXTRA_NO_AUTOPLAY, false)
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

