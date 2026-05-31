package com.gmail.omkarjoshi1989

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gmail.omkarjoshi1989.ui.screens.PdfViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import java.io.File

/**
 * A dedicated activity for viewing a single PDF file in-app.
 * No sibling-file swiping — only the tapped PDF is opened.
 */
class PdfViewerActivity : ComponentActivity() {

    companion object {
        /** Absolute path of the PDF file to open. */
        const val EXTRA_FILE_PATH = "file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) { finish(); return }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) { finish(); return }

        setContent {
            FilesTheme {
                PdfViewerScreen(
                    file = file,
                    onClose = { finish() }
                )
            }
        }
    }
}

