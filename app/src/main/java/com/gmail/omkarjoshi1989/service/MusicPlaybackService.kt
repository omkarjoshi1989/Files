package com.gmail.omkarjoshi1989.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gmail.omkarjoshi1989.MediaViewerActivity
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

        mediaSession = MediaSession.Builder(this, player).build()

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
}

