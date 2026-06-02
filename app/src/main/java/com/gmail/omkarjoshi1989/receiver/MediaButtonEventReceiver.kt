package com.gmail.omkarjoshi1989.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.gmail.omkarjoshi1989.service.MusicPlaybackService
import com.gmail.omkarjoshi1989.util.MusicResumeManager

/**
 * Handles Android media button events (e.g. from Macrodroid's "Simulate Media Button")
 * when the MediaSession is inactive or absent.
 *
 * On Android 5+, AudioManager.dispatchMediaKeyEvent() routes directly to the active
 * MediaSession. This receiver is only invoked as a fallback — when there is no active
 * session (service killed, player paused after Bluetooth disconnect, etc.).
 *
 * Two conditions trigger a service start/command:
 *  • The service is already running but the session became inactive (e.g. paused after
 *    Bluetooth disconnect) → send ACTION_PLAY_LAST so the service resumes.
 *  • The service is not running but a last-played track is persisted → start the service
 *    and load that track.
 */
class MediaButtonEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent: KeyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return

        // Only act on ACTION_DOWN to avoid double-firing
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        // Only handle play/pause related keys
        val isPlayKey = keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK

        if (!isPlayKey) return

        val serviceRunning = isMusicServiceRunning(context)
        val hasLastTrack = MusicResumeManager.hasLastPlayed(context)

        // Nothing to do: service is dead and no track has ever been played in the app.
        if (!serviceRunning && !hasLastTrack) return

        // Send ACTION_PLAY_LAST to the service.
        //  • If service is running   → onStartCommand receives it and applies the smart
        //                              play/pause/resume state machine.
        //  • If service is not running → the service is cold-started and loads the last track.
        val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_LAST
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Swallow: background-start restrictions on some Android 14+ builds
        }
    }

    @Suppress("DEPRECATION")
    private fun isMusicServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == MusicPlaybackService::class.java.name
        }
    }
}
