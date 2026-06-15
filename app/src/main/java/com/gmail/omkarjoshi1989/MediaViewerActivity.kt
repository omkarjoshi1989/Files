package com.gmail.omkarjoshi1989

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.gmail.omkarjoshi1989.ui.screens.MediaViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.SettingsManager
import java.io.File

class MediaViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_FILE_PATH = "file_path"
        /** When true, only the single tapped file is shown — no swiping to siblings. */
        const val EXTRA_SINGLE_FILE_MODE = "single_file_mode"
        /** When true, the music player opens without auto-playing — waits for user to tap play. */
        const val EXTRA_NO_AUTOPLAY = "no_autoplay"
        /**
         * When true, swiping includes both images and videos from the same folder
         * (instead of only files of the same type as the opened file).
         */
        const val EXTRA_MIX_IMAGES_VIDEOS = "mix_images_videos"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
        private const val ACTION_PIP_CONTROL = "com.gmail.omkarjoshi1989.PIP_CONTROL"
        private const val REQUEST_PIP_CONTROL = 1001

        /**
         * Direct reference to the active [ExoPlayer] set by [VideoPage] when it is the
         * current visible page.  The broadcast receiver reads this to toggle play/pause
         * without going through any Compose state, which may be paused in PiP mode.
         */
        var activePipPlayer: Player? = null
    }

    private var mediaFiles by mutableStateOf<List<File>>(emptyList())
    private var initialIndex by mutableIntStateOf(0)
    private var loopEnabled by mutableStateOf(false)
    private var autoPlay by mutableStateOf(true)
    private var screenKey by mutableIntStateOf(0)

    // ── PiP state ────────────────────────────────────────────────────────────
    /** True while the activity is rendered inside the PiP window. */
    private var isInPipMode by mutableStateOf(false)
    /** Updated by MediaViewerScreen whenever the active page changes type. */
    private var isOnVideoPage by mutableStateOf(false)
    /** True while the active video is playing — drives the play/pause icon in the PiP overlay. */
    private var isVideoPlaying by mutableStateOf(false)

    /**
     * Receives the play/pause tap from the PiP overlay button and calls the
     * active player directly via [activePipPlayer].
     */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PIP_CONTROL) {
                val player = activePipPlayer ?: return
                if (player.isPlaying) player.pause() else player.play()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        // Register PiP broadcast receiver (local — RECEIVER_NOT_EXPORTED on API 33+)
        val pipFilter = IntentFilter(ACTION_PIP_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, pipFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, pipFilter)
        }

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
                // Reactively update the PiP overlay button icon when playing state changes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LaunchedEffect(isVideoPlaying, isInPipMode) {
                        if (isInPipMode) {
                            setPictureInPictureParams(buildPipParams())
                        }
                    }
                }
                key(screenKey) {
                    MediaViewerScreen(
                        mediaFiles = mediaFiles,
                        initialIndex = initialIndex,
                        loopEnabled = loopEnabled,
                        autoPlay = autoPlay,
                        isInPipMode = isInPipMode,
                        onEnterPip = { enterPipMode() },
                        onVideoPageChanged = { isOnVideoPage = it },
                        onVideoPlayingChanged = { isVideoPlaying = it },
                        onFileDeleted = { deletedFile ->
                            val currentList = mediaFiles
                            val deletedIdx = currentList.indexOfFirst { it.absolutePath == deletedFile.absolutePath }
                            val newList = currentList.toMutableList().also { it.remove(deletedFile) }
                            if (newList.isEmpty()) {
                                finish()
                            } else {
                                val newIndex = if (deletedIdx >= newList.size) newList.size - 1 else deletedIdx.coerceAtLeast(0)
                                mediaFiles = newList
                                initialIndex = newIndex
                                screenKey++
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activePipPlayer = null
        try { unregisterReceiver(pipReceiver) } catch (_: Exception) {}
    }

    // ── PiP helpers ──────────────────────────────────────────────────────────

    /**
     * Builds [PictureInPictureParams] with a play/pause [RemoteAction] whose icon
     * reflects the current [isVideoPlaying] state.
     */
    private fun buildPipParams(): PictureInPictureParams {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw IllegalStateException("PiP requires API 26+")
        }
        val pipIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_PIP_CONTROL,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iconRes = if (isVideoPlaying) android.R.drawable.ic_media_pause
                      else android.R.drawable.ic_media_play
        val label = if (isVideoPlaying) "Pause" else "Play"
        val action = RemoteAction(
            Icon.createWithResource(this, iconRes),
            label, label, pipIntent
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    /** Enter PiP with a 16:9 aspect ratio (API 26+). No-op on older devices. */
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    /**
     * Auto-enter PiP when the user navigates away (home button, recent apps, etc.)
     * and a video is currently the active page.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isOnVideoPage) {
            enterPipMode()
        }
    }

    /** Keep [isInPipMode] in sync with the actual PiP window state. */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun loadFromIntent(intent: Intent?): Boolean {
        intent ?: return false

        // ── External ACTION_VIEW intent (opened from outside our app) ──────────
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val resolvedFile = FileUtils.resolveUriToFile(this, uri) ?: return false
            if (!FileUtils.isImageFile(resolvedFile) && !FileUtils.isVideoFile(resolvedFile)) {
                return false
            }

            val folder = resolvedFile.parentFile
            val files: List<File> = if (
                folder != null &&
                folder.canRead() &&
                !resolvedFile.absolutePath.startsWith(cacheDir.absolutePath)
            ) {
                // Mix images and videos for external intents so the user can swipe
                // through all visual media in the same folder.
                val raw = FileUtils.getVisualMediaFilesInFolder(this, folder)
                    .ifEmpty { listOf(resolvedFile) }
                // Apply the folder's saved sort order (if any)
                if (folder != null) applySortOrder(raw, folder.absolutePath) else raw
            } else {
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
        val mixImagesVideos = intent.getBooleanExtra(EXTRA_MIX_IMAGES_VIDEOS, false)

        val files: List<File> = if (singleFileMode) {
            if (targetFile.exists()) listOf(targetFile) else emptyList()
        } else {
            val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: return false
            val folder = File(folderPath)
            val raw = if (mixImagesVideos && (FileUtils.isImageFile(targetFile) || FileUtils.isVideoFile(targetFile))) {
                // Include both images and videos from the same folder
                FileUtils.getVisualMediaFilesInFolder(this, folder)
                    .ifEmpty { FileUtils.getFilesOfSameType(this, folder, targetFile) }
            } else {
                FileUtils.getFilesOfSameType(this, folder, targetFile)
            }
            // Apply the folder's saved sort order so swipe order matches the folder view
            applySortOrder(raw, folderPath)
        }

        if (files.isEmpty()) return false

        mediaFiles = files
        initialIndex = files.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
        loopEnabled = true
        autoPlay = !intent.getBooleanExtra(EXTRA_NO_AUTOPLAY, false)
        return true
    }

    // ── Sort helpers: mirror FileExplorerViewModel.buildComparator() for File objects ──

    /**
     * Returns the file's creation time in milliseconds.
     * On API 26+ uses NIO BasicFileAttributes (single stat); falls back to lastModified().
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCreationTimeNio(file: File): Long = try {
        java.nio.file.Files.readAttributes(
            file.toPath(),
            java.nio.file.attribute.BasicFileAttributes::class.java
        ).creationTime().toMillis()
    } catch (_: Exception) {
        file.lastModified()
    }

    private fun getCreationTime(file: File): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getCreationTimeNio(file)
        else file.lastModified()

    /**
     * Sorts [files] using the same comparator logic as FileExplorerViewModel,
     * driven by the folder's saved sort preference from [SettingsManager].
     * If no preference is saved the list is returned unchanged (already name-sorted).
     */
    private fun applySortOrder(files: List<File>, folderPath: String): List<File> {
        val sortPref = SettingsManager.getFolderSort(this, folderPath) ?: return files
        val (optionName, ascending) = sortPref
        return when (optionName) {
            "NAME" ->
                if (ascending) files.sortedBy { it.name.lowercase() }
                else files.sortedByDescending { it.name.lowercase() }
            "DATE" ->
                if (ascending) files.sortedBy { getCreationTime(it) }
                else files.sortedByDescending { getCreationTime(it) }
            "SIZE" ->
                if (ascending) files.sortedBy { it.length() }
                else files.sortedByDescending { it.length() }
            "TYPE" ->
                if (ascending)
                    files.sortedWith(compareBy({ it.extension.lowercase() }, { it.name.lowercase() }))
                else
                    files.sortedWith(compareByDescending<File> { it.extension.lowercase() }
                        .thenBy { it.name.lowercase() })
            else -> files
        }
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
