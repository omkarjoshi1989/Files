package com.gmail.omkarjoshi1989.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gmail.omkarjoshi1989.FileExplorerActivity
import com.gmail.omkarjoshi1989.util.BackgroundOperationsManager
import com.gmail.omkarjoshi1989.util.SmbClientManager
import com.gmail.omkarjoshi1989.util.SmbConnectionsManager
import com.gmail.omkarjoshi1989.viewmodel.ClipboardData
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SmbUploadService : Service() {

    companion object {
        const val ACTION_START_UPLOAD = "com.gmail.omkarjoshi1989.action.START_SMB_UPLOAD"

        const val EXTRA_CONNECTION_ID = "extra_connection_id"
        const val EXTRA_SHARE_NAME = "extra_share_name"
        const val EXTRA_REMOTE_DIRECTORY_PATH = "extra_remote_directory_path"
        const val EXTRA_LOCAL_PATHS = "extra_local_paths"
        const val EXTRA_CLIPBOARD_OPERATION = "extra_clipboard_operation"

        private const val CHANNEL_ID = "smb_uploads"
        private const val NOTIFICATION_ID = 9104
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_UPLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildProgressNotification("Preparing SMB upload...", "", 0, 0))

        val connectionId = intent.getStringExtra(EXTRA_CONNECTION_ID).orEmpty()
        val shareName = intent.getStringExtra(EXTRA_SHARE_NAME).orEmpty()
        val remoteDirectoryPath = intent.getStringExtra(EXTRA_REMOTE_DIRECTORY_PATH).orEmpty()
        val localPaths = intent.getStringArrayListExtra(EXTRA_LOCAL_PATHS).orEmpty()
        val opName = intent.getStringExtra(EXTRA_CLIPBOARD_OPERATION).orEmpty()
        val operation = runCatching { ClipboardOperation.valueOf(opName) }.getOrElse { ClipboardOperation.COPY }

        val total = localPaths.size
        val opVerb = if (operation == ClipboardOperation.CUT) "Moving" else "Uploading"
        val smbDestinationPath = "smb://$shareName/$remoteDirectoryPath"

        serviceScope.launch {
            val config = SmbConnectionsManager.getConnection(applicationContext, connectionId)
            if (config == null || shareName.isBlank() || total == 0) {
                // Create individual failed operation items for each file
                for (localPath in localPaths) {
                    val operationId = UUID.randomUUID().toString()
                    val file = File(localPath)
                    BackgroundOperationsManager.fail(
                        id = operationId,
                        title = "$opVerb to SMB failed",
                        detail = "Missing SMB upload metadata",
                        current = 0,
                        total = 1,
                        fileName = file.name,
                        sourcePath = file.parentFile?.absolutePath.orEmpty(),
                        destinationPath = smbDestinationPath
                    )
                }
                stopSelf()
                return@launch
            }

            val clipData = ClipboardData(
                files = localPaths.map { File(it) },
                operation = operation
            )

            // Create individual operation items and track them
            val operationIds = mutableMapOf<String, String>() // fileName -> operationId
            for (localPath in localPaths) {
                val file = File(localPath)
                val operationId = UUID.randomUUID().toString()
                operationIds[file.name] = operationId

                // Start individual file operation
                BackgroundOperationsManager.start(
                    id = operationId,
                    title = "$opVerb to SMB",
                    detail = "Starting...",
                    total = 100,
                    fileName = file.name,
                    sourcePath = file.parentFile?.absolutePath.orEmpty(),
                    destinationPath = smbDestinationPath
                )
            }

            runCatching {
                SmbClientManager.pasteLocalClipboardToRemote(
                    config = config,
                    shareName = shareName,
                    remoteDirectoryPath = remoteDirectoryPath,
                    clipboardData = clipData,
                    onProgress = { current, tot, fileName ->
                        notifyProgress("$opVerb $current / $tot", fileName, current, tot)
                    },
                    onEntryProgress = { fileName, percent ->
                        val operationId = operationIds[fileName] ?: return@pasteLocalClipboardToRemote
                        BackgroundOperationsManager.progress(
                            id = operationId,
                            title = "$opVerb to SMB",
                            detail = "In progress...",
                            current = percent,
                            total = 100,
                            fileName = fileName,
                            sourcePath = localPaths.firstOrNull { File(it).name == fileName }
                                ?.let { File(it).parentFile?.absolutePath }
                                .orEmpty(),
                            destinationPath = smbDestinationPath
                        )
                    }
                )
            }.onSuccess {
                // Mark all individual files as completed
                for ((fileName, operationId) in operationIds) {
                    BackgroundOperationsManager.complete(
                        id = operationId,
                        title = "SMB $opVerb complete",
                        detail = "Successfully $opVerb to SMB",
                        current = 100,
                        total = 100,
                        fileName = fileName,
                        sourcePath = localPaths.firstOrNull { File(it).name == fileName }
                            ?.let { File(it).parentFile?.absolutePath }
                            .orEmpty(),
                        destinationPath = smbDestinationPath
                    )
                }
            }.onFailure { err ->
                // Mark all individual files as failed
                for ((fileName, operationId) in operationIds) {
                    BackgroundOperationsManager.fail(
                        id = operationId,
                        title = "$opVerb to SMB failed",
                        detail = err.localizedMessage ?: "Unknown SMB upload error",
                        current = 0,
                        total = 100,
                        fileName = fileName,
                        sourcePath = localPaths.firstOrNull { File(it).name == fileName }
                            ?.let { File(it).parentFile?.absolutePath }
                            .orEmpty(),
                        destinationPath = smbDestinationPath
                    )
                }
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
                "SMB Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background SMB upload progress"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun notifyProgress(title: String, text: String, current: Int, total: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildProgressNotification(title, text, current, total))
    }

    private fun buildProgressNotification(
        title: String,
        text: String,
        current: Int,
        total: Int
    ): Notification {
        val pending = createTapPendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .setProgress(total.coerceAtLeast(1), current.coerceAtLeast(0), total <= 0)
            .build()
    }


    private fun createTapPendingIntent(): PendingIntent {
        val tapIntent = Intent(this, FileExplorerActivity::class.java)
            .putExtra(FileExplorerActivity.EXTRA_OPEN_BACKGROUND_OPERATIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

