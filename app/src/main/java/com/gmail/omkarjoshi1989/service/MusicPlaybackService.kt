package com.gmail.omkarjoshi1989.service

import android.app.PendingIntent
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gmail.omkarjoshi1989.MediaViewerActivity
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.MusicResumeManager
import java.io.File

/**
 * A foreground service that manages audio playback and shows a media notification
 * with playback controls (play/pause, seek bar, etc.).
 *
 * Media3's MediaSessionService automatically creates and manages the notification
 * when there is an active MediaSession with a playing player.
 */
class MusicPlaybackService : MediaSessionService() {

    companion object {
        /** Sent by [com.gmail.omkarjoshi1989.receiver.MediaButtonEventReceiver]
         *  to cold-start this service and resume the last played track. */
        const val ACTION_PLAY_LAST = "com.gmail.omkarjoshi1989.action.PLAY_LAST"
    }

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()

        // Explicitly register the session so the MediaNotificationManager observes it
        // even before any MediaController connects (e.g. cold-start via Macrodroid).
        // Without this, the foreground media notification would never appear when the
        // service is started directly via startForegroundService() from MediaButtonEventReceiver.
        addSession(mediaSession!!)

        // Update the notification click intent whenever the media item changes
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val session = mediaSession ?: return
                val uri = mediaItem?.localConfiguration?.uri ?: return

                val filePath = uri.path ?: return
                val file = File(filePath)
                val folderPath = file.parent ?: return

                // Persist last-played info so the File Explorer can offer a resume button
                MusicResumeManager.saveLastPlayed(this@MusicPlaybackService, folderPath, filePath)

                val intent = Intent(this@MusicPlaybackService, MediaViewerActivity::class.java).apply {
                    putExtra(MediaViewerActivity.EXTRA_FOLDER_PATH, folderPath)
                    putExtra(MediaViewerActivity.EXTRA_FILE_PATH, filePath)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val pendingIntent = PendingIntent.getActivity(
                    this@MusicPlaybackService,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                session.setSessionActivity(pendingIntent)
            }
        })
    }

    /**
     * Handles [ACTION_PLAY_LAST] sent by [com.gmail.omkarjoshi1989.receiver.MediaButtonEventReceiver].
     * This is called both on cold starts (service was dead) and when the service is running
     * but the MediaSession became inactive (e.g. after a Bluetooth disconnect → pause).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_LAST) {
            resumeOrPlayLast()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Full play/pause/resume state machine invoked from [onStartCommand] and
     * [MediaSessionCallback.onMediaButtonEvent].
     *
     * States handled:
     *  • Player currently playing          → pause  (toggle behaviour)
     *  • Media loaded, STATE_IDLE          → re-prepare and play  (e.g. after audio error)
     *  • Media loaded, STATE_ENDED         → seek to start and play
     *  • Media loaded, paused (STATE_READY)→ resume play  (e.g. after Bluetooth disconnect)
     *  • No media loaded                   → load from [MusicResumeManager] and play
     */
    private fun resumeOrPlayLast() {
        val player = mediaSession?.player ?: return
        when {
            player.isPlaying -> player.pause()
            player.mediaItemCount > 0 -> when (player.playbackState) {
                Player.STATE_IDLE    -> { player.prepare(); player.play() }
                Player.STATE_ENDED   -> { player.seekToDefaultPosition(); player.play() }
                else                 -> player.play()   // STATE_READY or STATE_BUFFERING
            }
            else -> playLastTrack()
        }
    }

    /**
     * Loads the entire audio folder of the last-played track into the player and starts
     * playback from that track's position in the folder — identical to how the media viewer
     * builds its playlist when the user opens an audio file from the File Explorer.
     *
     * Falls back to a single-item playlist if the folder can't be read.
     */
    private fun playLastTrack() {
        val player = mediaSession?.player ?: return
        val lastFilePath   = MusicResumeManager.getLastFilePath(this)   ?: return
        val lastFolderPath = MusicResumeManager.getLastFolderPath(this) ?: return

        val lastFile   = File(lastFilePath)
        val folder     = File(lastFolderPath)

        // Load every audio file in the folder (same sort order as the File Explorer)
        val folderAudioFiles = if (folder.exists())
            FileUtils.getAudioFilesInFolder(this, folder)
        else emptyList()

        val audioFiles = folderAudioFiles.ifEmpty {
            // Folder gone / unreadable — fall back to the single last-played file
            if (lastFile.exists()) listOf(lastFile) else return
        }

        // Build MediaItems — same metadata the media viewer uses
        val mediaItems = audioFiles.map { file ->
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

        // Start from the last-played track; fall back to index 0 if it's not in the list
        val startIndex = audioFiles.indexOfFirst { it.absolutePath == lastFile.absolutePath }
            .coerceAtLeast(0)

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.repeatMode = Player.REPEAT_MODE_ALL   // loop the whole folder
        player.prepare()
        player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Custom [MediaSession.Callback] that intercepts media button events arriving through
     * the active MediaSession (e.g. from AudioManager.dispatchMediaKeyEvent).
     *
     * Covers edge cases that Media3's default handler misses:
     *  • Player in STATE_IDLE (e.g. audio error after Bluetooth change) → re-prepare
     *  • Player in STATE_ENDED                                           → seek & play
     *  • No media loaded at all                                          → load last track
     *
     * All other cases (normal play/pause toggle) are delegated to the default handler.
     */
    private inner class MediaSessionCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent: KeyEvent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent == null || keyEvent.action != KeyEvent.ACTION_DOWN) {
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            val isPlayCode = keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                    keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK

            if (isPlayCode) {
                val player = session.player
                when {
                    player.mediaItemCount == 0 -> {
                        // Nothing loaded — pull from MusicResumeManager
                        playLastTrack()
                        return true
                    }
                    player.playbackState == Player.STATE_IDLE -> {
                        // Media present but player reset (e.g. after audio error)
                        player.prepare()
                        player.play()
                        return true
                    }
                    player.playbackState == Player.STATE_ENDED -> {
                        // Track finished — restart from beginning
                        player.seekToDefaultPosition()
                        player.play()
                        return true
                    }
                    // STATE_READY / STATE_BUFFERING: fall through so Media3's default
                    // handler does the normal play/pause toggle.
                }
            }

            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }
}
