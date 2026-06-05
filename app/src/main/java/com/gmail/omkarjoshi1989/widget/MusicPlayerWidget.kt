package com.gmail.omkarjoshi1989.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.RemoteViews
import com.gmail.omkarjoshi1989.MusicPlayerActivity
import com.gmail.omkarjoshi1989.R
import com.gmail.omkarjoshi1989.service.MusicPlaybackService
import com.gmail.omkarjoshi1989.util.FileUtils
import com.gmail.omkarjoshi1989.util.MusicResumeManager
import java.io.File

/**
 * Home-screen widget that shows:
 *  • Album art (small, on the left)
 *  • Track file name (center-aligned)
 *  • Previous / Play-Pause / Next / Repeat controls
 *
 * Widget buttons send Intents to [MusicPlaybackService] which executes the
 * action and then broadcasts an update back to refresh the widget UI.
 */
class MusicPlayerWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.gmail.omkarjoshi1989.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS   = "com.gmail.omkarjoshi1989.widget.ACTION_PREVIOUS"
        const val ACTION_NEXT       = "com.gmail.omkarjoshi1989.widget.ACTION_NEXT"
        const val ACTION_REPEAT     = "com.gmail.omkarjoshi1989.widget.ACTION_REPEAT"

        /** Called by [MusicPlaybackService] after any state change to refresh all widget instances. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MusicPlayerWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, MusicPlayerWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    // ── AppWidgetProvider lifecycle ───────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Album-art loading is I/O — run off the main thread via goAsync()
        val pendingResult = goAsync()
        Thread {
            try {
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle button-click broadcast actions forwarded to the service
        val serviceAction: String? = when (intent.action) {
            ACTION_PLAY_PAUSE -> MusicPlaybackService.ACTION_WIDGET_PLAY_PAUSE
            ACTION_PREVIOUS   -> MusicPlaybackService.ACTION_WIDGET_PREVIOUS
            ACTION_NEXT       -> MusicPlaybackService.ACTION_WIDGET_NEXT
            ACTION_REPEAT     -> MusicPlaybackService.ACTION_WIDGET_REPEAT
            else              -> null
        }
        if (serviceAction != null) {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                action = serviceAction
            }
            try {
                context.startService(serviceIntent)
            } catch (_: Exception) {
                // Service may not be startable in the background on newer APIs;
                // silently ignore — the user will need to open the app first.
            }
        }
    }

    // ── Widget UI update ──────────────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        // ── Track name ──────────────────────────────────────────────────────
        val filePath = MusicResumeManager.getLastFilePath(context)
        val file     = filePath?.let { File(it) }?.takeIf { it.exists() }

        val trackName = if (file != null) {
            FileUtils.stripNumericPrefix(file.nameWithoutExtension)
        } else {
            context.getString(R.string.widget_no_track)
        }
        views.setTextViewText(R.id.widget_track_name, trackName)

        // ── Album art ───────────────────────────────────────────────────────
        val albumArt: Bitmap? = if (file != null) loadAlbumArt(file) else null
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.widget_album_art, albumArt)
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_widget_music_note)
        }

        // ── Play / Pause button icon ────────────────────────────────────────
        val isPlaying = MusicPlaybackService.isCurrentlyPlaying
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        // ── Repeat button icon + tint ───────────────────────────────────────
        val repeatIsOne = MusicPlaybackService.widgetRepeatModeOne
        views.setImageViewResource(
            R.id.widget_repeat,
            if (repeatIsOne) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat
        )
        // Highlight repeat button when repeat-one is active
        views.setInt(
            R.id.widget_repeat,
            "setColorFilter",
            if (repeatIsOne) 0xFFFF9800.toInt() else 0xB3FFFFFF.toInt()
        )

        // ── Click PendingIntents for each button ────────────────────────────
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            makeActionPendingIntent(context, ACTION_PLAY_PAUSE, widgetId, 0)
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            makeActionPendingIntent(context, ACTION_PREVIOUS, widgetId, 1)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            makeActionPendingIntent(context, ACTION_NEXT, widgetId, 2)
        )
        views.setOnClickPendingIntent(
            R.id.widget_repeat,
            makeActionPendingIntent(context, ACTION_REPEAT, widgetId, 3)
        )

        // ── Tap album art / track name → open MusicPlayerActivity ──────────
        val openIntent = Intent(context, MusicPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            filePath?.let {
                putExtra(MusicPlayerActivity.EXTRA_FILE_PATH, it)
                putExtra(MusicPlayerActivity.EXTRA_NO_AUTOPLAY, true)
                File(it).parentFile?.absolutePath?.let { folder ->
                    putExtra(MusicPlayerActivity.EXTRA_FOLDER_PATH, folder)
                }
            }
        }
        val openPending = PendingIntent.getActivity(
            context,
            widgetId * 10 + 9,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_album_art, openPending)
        views.setOnClickPendingIntent(R.id.widget_track_name, openPending)

        manager.updateAppWidget(widgetId, views)
    }

    /** Creates a broadcast PendingIntent that fires the given widget action. */
    private fun makeActionPendingIntent(
        context: Context,
        action: String,
        widgetId: Int,
        actionIndex: Int
    ): PendingIntent {
        val intent = Intent(context, MusicPlayerWidget::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 10 + actionIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Reads embedded album art from an audio file's metadata. Returns null if none. */
    private fun loadAlbumArt(file: File): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val art = mmr.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(art, 0, art.size)
        } catch (_: Exception) {
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }
}

