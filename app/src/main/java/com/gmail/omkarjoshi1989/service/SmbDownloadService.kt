package com.gmail.omkarjoshi1989.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.gmail.omkarjoshi1989.FileExplorerActivity
import com.gmail.omkarjoshi1989.util.BackgroundOperationsManager
import com.gmail.omkarjoshi1989.util.SmbClientManager
import com.gmail.omkarjoshi1989.util.SmbConnectionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SmbDownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.gmail.omkarjoshi1989.action.START_SMB_DOWNLOAD"

        const val EXTRA_CONNECTION_ID = "extra_connection_id"
        const val EXTRA_SHARE_NAME = "extra_share_name"
        const val EXTRA_REMOTE_PATHS = "extra_remote_paths"
        const val EXTRA_REMOTE_NAMES = "extra_remote_names"
        const val EXTRA_REMOTE_IS_DIR = "extra_remote_is_dir"

        private const val CHANNEL_ID = "smb_downloads"
        private const val NOTIFICATION_ID = 9102
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_DOWNLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val connectionId = intent.getStringExtra(EXTRA_CONNECTION_ID).orEmpty()
        val shareName = intent.getStringExtra(EXTRA_SHARE_NAME).orEmpty()
        val paths = intent.getStringArrayListExtra(EXTRA_REMOTE_PATHS).orEmpty()
        val names = intent.getStringArrayListExtra(EXTRA_REMOTE_NAMES).orEmpty()
        val isDirFlags = intent.getBooleanArrayExtra(EXTRA_REMOTE_IS_DIR) ?: BooleanArray(0)
        val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        startForeground(NOTIFICATION_ID, buildProgressNotification("Preparing SMB download...", 0, 0))

        serviceScope.launch {
            val config = SmbConnectionsManager.getConnection(applicationContext, connectionId)
            if (config == null || shareName.isBlank() || paths.isEmpty()) {
                // Create individual failed operation items for each file
                for (index in 0 until minOf(paths.size, names.size)) {
                    val operationId = UUID.randomUUID().toString()
                    BackgroundOperationsManager.fail(
                        id = operationId,
                        title = "SMB download failed",
                        detail = "Missing SMB download metadata",
                        current = 0,
                        total = 1,
                        fileName = names[index],
                        sourcePath = "smb://${config?.host ?: "unknown"}/$shareName/${paths[index]}",
                        destinationPath = downloadsPath
                    )
                }
                stopSelf()
                return@launch
            }

            val total = minOf(paths.size, names.size, isDirFlags.size)
            if (total <= 0) {
                stopSelf()
                return@launch
            }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Create individual operation items for each file
            for (index in 0 until total) {
                val remotePath = paths[index]
                val remoteName = names[index]
                val isDirectory = isDirFlags[index]
                val operationId = UUID.randomUUID().toString()

                // Start individual file operation
                BackgroundOperationsManager.start(
                    id = operationId,
                    title = "SMB download",
                    detail = "Starting...",
                    total = 100,
                    fileName = remoteName,
                    sourcePath = "smb://${config.host}/$shareName/$remotePath",
                    destinationPath = downloadDir.absolutePath
                )

                notifyProgress("Downloading ${index + 1} / $total", remoteName, index, total)

                runCatching {
                    SmbClientManager.downloadEntryToLocal(
                        config = config,
                        shareName = shareName,
                        remotePath = remotePath,
                        isDirectory = isDirectory,
                        localDest = File(downloadDir, remoteName),
                        onProgressPercent = { percent ->
                            BackgroundOperationsManager.progress(
                                id = operationId,
                                title = "SMB download",
                                detail = "In progress...",
                                current = percent,
                                total = 100,
                                fileName = remoteName,
                                sourcePath = "smb://${config.host}/$shareName/$remotePath",
                                destinationPath = downloadDir.absolutePath
                            )
                        }
                    )
                }.onSuccess {
                    // Mark individual file as completed
                    BackgroundOperationsManager.complete(
                        id = operationId,
                        title = "SMB download complete",
                        detail = "Downloaded to Downloads",
                        current = 100,
                        total = 100,
                        fileName = remoteName,
                        sourcePath = "smb://${config.host}/$shareName/$remotePath",
                        destinationPath = downloadDir.absolutePath
                    )
                }.onFailure { err ->
                    // Mark individual file as failed
                    BackgroundOperationsManager.fail(
                        id = operationId,
                        title = "SMB download failed",
                        detail = err.localizedMessage ?: "Unknown error",
                        current = 0,
                        total = 100,
                        fileName = remoteName,
                        sourcePath = "smb://${config.host}/$shareName/$remotePath",
                        destinationPath = downloadDir.absolutePath
                    )
                }

                notifyProgress("Downloading ${index + 1} / $total", remoteName, index + 1, total)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMB Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background SMB download progress"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun notifyProgress(title: String, text: String, current: Int, total: Int) {
        val notification = buildProgressNotification(title, current, total, text)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(
        title: String,
        current: Int,
        total: Int,
        text: String = ""
    ): Notification {
        val pendingIntent = createTapPendingIntent()

        val indeterminate = total <= 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setProgress(total.coerceAtLeast(1), current.coerceAtLeast(0), indeterminate)
            .build()
    }


    private fun createTapPendingIntent(): android.app.PendingIntent {
        val tapIntent = Intent(this, FileExplorerActivity::class.java)
            .putExtra(FileExplorerActivity.EXTRA_OPEN_BACKGROUND_OPERATIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return android.app.PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}


