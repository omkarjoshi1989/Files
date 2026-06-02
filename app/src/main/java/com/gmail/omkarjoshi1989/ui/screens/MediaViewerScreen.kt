package com.gmail.omkarjoshi1989.ui.screens

import android.app.Activity
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.shape.CircleShape
// Shuffle removed: no import
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.gmail.omkarjoshi1989.service.MusicPlaybackService
import com.gmail.omkarjoshi1989.util.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import java.io.File
import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<File>,
    initialIndex: Int,
    loopEnabled: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // ── Loop swiping: use a large virtual page count so user can swipe endlessly ──
    val actualCount = mediaFiles.size
    val virtualPageCount = if (loopEnabled && actualCount > 1) actualCount * 10_000 else actualCount
    val virtualInitialPage = if (loopEnabled && actualCount > 1) {
        // Start in the middle of the virtual range at the correct offset
        (5_000 * actualCount) + initialIndex
    } else {
        initialIndex
    }

    val pagerState = rememberPagerState(
        initialPage = virtualInitialPage,
        pageCount = { virtualPageCount }
    )

    // Map virtual page to actual index
    fun virtualToActual(virtualPage: Int): Int {
        return if (loopEnabled && actualCount > 1) virtualPage.mod(actualCount) else virtualPage
    }

    val currentFile = mediaFiles.getOrNull(virtualToActual(pagerState.settledPage))
    var isImmersive by remember { mutableStateOf(false) }

    // ── Swipe lock: disable left/right swipe when in landscape + video page ──
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCurrentFileVideo = currentFile != null && FileUtils.isVideoFile(currentFile)
    val pagerScrollEnabled = !(isLandscape && isCurrentFileVideo)

    // ── Screen-level brightness override (shared across video pages) ────
    val screenActivity = remember(context) {
        var c: Context? = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        c as? Activity
    }
    var screenBrightness by remember(screenActivity) {
        val seeded = if (screenActivity == null) {
            0.5f
        } else {
            val current = screenActivity.window.attributes.screenBrightness
            if (current >= 0f) current else {
                try {
                    Settings.System.getInt(
                        screenActivity.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS
                    ) / 255f
                } catch (_: Exception) {
                    0.5f
                }
            }
        }
        mutableStateOf(seeded.coerceIn(0f, 1f))
    }
    LaunchedEffect(screenActivity, screenBrightness) {
        screenActivity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
            it.window.attributes = lp
        }
    }
    DisposableEffect(screenActivity) {
        onDispose {
            screenActivity?.let {
                val lp = it.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = lp
            }
        }
    }

    // ── Audio playlist mapping ──────────────────────────────────────────
    // audioPageIndices: list of pager indices that are audio files (in pager order)
    // pageToPlaylistIndex: pager page index → playlist position
    // playlistToPageIndex: playlist position → pager page index
    val audioPageIndices = remember(mediaFiles) {
        mediaFiles.mapIndexedNotNull { index, file ->
            if (FileUtils.isAudioFile(file)) index else null
        }
    }
    val pageToPlaylistIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> pageIdx to playlistIdx }
    }
    val playlistToPageIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> playlistIdx to pageIdx }
    }
    val hasAudioFiles = audioPageIndices.isNotEmpty()

    // ── Shared music controller state (hoisted) ─────────────────────────
    var musicController by remember { mutableStateOf<MediaController?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioPosition by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    // repeat/shuffle state are maintained per-audio page below
    // Hoist repeat mode so the UI can reflect the player's repeat state across pages.
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }
    // Tracks the player's current media-item index inside the playlist.
    // -1 means "no audio track active yet".
    var playerTrackIndex by remember { mutableIntStateOf(-1) }
    // Whether we have already loaded our playlist into the service player.
    var playlistLoaded by remember { mutableStateOf(false) }
    // True while the pager is being scrolled programmatically (due to a player
    // track auto-advance).  Prevents snapshotFlow from calling syncAudioToPage
    // and seeking the player backward to an intermediate/stale page.
    var isProgrammaticPagerScroll by remember { mutableStateOf(false) }
    // Incremented on ON_RESUME to trigger an instant pager snap when the pager
    // is stale (animations couldn't run while the screen was off).
    var resumeSyncNeeded by remember { mutableStateOf(false) }

    // Build MediaItem list once (stable across recompositions)
    val audioMediaItems = remember(mediaFiles, audioPageIndices) {
        audioPageIndices.map { pageIdx ->
            val file = mediaFiles[pageIdx]
            MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.nameWithoutExtension)
                        .setArtist("Files App")
                        .build()
                )
                .build()
        }
    }

    // ── Connect to MusicPlaybackService once at screen level ────────────
    if (hasAudioFiles) {
        DisposableEffect(Unit) {
            val sessionToken = SessionToken(
                context,
                android.content.ComponentName(context, MusicPlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

            controllerFuture.addListener(
                {
                    val mc = controllerFuture.get()
                    musicController = mc

                    // Check if the service already has the exact same playlist loaded
                    val samePlaylist = mc.mediaItemCount == audioMediaItems.size &&
                            audioMediaItems.indices.all { i ->
                                mc.getMediaItemAt(i).localConfiguration?.uri ==
                                        audioMediaItems[i].localConfiguration?.uri
                            }

                    if (samePlaylist) {
                        // Playlist already loaded (e.g. returning from notification) — sync UI
                        playlistLoaded = true
                        isAudioPlaying = mc.isPlaying
                        if (mc.duration > 0) audioDuration = mc.duration
                        audioPosition = mc.currentPosition
                        playerTrackIndex = mc.currentMediaItemIndex
                        // Sync the UI repeat mode from the player
                        try { repeatMode = mc.repeatMode } catch (_: Exception) { }
                    } else {
                        // If we are about to load a new playlist later, prefer the UI's repeatMode
                        try { mc.repeatMode = repeatMode } catch (_: Exception) { }
                    }

                    // Listen for state changes from the player / notification controls
                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isAudioPlaying = playing
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                audioDuration = mc.duration
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            playerTrackIndex = mc.currentMediaItemIndex
                            audioDuration = if (mc.duration > 0) mc.duration else 0L
                            audioPosition = mc.currentPosition
                        }

                        override fun onRepeatModeChanged(repeatModeFromPlayer: Int) {
                            repeatMode = repeatModeFromPlayer
                        }
                    })
                 },
                 MoreExecutors.directExecutor()
             )

            onDispose {
                MediaController.releaseFuture(controllerFuture)
                musicController = null
            }
        }
    }

    // ── Helper: load/seek the audio playlist and auto-play ─────────────
    // Extracted so both the snapshotFlow and the controller-ready effect
    // can call the same logic without duplication.
    fun syncAudioToPage(mc: MediaController, page: Int) {
        val playlistIdx = pageToPlaylistIndex[page] ?: return
        // Treat the playlist as unloaded if either we haven't loaded it yet, OR
        // the service was killed while the screen was off/idle (mediaItemCount == 0).
        val needsReload = !playlistLoaded || mc.mediaItemCount == 0
        if (needsReload) {
            // First time landing on audio, or service was restarted — reload playlist
            mc.setMediaItems(audioMediaItems, playlistIdx, 0L)
            mc.prepare()
            // Do not override existing repeatMode here; the UI's hoisted `repeatMode`
            // was already applied to the controller when it connected. This preserves
            // the user's repeat choice across swipes.
            mc.play()                      // ← auto-play
            playlistLoaded = true
            playerTrackIndex = playlistIdx
        } else if (mc.currentMediaItemIndex != playlistIdx) {
            mc.seekToDefaultPosition(playlistIdx)
            mc.play()                      // ← auto-play on swipe
            playerTrackIndex = playlistIdx
        } else if (!mc.isPlaying) {
            // Same track but paused (e.g. swiped away to image then back,
            // or player reached STATE_IDLE/STATE_ENDED after long idle).
            when (mc.playbackState) {
                Player.STATE_IDLE -> { mc.prepare(); mc.play() }
                Player.STATE_ENDED -> { mc.seekToDefaultPosition(playlistIdx); mc.play() }
                else -> mc.play()
            }
        }
        // Sync UI immediately
        isAudioPlaying = mc.isPlaying
        audioDuration = if (mc.duration > 0) mc.duration else 0L
        audioPosition = mc.currentPosition
    }

    // ── Helper: resume/play current audio, recovering from service kill ─────
    fun playCurrentAudio() {
        val mc = musicController ?: return
        val actualPage = virtualToActual(pagerState.settledPage)
        val playlistIdx = pageToPlaylistIndex[actualPage] ?: return
        when {
            mc.mediaItemCount == 0 || !playlistLoaded -> {
                // Service was killed (no media items) or never loaded — reload playlist,
                // resuming from the last known position so the user doesn't lose progress.
                mc.setMediaItems(audioMediaItems, playlistIdx, audioPosition.coerceAtLeast(0L))
                mc.prepare()
                mc.play()
                playlistLoaded = true
                playerTrackIndex = playlistIdx
            }
            mc.playbackState == Player.STATE_ENDED -> {
                mc.seekToDefaultPosition(playlistIdx)
                mc.play()
            }
            mc.playbackState == Player.STATE_IDLE -> {
                mc.prepare()
                mc.play()
            }
            else -> mc.play()
        }
    }

    // ── Pager page settled → seek player or pause ────────────────────────
    // Uses settledPage so playback only starts after the swipe animation
    // finishes, preventing audio blips on intermediate pages.
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            isImmersive = false

            val actualPage = virtualToActual(page)

            val playlistIdx = pageToPlaylistIndex[actualPage]
            if (playlistIdx != null) {
                if (!isProgrammaticPagerScroll) {
                    // User-initiated swipe: seek the player to the new page's track.
                    musicController?.let { mc -> syncAudioToPage(mc, actualPage) }
                } else {
                    // Programmatic scroll (player auto-advanced to next track):
                    // Do NOT seek the player — it is already on the correct track.
                    // Only refresh the UI position/duration state.
                    musicController?.let { mc ->
                        isAudioPlaying = mc.isPlaying
                        if (mc.duration > 0) audioDuration = mc.duration
                        audioPosition = mc.currentPosition
                    }
                }
            } else {
                // Non-audio page — pause audio if playing
                musicController?.let { mc ->
                    if (mc.isPlaying) mc.pause()
                }
            }
        }
    }

    // ── Controller connected → catch up with current page ────────────────
    // Fixes the race where the snapshotFlow already fired before the
    // MediaController was ready (musicController was null at that point).
    LaunchedEffect(musicController) {
        val mc = musicController ?: return@LaunchedEffect
        val actualPage = virtualToActual(pagerState.settledPage)
        val playlistIdx = pageToPlaylistIndex[actualPage]
        if (playlistIdx != null) {
            syncAudioToPage(mc, actualPage)
        }
    }

    // ── Player track changed externally (notification skip) → scroll pager ──
    LaunchedEffect(playerTrackIndex) {
        if (playerTrackIndex >= 0) {
            val targetActualPage = playlistToPageIndex[playerTrackIndex]
            if (targetActualPage != null) {
                val currentActualPage = virtualToActual(pagerState.settledPage)
                if (currentActualPage != targetActualPage) {
                    // Mark as programmatic so snapshotFlow won't re-seek the player
                    // backward when the pager settles (including on a cancelled animation).
                    isProgrammaticPagerScroll = true
                    try {
                        if (loopEnabled && actualCount > 1) {
                            // Calculate the nearest virtual page for the target actual page
                            val currentVirtual = pagerState.settledPage
                            val currentOffset = currentVirtual - currentActualPage
                            val targetVirtual = currentOffset + targetActualPage
                            pagerState.animateScrollToPage(targetVirtual)
                        } else {
                            pagerState.animateScrollToPage(targetActualPage)
                        }
                    } finally {
                        // Always clear the flag — both on successful completion and
                        // on cancellation (when the next track fires before this
                        // animation finishes).  The next LaunchedEffect will re-set it.
                        isProgrammaticPagerScroll = false
                    }
                }
            }
        }
    }

    // ── Periodic position update while audio is playing ─────────────────
    // Keep position updated regardless of play state so the seekbar stays in sync.
    LaunchedEffect(musicController) {
        while (musicController != null) {
            musicController?.let { audioPosition = it.currentPosition; audioDuration = if (it.duration > 0) it.duration else audioDuration }
            delay(500)
        }
    }

    // ── Re-sync state after screen unlock / returning from background ─────
    // When the screen turns off and back on (or the app is resumed from background),
    // the player state may have drifted (e.g. service killed, audio focus lost).
    // This observer catches ON_RESUME to refresh all UI state and mark the playlist
    // as needing a reload if the service was killed (mediaItemCount == 0).
    if (hasAudioFiles) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val mc = musicController ?: return@LifecycleEventObserver
                    // Refresh UI state from the actual player
                    isAudioPlaying = mc.isPlaying
                    if (mc.duration > 0) audioDuration = mc.duration
                    audioPosition = mc.currentPosition
                    // If service was killed (player has no media items), mark playlist as
                    // unloaded so the next play press (or syncAudioToPage call) reloads it.
                    if (mc.mediaItemCount == 0) {
                        playlistLoaded = false
                    } else {
                        // Pager may be stale: animations don't run with screen off, so
                        // settledPage might still point at the track that was playing when
                        // the screen turned off.  Request an instant snap to the correct page.
                        val currentActualPage = virtualToActual(pagerState.settledPage)
                        val expectedPage = playlistToPageIndex[mc.currentMediaItemIndex]
                        if (expectedPage != null && currentActualPage != expectedPage) {
                            resumeSyncNeeded = !resumeSyncNeeded  // toggle to trigger LaunchedEffect
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // ── Instantly snap the pager to the player's current track on resume ──
    // Runs whenever resumeSyncNeeded flips, which means the screen just came
    // back on and the pager is pointing at a stale track.
    if (hasAudioFiles) {
        LaunchedEffect(resumeSyncNeeded) {
            val mc = musicController ?: return@LaunchedEffect
            val targetActualPage = playlistToPageIndex[mc.currentMediaItemIndex]
                ?: return@LaunchedEffect
            val currentActualPage = virtualToActual(pagerState.settledPage)
            if (currentActualPage != targetActualPage) {
                val targetVirtual = if (loopEnabled && actualCount > 1) {
                    pagerState.settledPage - currentActualPage + targetActualPage
                } else targetActualPage
                isProgrammaticPagerScroll = true
                try {
                    // scrollToPage = instant snap (no animation); avoids the
                    // jerky "catch-up" animation that would otherwise play.
                    pagerState.scrollToPage(targetVirtual)
                } finally {
                    isProgrammaticPagerScroll = false
                }
            }
        }
    }

    // ── Control system bars ─────────────────────────────────────────────
    val view = LocalView.current
    val activity = (view.context as? android.app.Activity)

    LaunchedEffect(isImmersive) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            if (isImmersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Restore system bars when leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,   // prevent pre-rendering adjacent pages
            userScrollEnabled = pagerScrollEnabled,
            key = { "${it}_${mediaFiles[virtualToActual(it)].absolutePath}" }
        ) { page ->
            val actualPage = virtualToActual(page)
            val file = mediaFiles[actualPage]
            val isCurrentPage = pagerState.settledPage == page

            when {
                FileUtils.isImageFile(file) -> ImagePage(
                    file = file,
                    onTap = { isImmersive = !isImmersive }
                )
                FileUtils.isVideoFile(file) -> VideoPage(
                    file = file,
                    isActive = isCurrentPage,
                    musicController = musicController,
                    brightness = screenBrightness,
                    onBrightnessChange = { screenBrightness = it.coerceIn(0f, 1f) },
                    onImmersiveChange = { immersive -> isImmersive = immersive }
                )
                                FileUtils.isAudioFile(file) -> AudioPage(
                                    file = file,
                                    controller = musicController,
                                    isPlaying = isAudioPlaying && isCurrentPage,
                                    currentPosition = if (isCurrentPage) audioPosition else 0L,
                                    duration = if (isCurrentPage) audioDuration else 0L,
                                    repeatMode = repeatMode,
                                    onToggleRepeat = {
                                        val next = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                                        repeatMode = next
                                        musicController?.let { mc ->
                                            try { mc.repeatMode = next } catch (_: Exception) { try { mc.setRepeatMode(next) } catch (_: Exception) {} }
                                        }
                                    },
                                    onPlayPause = {
                                        if (musicController?.isPlaying == true) musicController?.pause()
                                        else playCurrentAudio()
                                    },
                                    onTap = { isImmersive = !isImmersive }
                                )
            }
        }

        // Top bar overlay with animation
        AnimatedVisibility(
            visible = !isImmersive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentFile?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${virtualToActual(pagerState.settledPage) + 1} / ${actualCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.systemBarsPadding()
            )
        }
    }
}

@Composable
private fun ImagePage(file: File, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    file: File,
    isActive: Boolean,
    musicController: MediaController?,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onImmersiveChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val mediaUri = remember(file.absolutePath) { android.net.Uri.fromFile(file) }

    val exoPlayer = remember(file.absolutePath) {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 2_000,
                        /* maxBufferMs = */ 10_000,
                        /* bufferForPlaybackMs = */ 300,
                        /* bufferForPlaybackAfterRebufferMs = */ 700
                    )
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // Helper: save current position synchronously (commit=true) so the write
    // is guaranteed to reach disk even if the process is killed immediately after.
    fun savePosition() {
        try {
            val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 0L && (dur <= 0L || pos < dur - 3_000L)) {
                prefs.edit(commit = true) { putLong(file.absolutePath, pos) }
            } else if (pos > 0L) {
                // Near or past end — clear so next open starts fresh
                prefs.edit(commit = true) { remove(file.absolutePath) }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isActive, mediaUri) {
        if (isActive) {
            if (exoPlayer.currentMediaItem == null) {
                // Read saved position BEFORE prepare() so we can pass it directly
                // to setMediaItem — this is the most reliable way to start ExoPlayer
                // at a specific position (avoids a race between prepare() and seekTo()).
                val startPosition = try {
                    val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
                    prefs.getLong(file.absolutePath, 0L).coerceAtLeast(0L)
                } catch (_: Exception) { 0L }

                exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri), startPosition)
                exoPlayer.prepare()
            }
            // Explicitly pause music service when video page becomes active
            musicController?.let { mc ->
                if (mc.isPlaying) mc.pause()
            }
            // Auto-play so resume position takes effect immediately
            exoPlayer.playWhenReady = true
        } else {
            // Page is no longer active — save position and release resources.
            savePosition()
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Clear stored position when playback naturally completes.
    DisposableEffect(exoPlayer, file.absolutePath) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    try {
                        val prefs = context.getSharedPreferences(
                            "media_positions",
                            Context.MODE_PRIVATE
                        )
                        prefs.edit(commit = true) { remove(file.absolutePath) }
                    } catch (_: Exception) {}
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Periodically persist the current playback position while the page is
    // active, so the resume position survives process death.
    LaunchedEffect(exoPlayer, file.absolutePath, isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(3_000)
            savePosition()
        }
    }

    // Save position on every ON_PAUSE (guaranteed before the process can be killed).
    // This is the most reliable save point and uses a synchronous commit.
    val lifecycleOwnerForSave = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerForSave, file.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isActive) {
                savePosition()
            }
        }
        lifecycleOwnerForSave.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerForSave.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            // Synchronous commit so the write reaches disk before the process dies.
            savePosition()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        var controlsVisible by remember { mutableStateOf(true) }
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
        var widthPx by remember { mutableIntStateOf(0) }
        var speedBoostActive by remember { mutableStateOf(false) }

        // ── Background playback toggle (configured in Settings screen) ──
        val backgroundPlaybackEnabled = remember(context) {
            com.gmail.omkarjoshi1989.util.SettingsManager
                .isBackgroundPlaybackEnabled(context)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, backgroundPlaybackEnabled) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && !backgroundPlaybackEnabled) {
                    try { exoPlayer.pause() } catch (_: Exception) {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ── Hoisted volume state ────────────────────────────────────────
        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        val maxVolume = remember(audioManager) {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }
        var rawVolume by remember(audioManager) {
            mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        DisposableEffect(audioManager) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    rawVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
            onDispose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    controllerHideOnTouch = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            val visible = visibility == android.view.View.VISIBLE
                            controlsVisible = visible
                            onImmersiveChange(!visible)
                        }
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerViewRef = playerView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap overlay (active only while controller is hidden): single tap
        // re-shows the controller; double-tap on the left half rewinds 10s,
        // on the right half fast-forwards 10s. When the controller IS
        // visible, this Box is a passive layer so taps on play/pause/seek
        // inside PlayerView are not intercepted.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width }
                .then(
                    if (!controlsVisible) {
                        Modifier.pointerInput(exoPlayer) {
                            var boostActive = false
                            detectTapGestures(
                                onPress = {
                                    tryAwaitRelease()
                                    if (boostActive) {
                                        try { exoPlayer.setPlaybackSpeed(1f) } catch (_: Exception) {}
                                        boostActive = false
                                        speedBoostActive = false
                                    }
                                },
                                onLongPress = {
                                    if (exoPlayer.isPlaying) {
                                        try { exoPlayer.setPlaybackSpeed(1.5f) } catch (_: Exception) {}
                                        boostActive = true
                                        speedBoostActive = true
                                    }
                                },
                                onTap = {
                                    playerViewRef?.showController()
                                },
                                onDoubleTap = { offset ->
                                    if (widthPx > 0) {
                                        val isLeft = offset.x < widthPx / 2f
                                        val duration = exoPlayer.duration
                                        val current = exoPlayer.currentPosition
                                        val target = if (isLeft) {
                                            (current - 10_000L).coerceAtLeast(0L)
                                        } else {
                                            val upper = if (duration > 0) duration else Long.MAX_VALUE
                                            (current + 10_000L).coerceAtMost(upper)
                                        }
                                        exoPlayer.seekTo(target)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                VerticalSliderColumn(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.BrightnessHigh,
                            contentDescription = "Brightness",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    value = brightness,
                    onValueChange = { onBrightnessChange(it) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                )
                VerticalSliderColumn(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    value = rawVolume.toFloat() / maxVolume.toFloat(),
                    onValueChange = { f ->
                        val newVolume = (f * maxVolume).toInt().coerceIn(0, maxVolume)
                        if (newVolume != rawVolume) {
                            rawVolume = newVolume
                            try {
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVolume,
                                    0
                                )
                            } catch (_: SecurityException) {
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = speedBoostActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "1.5x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun VerticalSliderColumn(
    icon: @Composable () -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .pointerInput(Unit) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(width = 48.dp, height = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -90f }
                    .requiredSize(width = 220.dp, height = 48.dp)
            )
        }
    }
}

/**
 * Pure UI component — does NOT connect to the service itself.
 * All playback state is hoisted to [MediaViewerScreen].
 */
@Composable
private fun AudioPage(
    file: File,
    controller: MediaController?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onTap: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Album art loaded once per file
    val albumArt by produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath) {
        value = try {
            withContext(Dispatchers.IO) { loadAlbumArt(file) }
        } catch (_: Exception) {
            null
        }
    }

    // Seek state hoisted so both portrait and landscape can share it
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(currentPosition) }
    val progressFraction = if (duration > 0) {
        if (isSeeking) seekPosition.toFloat() / duration.toFloat()
        else currentPosition.toFloat() / duration.toFloat()
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // ── Landscape layout: album art on left half, controls on right half ──
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left half — album art
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AudioAlbumArt(albumArt = albumArt, landscapeMode = true)
                }

                // Right half — all controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(start = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // File name
                    Text(
                        text = file.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // File size
                    Text(
                        text = FileUtils.formatFileSize(file.length()),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Seek bar
                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            if (duration > 0) {
                                isSeeking = true
                                seekPosition = (fraction * duration).toLong()
                            }
                        },
                        onValueChangeFinished = {
                            controller?.let { mc ->
                                if (duration > 0) {
                                    try { mc.seekTo(seekPosition) } catch (_: Exception) {
                                        try { mc.seekToDefaultPosition() } catch (_: Exception) {}
                                    }
                                }
                            }
                            isSeeking = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        valueRange = 0f..1f
                    )

                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                                    // Playback controls row
                                    AudioControlsRow(
                                        isPlaying = isPlaying,
                                        repeatMode = repeatMode,
                                        onToggleRepeat = onToggleRepeat,
                                        onPlayPause = onPlayPause,
                                        controller = controller
                                    )
                                }
                            }
                        } else {
                            // ── Portrait layout: original vertical column ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                AudioAlbumArt(albumArt = albumArt, landscapeMode = false)

                Spacer(modifier = Modifier.height(24.dp))

                // File name
                Text(
                    text = file.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // File size
                Text(
                    text = FileUtils.formatFileSize(file.length()),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Seek bar
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        if (duration > 0) {
                            isSeeking = true
                            seekPosition = (fraction * duration).toLong()
                        }
                    },
                    onValueChangeFinished = {
                        controller?.let { mc ->
                            if (duration > 0) {
                                try { mc.seekTo(seekPosition) } catch (_: Exception) {
                                    try { mc.seekToDefaultPosition() } catch (_: Exception) {}
                                }
                            }
                        }
                        isSeeking = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback controls row
                AudioControlsRow(
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    onToggleRepeat = onToggleRepeat,
                    onPlayPause = onPlayPause,
                    controller = controller
                )
            }
        }
    }
}

/** Album art image or fallback music-note icon. */
@Composable
private fun AudioAlbumArt(albumArt: Bitmap?, landscapeMode: Boolean) {
    if (albumArt != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(albumArt)
                .crossfade(true)
                .build(),
            contentDescription = "Album art",
            modifier = if (landscapeMode) {
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(12.dp))
            },
            contentScale = ContentScale.Fit
        )
    } else {
        // Fallback audio icon when no art is embedded
        val iconSize = if (landscapeMode) 96.dp else 64.dp
        val boxSize = if (landscapeMode) 160.dp else 120.dp
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Audio",
                modifier = Modifier.size(iconSize),
                tint = Color(0xFFFF9800)
            )
        }
    }
}

/** Repeat · Previous · Play/Pause · Next controls row, shared by both orientations. */
@Composable
private fun AudioControlsRow(
    isPlaying: Boolean,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    controller: MediaController?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val repeatActive = repeatMode == Player.REPEAT_MODE_ONE

        // Repeat toggle
        IconButton(
            onClick = { onToggleRepeat() },
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (repeatActive) Color(0xFFFF9800).copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.12f)
                )
        ) {
            Icon(
                imageVector = if (repeatActive) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repeat",
                modifier = Modifier.size(32.dp),
                tint = if (repeatActive) Color(0xFFFF9800) else Color.White
            )
        }

        // Previous track
        IconButton(
            onClick = {
                controller?.let { mc ->
                    try { mc.seekToPrevious() } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }

        // Play / Pause (primary, larger)
        IconButton(
            onClick = { onPlayPause() },
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9800))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(42.dp),
                tint = Color.White
            )
        }

        // Next track
        IconButton(
            onClick = {
                controller?.let { mc ->
                    try { mc.seekToNext() } catch (_: Exception) {}
                }
            },
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun loadAlbumArt(file: File): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(file.absolutePath)
        val art = mmr.embeddedPicture
        if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
    } catch (_: Exception) {
        null
    } finally {
        try {
            mmr.release()
        } catch (_: Exception) {
        }
    }
}

