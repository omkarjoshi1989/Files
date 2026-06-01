package com.gmail.omkarjoshi1989.ui.screens

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
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
        if (!playlistLoaded) {
            // First time landing on audio — load the full playlist at the correct track
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
            // Same track but paused (e.g. swiped away to image then back)
            mc.play()
        }
        // Sync UI immediately
        isAudioPlaying = mc.isPlaying
        audioDuration = if (mc.duration > 0) mc.duration else 0L
        audioPosition = mc.currentPosition
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
                // Landed on an audio page
                musicController?.let { mc -> syncAudioToPage(mc, actualPage) }
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
                    if (loopEnabled && actualCount > 1) {
                        // Calculate the nearest virtual page for the target actual page
                        val currentVirtual = pagerState.settledPage
                        val currentOffset = currentVirtual - currentActualPage
                        val targetVirtual = currentOffset + targetActualPage
                        pagerState.animateScrollToPage(targetVirtual)
                    } else {
                        pagerState.animateScrollToPage(targetActualPage)
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

    LaunchedEffect(isActive, mediaUri) {
        if (isActive) {
            if (exoPlayer.currentMediaItem == null) {
                exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
                exoPlayer.prepare()
            }
            // Restore last-played position for this video if available
            try {
                val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
                val saved = prefs.getLong(file.absolutePath, 0L)
                if (saved > 0L) exoPlayer.seekTo(saved)
            } catch (_: Exception) {
            }
            // Explicitly pause music service when video page becomes active
            musicController?.let { mc ->
                if (mc.isPlaying) mc.pause()
            }
        } else {
            // Release decoder/sample buffers as soon as page is no longer active.
            // Save current playback position for resume
            try {
                val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
                prefs.edit { putLong(file.absolutePath, exoPlayer.currentPosition) }
            } catch (_: Exception) {}
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            // Persist position when the page is disposed
            try {
                val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
                prefs.edit { putLong(file.absolutePath, exoPlayer.currentPosition) }
            } catch (_: Exception) {}
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
                            val controllerVisible = visibility == android.view.View.VISIBLE
                            onImmersiveChange(!controllerVisible)
                        }
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )
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
    onTap: () -> Unit
) {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Album art: fill width (left/right) and wrap height to preserve aspect ratio
            val albumArt by produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath) {
                value = try {
                    withContext(Dispatchers.IO) { loadAlbumArt(file) }
                } catch (_: Exception) {
                    null
                }
            }

            if (albumArt != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArt)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback audio icon when no art embedded
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Audio",
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFFFF9800)
                    )
                }
            }

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

            // Seek / Progress controls
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableLongStateOf(currentPosition) }

            val progressFraction = if (duration > 0) {
                if (isSeeking) seekPosition.toFloat() / duration.toFloat() else currentPosition.toFloat() / duration.toFloat()
            } else 0f

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
                            try {
                                mc.seekTo(seekPosition)
                            } catch (_: Exception) {
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
            // Playback controls: Repeat · Previous · Play/Pause · Next — all circular, in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val repeatActive = repeatMode == Player.REPEAT_MODE_ONE

                // Repeat toggle (circular, sized like skip buttons)
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
                    onClick = {
                        controller?.let { mc ->
                            if (mc.isPlaying) {
                                mc.pause()
                            } else {
                                mc.playWhenReady = true
                            }
                        }
                    },
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

