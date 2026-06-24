package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

object SmbMediaThumbRepository {

    private const val CACHE_DIR = "smb_thumbs"
    private const val MAX_MEMORY_CACHE_KB = 12 * 1024
    private const val EXTRACTION_TIMEOUT_MS = 4_500L
    private const val MAX_IMAGE_PREVIEW_BYTES = 4L * 1024L * 1024L

    private val memoryCache = object : LruCache<String, ByteArray>(MAX_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return (value.size / 1024).coerceAtLeast(1)
        }
    }

    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractionSemaphore = Semaphore(permits = 3)
    private val jobsMutex = Mutex()
    private val inFlightJobs = mutableMapOf<String, kotlinx.coroutines.Deferred<ByteArray?>>()

    suspend fun getVideoThumbnail(
        context: Context,
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        fileSize: Long,
        lastModified: Long
    ): ByteArray? {
        val key = buildKey(
            kind = "video",
            connection = connection,
            shareName = shareName,
            remotePath = remotePath,
            fileSize = fileSize,
            lastModified = lastModified
        )
        return loadOrExtract(context, key) {
            extractVideoFrame(connection, shareName, remotePath)
        }
    }

    suspend fun getAudioAlbumArt(
        context: Context,
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        fileSize: Long,
        lastModified: Long
    ): ByteArray? {
        val key = buildKey(
            kind = "audio",
            connection = connection,
            shareName = shareName,
            remotePath = remotePath,
            fileSize = fileSize,
            lastModified = lastModified
        )
        return loadOrExtract(context, key) {
            extractEmbeddedAlbumArt(connection, shareName, remotePath)
        }
    }

    suspend fun getImageThumbnail(
        context: Context,
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        fileSize: Long,
        lastModified: Long
    ): ByteArray? {
        val key = buildKey(
            kind = "image",
            connection = connection,
            shareName = shareName,
            remotePath = remotePath,
            fileSize = fileSize,
            lastModified = lastModified
        )
        return loadOrExtract(context, key) {
            extractImageBytesCapped(connection, shareName, remotePath)
        }
    }

    private suspend fun loadOrExtract(
        context: Context,
        key: String,
        extractor: suspend () -> ByteArray?
    ): ByteArray? {
        memoryCache.get(key)?.let { return it }

        val diskFile = diskCacheFile(context, key)
        if (diskFile.exists()) {
            val cached = runCatching { diskFile.readBytes() }.getOrNull()
            if (cached != null && cached.isNotEmpty()) {
                memoryCache.put(key, cached)
                return cached
            }
        }

        val deferred = jobsMutex.withLock {
            inFlightJobs[key] ?: extractionScope.async {
                extractionSemaphore.withPermit {
                    val bytes = withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) { extractor() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val nonNullBytes = bytes
                        memoryCache.put(key, nonNullBytes)
                        runCatching {
                            diskFile.parentFile?.mkdirs()
                            diskFile.writeBytes(nonNullBytes)
                        }
                    }
                    bytes
                }
            }.also { inFlightJobs[key] = it }
        }

        return try {
            deferred.await()
        } finally {
            jobsMutex.withLock {
                if (inFlightJobs[key] === deferred) {
                    inFlightJobs.remove(key)
                }
            }
        }
    }

    private fun extractVideoFrame(
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String
    ): ByteArray? {
        val reader = SmbClientManager.openRandomAccessReader(
            config = connection,
            shareName = shareName,
            remotePath = remotePath
        )
        val dataSource = SmbReaderMediaDataSource(reader)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            frameToJpeg(frame)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { dataSource.close() }
        }
    }

    private fun extractEmbeddedAlbumArt(
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String
    ): ByteArray? {
        val reader = SmbClientManager.openRandomAccessReader(
            config = connection,
            shareName = shareName,
            remotePath = remotePath
        )
        val dataSource = SmbReaderMediaDataSource(reader)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            retriever.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { dataSource.close() }
        }
    }

    private fun extractImageBytesCapped(
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String
    ): ByteArray? {
        val reader = SmbClientManager.openRandomAccessReader(
            config = connection,
            shareName = shareName,
            remotePath = remotePath
        )
        return try {
            val length = reader.length
            if (length <= 0L || length > MAX_IMAGE_PREVIEW_BYTES) return null
            val expectedSize = length.toInt()
            val output = ByteArrayOutputStream(expectedSize)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var offset = 0L
            while (offset < length) {
                val toRead = minOf(buffer.size.toLong(), length - offset).toInt()
                val read = reader.readAt(offset, buffer, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                offset += read
            }
            if (offset == length) output.toByteArray() else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun frameToJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    return null
                }
                output.toByteArray()
            }
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun buildKey(
        kind: String,
        connection: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        fileSize: Long,
        lastModified: Long
    ): String {
        return "smb:$kind:${connection.id}:$shareName:$remotePath:$fileSize:$lastModified"
    }

    private fun diskCacheFile(context: Context, key: String): File {
        val digest = sha256(key)
        return File(File(context.cacheDir, CACHE_DIR), "$digest.bin")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            hexChars[i * 2] = HEX[v ushr 4]
            hexChars[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(hexChars)
    }

    private val HEX = "0123456789abcdef".toCharArray()

    private class SmbReaderMediaDataSource(
        private val reader: SmbClientManager.RandomAccessReader
    ) : MediaDataSource() {

        override fun getSize(): Long = reader.length

        override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
            if (buffer == null || size <= 0) return -1
            return runCatching {
                reader.readAt(position, buffer, offset, size)
            }.getOrElse { -1 }
        }

        override fun close() {
            runCatching { reader.close() }
        }
    }
}

