package com.gmail.omkarjoshi1989

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.gmail.omkarjoshi1989.ui.screens.PdfViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.RotationManager
import com.gmail.omkarjoshi1989.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A dedicated activity for viewing a single PDF file in-app.
 *
 * Can be launched:
 *  - Internally via [EXTRA_FILE_PATH] extra (from within the app).
 *  - Externally via ACTION_VIEW with a content:// or file:// URI
 *    (e.g. from Gmail, WhatsApp, Downloads, or any file manager).
 */
class PdfViewerActivity : ComponentActivity() {

    companion object {
        /** Absolute path of the PDF file to open (internal launch). */
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var rotationManager: RotationManager

    private var pendingExportStreamProvider: (() -> ParcelFileDescriptor?)? = null

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { destinationUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                val sourcePfd = pendingExportStreamProvider?.invoke()
                if (sourcePfd == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfViewerActivity, "Export failed: source file not accessible", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                try {
                    contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        ParcelFileDescriptor.AutoCloseInputStream(sourcePfd).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfViewerActivity, "Exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfViewerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingExportStreamProvider = null
                }
            }
        }
    }

    private fun exportPdf(displayName: String, pfdProvider: () -> ParcelFileDescriptor?) {
        pendingExportStreamProvider = pfdProvider
        createDocumentLauncher.launch(displayName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply global setting that controls forced rotation in media viewer screens.
        rotationManager = RotationManager(this)
        if (SettingsManager.isForceMediaRotationEnabled(this)) {
            rotationManager.enableAutoRotation()
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // ── 1. Internal launch via explicit file path ───────────────────
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) { finish(); return }
            setContent {
                FilesTheme {
                    PdfViewerScreen(
                        file     = file,
                        onClose  = { finish() },
                        onExport = { exportPdf(file.name) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } }
                    )
                }
            }
            return
        }

        // ── 2. External launch via ACTION_VIEW intent ───────────────────
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri = intent.data ?: run { finish(); return }

            var displayName: String
            var pdfKey: String
            var openPfd: () -> ParcelFileDescriptor?

            when (uri.scheme?.lowercase()) {
                "file" -> {
                    val file = File(uri.path ?: run { finish(); return })
                    displayName = file.name
                    pdfKey      = file.absolutePath
                    openPfd     = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
                }
                "content" -> {
                    displayName = resolveDisplayName(uri)
                    pdfKey      = uri.toString()
                    openPfd     = { contentResolver.openFileDescriptor(uri, "r") }
                }
                else -> { finish(); return }
            }

            setContent {
                FilesTheme {
                    PdfViewerScreen(
                        displayName = displayName,
                        pdfKey      = pdfKey,
                        openPfd     = openPfd,
                        onClose     = { finish() },
                        onExport    = { exportPdf(displayName, openPfd) }
                    )
                }
            }
            return
        }

        // Nothing matched — close
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationManager.release()
    }

    /** Queries the content resolver for a human-readable file name. */
    private fun resolveDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                } ?: uri.lastPathSegment ?: "document.pdf"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "document.pdf"
        }
    }
}

