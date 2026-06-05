package com.gmail.omkarjoshi1989

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FilesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
