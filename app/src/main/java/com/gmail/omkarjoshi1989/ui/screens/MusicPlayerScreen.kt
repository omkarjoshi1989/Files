package com.gmail.omkarjoshi1989.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gmail.omkarjoshi1989.service.MusicPlaybackService
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.MusicResumeManager
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    mediaFiles: List<File>,
    initialIndex: Int,
    loopEnabled: Boolean = true,
    autoPlay: Boolean = true,
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
    // All files in MusicPlayerScreen are audio, so the page index equals the playlist index.
    val audioPageIndices = remember(mediaFiles) {
        mediaFiles.indices.toList()
    }
    val pageToPlaylistIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> pageIdx to playlistIdx }
    }
    val playlistToPageIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> playlistIdx to pageIdx }
    }

    // ── Shared music controller state (hoisted) ─────────────────────────
    var musicController by remember { mutableStateOf<MediaController?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioPosition by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
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
    // When autoPlay=false (launched from toolbar "resume" button), suppress the
    // very first mc.play() so the player loads the track but stays paused until
    // the user taps the play button themselves.
    var initialLoadDone by remember { mutableStateOf(autoPlay) }

    // Build MediaItem list once (stable across recompositions)
    val audioMediaItems = remember(mediaFiles, audioPageIndices) {
        audioPageIndices.map { pageIdx ->
            val file = mediaFiles[pageIdx]
            MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(FileUtils.stripNumericPrefix(file.nameWithoutExtension))
                        .setArtist("Files App")
                        .build()
                )
                .build()
        }
    }

    // ── Connect to MusicPlaybackService once at screen level ────────────
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
                        // Save as new most-recent only for genuine track switches
                        // (auto-advance at end of song, or notification/button skip).
                        // • PLAYLIST_CHANGED → skip: syncAudioToPage already saved the correct
                        //   resume position before calling setMediaItems.
                        // • REPEAT → skip: same song starting over, no position reset needed.
                        val isGenuineSwitch =
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                        if (isGenuineSwitch) {
                            val newFile = mediaFiles.getOrNull(mc.currentMediaItemIndex)
                            if (newFile != null) {
                                MusicResumeManager.saveLastPlayed(
                                    context,
                                    newFile.parentFile?.absolutePath ?: "",
                                    newFile.absolutePath,
                                    0L   // new song starts from the beginning
                                )
                            }
                        }
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

    // ── Helper: load/seek the audio playlist and auto-play ─────────────
    // Extracted so both the snapshotFlow and the controller-ready effect
    // can call the same logic without duplication.
    fun syncAudioToPage(mc: MediaController, page: Int) {
        val playlistIdx = pageToPlaylistIndex[page] ?: return
        val file = mediaFiles.getOrNull(playlistIdx)
        // Treat the playlist as unloaded if either we haven't loaded it yet, OR
        // the service was killed while the screen was off/idle (mediaItemCount == 0).
        val needsReload = !playlistLoaded || mc.mediaItemCount == 0
        if (needsReload) {
            // ── Determine resume position ───────────────────────────────
            // If this file is THE most-recently-played song, resume from where
            // the user left off.  Otherwise start fresh and register this file
            // as the new most-recent song (position 0).
            val lastFilePath = MusicResumeManager.getLastFilePath(context)
            val resumePosition: Long = if (
                file != null && file.absolutePath == lastFilePath
            ) {
                MusicResumeManager.getLastPositionMs(context)
            } else {
                0L
            }
            // Persist this file as the (only) most-recent song.
            if (file != null) {
                MusicResumeManager.saveLastPlayed(
                    context,
                    file.parentFile?.absolutePath ?: "",
                    file.absolutePath,
                    resumePosition          // keep saved position accurate from the start
                )
            }
            // First time landing on audio, or service was restarted — reload playlist
            mc.setMediaItems(audioMediaItems, playlistIdx, resumePosition)
            mc.prepare()
            // Do not override existing repeatMode here; the UI's hoisted `repeatMode`
            // was already applied to the controller when it connected. This preserves
            // the user's repeat choice across swipes.
            if (initialLoadDone) {
                mc.play()                  // ← auto-play
            } else {
                // First open from toolbar — prepare the track but wait for user to tap play
                initialLoadDone = true
            }
            playlistLoaded = true
            playerTrackIndex = playlistIdx
        } else if (mc.currentMediaItemIndex != playlistIdx) {
            // User swiped to a different track — this becomes the new most-recent song.
            if (file != null) {
                MusicResumeManager.saveLastPlayed(
                    context,
                    file.parentFile?.absolutePath ?: "",
                    file.absolutePath,
                    0L                      // new song starts from the beginning
                )
            }
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
                // No-op: in MusicPlayerScreen all pages are audio, so this branch is
                // never reached. Kept for safety.
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
    // Also persist the playback position to MusicResumeManager every ~5 s so that
    // relaunching the player (MacroDroid shortcut, fresh Activity start, etc.) can
    // resume from where the user left off.
    LaunchedEffect(musicController) {
        var saveTickCounter = 0
        while (musicController != null) {
            musicController?.let { mc ->
                audioPosition = mc.currentPosition
                audioDuration = if (mc.duration > 0) mc.duration else audioDuration
                // Save position every 10 ticks × 500 ms = ~5 seconds while playing
                if (mc.isPlaying) {
                    saveTickCounter++
                    if (saveTickCounter >= 10) {
                        saveTickCounter = 0
                        MusicResumeManager.savePosition(context, mc.currentPosition)
                    }
                } else {
                    saveTickCounter = 0   // reset counter when paused
                }
            }
            delay(500)
        }
    }

    // ── Re-sync state after screen unlock / returning from background ─────
    // When the screen turns off and back on (or the app is resumed from background),
    // the player state may have drifted (e.g. service killed, audio focus lost).
    // This observer catches ON_RESUME to refresh all UI state and mark the playlist
    // as needing a reload if the service was killed (mediaItemCount == 0).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                // Persist the exact position the moment the user leaves the screen.
                // This is the most-accurate save point (covers home button, recents,
                // notification shade, MacroDroid triggers, etc.).
                val mc = musicController ?: return@LifecycleEventObserver
                val actualPage = virtualToActual(pagerState.settledPage)
                val file = mediaFiles.getOrNull(actualPage)
                if (file != null) {
                    MusicResumeManager.saveLastPlayed(
                        context,
                        file.parentFile?.absolutePath ?: "",
                        file.absolutePath,
                        mc.currentPosition
                    )
                }
            }
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

    // ── Instantly snap the pager to the player's current track on resume ──
    // Runs whenever resumeSyncNeeded flips, which means the screen just came
    // back on and the pager is pointing at a stale track.
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

            MusicAudioPage(
                file = file,
                controller = musicController,
                isPlaying = isAudioPlaying && isCurrentPage,
                currentPosition = if (isCurrentPage) audioPosition else 0L,
                duration = if (isCurrentPage) audioDuration else 0L,
                repeatMode = repeatMode,
                trackIndex = (pageToPlaylistIndex[actualPage] ?: 0) + 1,
                totalTracks = audioPageIndices.size,
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

        // Top bar overlay with animation — always shows back button + track info
        AnimatedVisibility(
            visible = !isImmersive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentFile?.let {
                                FileUtils.stripNumericPrefix(it.nameWithoutExtension)
                            } ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${virtualToActual(pagerState.settledPage) + 1} / $actualCount",
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
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.systemBarsPadding()
            )
        }
    }
}

/**
 * Pure UI component — does NOT connect to the service itself.
 * All playback state is hoisted to [MusicPlayerScreen].
 */
@Composable
private fun MusicAudioPage(
    file: File,
    controller: MediaController?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    trackIndex: Int,
    totalTracks: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Album art loaded once per file
    val albumArt by produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath) {
        value = try {
            withContext(Dispatchers.IO) { musicLoadAlbumArt(file) }
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

    // ── Volume overlay state (hoisted here so it is an overlay, not in flow) ──
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var systemVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Explicit Job-based auto-hide: immune to Compose snapshot timing issues.
    // Every call to startVolumeAutoHide() cancels the previous countdown and
    // starts a fresh 3-second timer.  No LaunchedEffect key-change race involved.
    val volumeScope = rememberCoroutineScope()
    var volumeAutoHideJob by remember { mutableStateOf<Job?>(null) }

    fun startVolumeAutoHide() {
        volumeAutoHideJob?.cancel()
        volumeAutoHideJob = volumeScope.launch {
            delay(3_000)
            showVolumeSlider = false
            volumeAutoHideJob = null
        }
    }

    fun cancelVolumeAutoHide() {
        volumeAutoHideJob?.cancel()
        volumeAutoHideJob = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (showVolumeSlider) { showVolumeSlider = false; cancelVolumeAutoHide() } else onTap()
            },
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
                    MusicAlbumArt(albumArt = albumArt, landscapeMode = true)
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
                    // Folder name + track serial
                    Text(
                        text = "${file.parentFile?.name ?: ""}  ·  $trackIndex / $totalTracks",
                        color = Color(0xFFFF9800),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // File name
                    Text(
                        text = FileUtils.stripNumericPrefix(file.nameWithoutExtension),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                            text = musicFormatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = musicFormatTime(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback controls row
                    MusicControlsRow(
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        onToggleRepeat = onToggleRepeat,
                        onPlayPause = onPlayPause,
                        controller = controller,
                        showVolumeSlider = showVolumeSlider,
                        onToggleVolumeSlider = {
                            if (!showVolumeSlider) {
                                systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                showVolumeSlider = true
                                startVolumeAutoHide()
                            } else {
                                showVolumeSlider = false
                                cancelVolumeAutoHide()
                            }
                        },
                        systemVolume = systemVolume,
                        maxVolume = maxVolume,
                        onVolumeChange = { newVol ->
                            systemVolume = newVol
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                systemVolume,
                                0
                            )
                            // Reset the 3-second auto-hide timer on every drag event
                            startVolumeAutoHide()
                        }
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
                MusicAlbumArt(albumArt = albumArt, landscapeMode = false)

                Spacer(modifier = Modifier.height(24.dp))

                // Folder name + track serial
                Text(
                    text = "${file.parentFile?.name ?: ""}  ·  $trackIndex / $totalTracks",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // File name
                Text(
                    text = FileUtils.stripNumericPrefix(file.nameWithoutExtension),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                        text = musicFormatTime(currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = musicFormatTime(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback controls row
                MusicControlsRow(
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    onToggleRepeat = onToggleRepeat,
                    onPlayPause = onPlayPause,
                    controller = controller,
                    showVolumeSlider = showVolumeSlider,
                    onToggleVolumeSlider = {
                        if (!showVolumeSlider) {
                            systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            showVolumeSlider = true
                            startVolumeAutoHide()
                        } else {
                            showVolumeSlider = false
                            cancelVolumeAutoHide()
                        }
                    },
                    systemVolume = systemVolume,
                    maxVolume = maxVolume,
                    onVolumeChange = { newVol ->
                        systemVolume = newVol
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            newVol,
                            0
                        )
                        startVolumeAutoHide()
                    }
                )
            }
        }
    }
}

/** Album art image or fallback music-note icon. */
@Composable
private fun MusicAlbumArt(albumArt: Bitmap?, landscapeMode: Boolean) {
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

/** Previous · Play/Pause · Next controls row with Repeat button below, all centered. */
@Composable
private fun MusicControlsRow(
    isPlaying: Boolean,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    controller: MediaController?,
    showVolumeSlider: Boolean,
    onToggleVolumeSlider: () -> Unit,
    systemVolume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit
) {
    val repeatActive = repeatMode == Player.REPEAT_MODE_ONE

    // Controls column height: Prev/Play/Next (76dp) + Spacer (16dp) + Repeat/Volume (52dp) = 144dp
    // The volume slider Box uses offset(y) to appear 8dp below the column without shifting layout.
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Prev · Play/Pause · Next ────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── Repeat and Volume toggles (centered below play/pause) ────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Repeat toggle
                IconButton(
                    onClick = { onToggleRepeat() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (repeatActive) Color(0xFFFF9800).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                ) {
                    Icon(
                        imageVector = if (repeatActive) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        modifier = Modifier.size(28.dp),
                        tint = if (repeatActive) Color(0xFFFF9800) else Color.White
                    )
                }

                // Volume toggle button
                IconButton(
                    onClick = { onToggleVolumeSlider() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (showVolumeSlider) Color(0xFFFF9800).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Volume",
                        modifier = Modifier.size(28.dp),
                        tint = if (showVolumeSlider) Color(0xFFFF9800) else Color.White
                    )
                }
            }
        }

        // ── Volume slider overlay — positioned just below the controls column ──
        // offset(y = 152.dp) moves it past the column height (144dp) + 8dp gap.
        // Box does NOT clip children, so it renders below without affecting layout.
        AnimatedVisibility(
            visible = showVolumeSlider,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 152.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1A).copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .pointerInput(Unit) { detectTapGestures { /* consume taps */ } }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = systemVolume.toFloat(),
                        onValueChange = { newValue -> onVolumeChange(newValue.toInt()) },
                        valueRange = 0f..maxVolume.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun musicFormatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun musicLoadAlbumArt(file: File): Bitmap? {
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
