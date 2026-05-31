package com.gmail.omkarjoshi1989.model

data class ZipEntryItem(
    val name: String,
    val fullPath: String, // Path within the ZIP, e.g., "folder/file.txt"
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0
)
