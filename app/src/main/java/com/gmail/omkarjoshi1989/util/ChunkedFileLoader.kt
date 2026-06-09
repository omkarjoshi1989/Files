package com.gmail.omkarjoshi1989.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Configuration for chunked file loading behavior.
 */
data class ChunkConfig(
    /** Number of files to load per chunk */
    val chunkSize: Int = 150,
    /** Size of the initial batch to load immediately */
    val initialBatchSize: Int = 200,
    /** Maximum number of chunks to load concurrently */
    val maxConcurrentChunks: Int = 2,
    /** Delay between chunk loads to prevent UI blocking (ms) */
    val chunkDelayMs: Long = 50
)

/**
 * Represents a chunk of files being loaded.
 */
data class FileChunk(
    val files: List<File>,
    val chunkIndex: Int,
    val totalChunks: Int,
    val isInitial: Boolean = false,
    val isComplete: Boolean = false
)

/**
 * Utility class that handles progressive file loading in chunks.
 * Designed to prevent blocking UI when loading folders with thousands of files.
 *
 * Features:
 * - Loads files in configurable chunks
 * - Emits initial batch immediately for instant UI response
 * - Loads remaining chunks progressively in background
 * - Supports cancellation when user navigates away
 * - Prioritizes visible/important files
 */
class ChunkedFileLoader(
    private val config: ChunkConfig = ChunkConfig()
) {
    private var currentJob: Job? = null

    /**
     * Loads files from a directory in chunks, emitting them progressively.
     *
     * @param directory The directory to load files from
     * @param showHidden Whether to include hidden files
     * @return Flow that emits FileChunk objects as they're loaded
     */
    fun loadFilesChunked(
        directory: File,
        showHidden: Boolean
    ): Flow<FileChunk> = flow {
        // Cancel any previous loading operation
        cancelCurrentLoad()

        // Read all files from directory (I/O operation)
        val allFiles = withContext(Dispatchers.IO) {
            val files = directory.listFiles() ?: emptyArray()
            files.filter { showHidden || !it.isHidden }.toList()
        }

        if (allFiles.isEmpty()) {
            emit(FileChunk(emptyList(), 0, 0, isInitial = true, isComplete = true))
            return@flow
        }

        // Separate folders and files for prioritization
        val (folders, files) = allFiles.partition { it.isDirectory }
        
        // Folders first, then files - better UX as folders are instant to navigate
        val sortedFiles = folders + files
        val totalFiles = sortedFiles.size

        // Calculate chunks
        val initialBatch = sortedFiles.take(config.initialBatchSize)
        val remainingFiles = sortedFiles.drop(config.initialBatchSize)
        val totalChunks = if (remainingFiles.isEmpty()) 1 
                         else 1 + (remainingFiles.size + config.chunkSize - 1) / config.chunkSize

        // Emit initial batch immediately (no delay for first paint)
        emit(FileChunk(
            files = initialBatch,
            chunkIndex = 0,
            totalChunks = totalChunks,
            isInitial = true,
            isComplete = remainingFiles.isEmpty()
        ))

        // Load remaining files in chunks
        if (remainingFiles.isNotEmpty()) {
            val chunks = remainingFiles.chunked(config.chunkSize)
            
            chunks.forEachIndexed { index, chunk ->
                // Check if coroutine has been cancelled
                try {
                    delay(0) // Yields to check cancellation
                } catch (e: CancellationException) {
                    throw e
                }

                // Small delay to prevent overwhelming the UI thread
                if (index > 0) delay(config.chunkDelayMs)

                val chunkNumber = index + 1 // +1 because initial batch is chunk 0
                val isLast = chunkNumber == totalChunks - 1

                emit(FileChunk(
                    files = chunk,
                    chunkIndex = chunkNumber,
                    totalChunks = totalChunks,
                    isInitial = false,
                    isComplete = isLast
                ))
            }
        }
    }

    /**
     * Loads files in chunks and accumulates them into a list.
     * Useful when you need to collect all chunks into a single list while
     * still benefiting from progressive loading.
     *
     * @param directory The directory to load files from
     * @param showHidden Whether to include hidden files
     * @param onChunkLoaded Callback invoked for each chunk loaded
     * @return Complete list of all files
     */
    suspend fun loadAllFilesProgressive(
        directory: File,
        showHidden: Boolean,
        onChunkLoaded: ((FileChunk) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.Default) {
        val allFiles = mutableListOf<File>()
        
        loadFilesChunked(directory, showHidden).collect { chunk ->
            allFiles.addAll(chunk.files)
            onChunkLoaded?.invoke(chunk)
        }
        
        allFiles
    }

    /**
     * Cancels the current loading operation if one is in progress.
     */
    suspend fun cancelCurrentLoad() {
        currentJob?.cancelAndJoin()
        currentJob = null
    }

    /**
     * Priority-based file loading for when you need to load specific files first.
     * Useful for loading thumbnails of visible items before off-screen items.
     *
     * @param files List of files to load
     * @param priorityPaths Set of file paths that should be loaded first
     * @return Reordered list with priority files first
     */
    fun prioritizeFiles(files: List<File>, priorityPaths: Set<String>): List<File> {
        val (priority, normal) = files.partition { it.absolutePath in priorityPaths }
        return priority + normal
    }

    companion object {
        /**
         * Default configuration optimized for typical Android devices
         */
        val DEFAULT_CONFIG = ChunkConfig()

        /**
         * Configuration optimized for low-end devices with limited memory
         */
        val LOW_END_CONFIG = ChunkConfig(
            chunkSize = 75,
            initialBatchSize = 100,
            maxConcurrentChunks = 1,
            chunkDelayMs = 100
        )

        /**
         * Configuration optimized for high-end devices
         */
        val HIGH_END_CONFIG = ChunkConfig(
            chunkSize = 250,
            initialBatchSize = 300,
            maxConcurrentChunks = 3,
            chunkDelayMs = 25
        )
    }
}
