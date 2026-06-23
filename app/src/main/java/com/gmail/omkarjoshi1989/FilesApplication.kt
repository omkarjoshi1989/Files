package com.gmail.omkarjoshi1989

import android.app.Application
import com.gmail.omkarjoshi1989.util.BackgroundOperationsManager
import com.gmail.omkarjoshi1989.util.DirectoryCacheManager
import com.gmail.omkarjoshi1989.util.FileOperationNotificationHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FilesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Register the notification channel for file copy / move progress
        FileOperationNotificationHelper.createChannel(this)
        // Initialise the two-level directory listing cache (memory + disk).
        // Must be done before any ViewModel is created so that cold-start
        // navigation to heavy folders (e.g. DCIM/Camera) is instant.
        DirectoryCacheManager.init(applicationContext)
        BackgroundOperationsManager.init(applicationContext)
    }
}
