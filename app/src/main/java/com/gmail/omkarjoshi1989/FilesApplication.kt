package com.gmail.omkarjoshi1989

import android.app.Application
import com.gmail.omkarjoshi1989.util.FileOperationNotificationHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FilesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Register the notification channel for file copy / move progress
        FileOperationNotificationHelper.createChannel(this)
    }
}
