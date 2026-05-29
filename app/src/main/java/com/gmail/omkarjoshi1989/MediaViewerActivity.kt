package com.gmail.omkarjoshi1989

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gmail.omkarjoshi1989.ui.screens.MediaViewerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme
import com.gmail.omkarjoshi1989.util.FileUtils
import java.io.File

class MediaViewerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_FILE_PATH = "file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: run {
            finish(); return
        }
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish(); return
        }

        val folder = File(folderPath)
        val mediaFiles = FileUtils.getMediaFilesInFolder(folder)
        val initialIndex = mediaFiles.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)

        if (mediaFiles.isEmpty()) {
            finish(); return
        }

        setContent {
            FilesTheme {
                MediaViewerScreen(
                    mediaFiles = mediaFiles,
                    initialIndex = initialIndex,
                    onClose = { finish() }
                )
            }
        }
    }
}

