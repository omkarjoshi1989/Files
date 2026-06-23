package com.gmail.omkarjoshi1989.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gmail.omkarjoshi1989.FileExplorerActivity

/**
 * Manages system-tray notifications for file copy / move (cut-paste) operations.
 *
 * Progress notification  → ongoing = true  (user cannot dismiss while operation runs)
 * Completion notification → ongoing = false (user can swipe it away whenever they like)
 */
object FileOperationNotificationHelper {

    const val CHANNEL_ID   = "file_operations"
    const val NOTIFICATION_ID = 9001

    // ── Channel ───────────────────────────────────────────────────────────────

    /** Call once from Application.onCreate(). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Operations",
                NotificationManager.IMPORTANCE_LOW          // no sound / vibration
            ).apply {
                description = "Progress for file copy and move operations"
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ── Progress (ongoing — cannot be dismissed) ──────────────────────────────

    /**
     * Posts or updates a progress notification.
     *
     * @param title     e.g. "Copying 3 files"
     * @param text      e.g. "2 / 3 · photo.jpg"
     * @param current   files already processed
     * @param total     total number of files
     */
    fun showProgress(
        context: Context,
        title: String,
        text:  String,
        current: Int,
        total:   Int
    ) {
        val notification = baseBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total.coerceAtLeast(1), current, total == 0)
            .setOngoing(true)           // cannot be swiped away
            .setAutoCancel(false)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    // ── Completion (persistent but dismissible) ───────────────────────────────

    /**
     * Replaces the progress notification with a completion notice.
     * Stays in the panel until the user explicitly removes it.
     *
     * @param title  e.g. "Copy complete"
     * @param text   e.g. "3 items pasted to Downloads"
     */
    fun showCompletion(context: Context, title: String, text: String) {
        val notification = baseBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)   // removes the progress bar
            .setOngoing(false)          // user CAN swipe it away
            .setAutoCancel(true)        // also dismissed on tap
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    // ── Error (persistent but dismissible) ────────────────────────────────────

    fun showError(context: Context, title: String, text: String) {
        val notification = baseBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    // ── Cancel (used if the caller wants to remove it programmatically) ───────

    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, FileExplorerActivity::class.java)
                .putExtra(FileExplorerActivity.EXTRA_OPEN_BACKGROUND_OPERATIONS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
    }
}

