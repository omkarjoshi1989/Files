package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@UnstableApi
class SmbSeekableDataSourceFactory(
	private val context: Context
) : DataSource.Factory {
	override fun createDataSource(): DataSource {
		return SmbSeekableDataSource(DefaultDataSource.Factory(context).createDataSource())
	}
}

@UnstableApi
private class SmbSeekableDataSource(
	private val fallback: DataSource
) : DataSource {
	companion object {
		private const val READER_KEEP_ALIVE_MS = 8_000L
		private const val READ_AHEAD_BUFFER_SIZE = 512 * 1024
		private const val LARGE_DIRECT_READ_THRESHOLD = 128 * 1024
		private val readerCloseExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
			Thread(runnable, "SmbSeekableDataSourceCloser").apply { isDaemon = true }
		}
	}

	private var openedWithSmb = false
	private var currentUri: Uri? = null
	private var reader: SmbClientManager.RandomAccessReader? = null
	private var readPosition: Long = 0L
	private var bytesRemaining: Long = 0L
	private var readAheadStart: Long = -1L
	private var readAheadLength: Int = 0
	private val readAheadBuffer = ByteArray(READ_AHEAD_BUFFER_SIZE)
	private var pendingReaderClose: ScheduledFuture<*>? = null

	override fun addTransferListener(transferListener: TransferListener) {
		fallback.addTransferListener(transferListener)
	}

	@Synchronized
	override fun open(dataSpec: DataSpec): Long {
		val streamEntry = SmbStreamRegistry.findByUri(dataSpec.uri)
		if (streamEntry == null) {
			// Not an SMB stream — close any lingering SMB reader and use the fallback.
			closeReaderImmediately()
			openedWithSmb = false
			currentUri = dataSpec.uri
			return fallback.open(dataSpec)
		}

		openedWithSmb = true
		cancelPendingReaderClose()

		// Re-use the existing connection when seeking within the same file.
		// MKV (Matroska) requires multiple backward/forward seeks during initial
		// parsing (reading the Cues section near EOF, then jumping back to the
		// cluster data).  Creating a brand-new TCP+SMB connection on every seek
		// caused each seek to take 1-3 s, which either hit ExoPlayer's load
		// timeout or exhausted the server's connection limit — making MKV files
		// unplayable.  Since RandomAccessReader.readAt() already supports
		// arbitrary offsets, a seek is simply a readPosition update with no
		// reconnection needed.
		val smbReader: SmbClientManager.RandomAccessReader =
			if (reader != null && currentUri == dataSpec.uri) {
				// Same file, different position — reuse the open connection.
				reader!!
			} else {
				// New file (or first open) — close the old connection and open a fresh one.
				closeActiveReader()
				val newReader = SmbClientManager.openRandomAccessReader(
					config = streamEntry.connection,
					shareName = streamEntry.shareName,
					remotePath = streamEntry.remotePath
				)
				reader = newReader
				newReader
			}

		currentUri = dataSpec.uri

		val fileLength = smbReader.length
		if (fileLength >= 0L && dataSpec.position > fileLength) {
			throw IOException("Seek position ${dataSpec.position} is beyond SMB file length $fileLength")
		}

		readPosition = dataSpec.position
		bytesRemaining = when {
			dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length.coerceAtLeast(0L)
			fileLength >= 0L -> (fileLength - readPosition).coerceAtLeast(0L)
			else -> C.LENGTH_UNSET.toLong()
		}
		invalidateReadAhead()

		return bytesRemaining
	}

	@Synchronized
	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (!openedWithSmb) {
			return fallback.read(buffer, offset, length)
		}

		val smbReader = reader ?: return C.RESULT_END_OF_INPUT
		if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

		val toRead = when (bytesRemaining) {
			C.LENGTH_UNSET.toLong() -> length
			else -> minOf(length.toLong(), bytesRemaining).toInt()
		}
		if (toRead <= 0) return C.RESULT_END_OF_INPUT

		val read = if (toRead >= LARGE_DIRECT_READ_THRESHOLD) {
			// Large reads are streamed directly to the player buffer.
			smbReader.readAt(readPosition, buffer, offset, toRead)
		} else {
			readFromReadAhead(smbReader, buffer, offset, toRead)
		}
		if (read <= 0) return C.RESULT_END_OF_INPUT

		readPosition += read
		if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
			bytesRemaining -= read
		}
		return read
	}

	@Synchronized
	override fun getUri(): Uri? {
		return if (openedWithSmb) currentUri else fallback.uri
	}

	@Synchronized
	override fun close() {
		if (openedWithSmb) {
			// ExoPlayer may close/open repeatedly during seeks. Keep the SMB reader
			// warm briefly so seek-heavy containers (e.g. MKV) avoid reconnect churn.
			scheduleDeferredReaderClose()
			openedWithSmb = false
			return
		}
		fallback.close()
	}

	@Synchronized
	private fun scheduleDeferredReaderClose() {
		if (reader == null) return
		cancelPendingReaderClose()
		pendingReaderClose = readerCloseExecutor.schedule(
			{ closeReaderIfIdle() },
			READER_KEEP_ALIVE_MS,
			TimeUnit.MILLISECONDS
		)
	}

	@Synchronized
	private fun closeReaderIfIdle() {
		if (!openedWithSmb) {
			closeActiveReader()
			currentUri = null
		}
	}

	@Synchronized
	private fun cancelPendingReaderClose() {
		pendingReaderClose?.cancel(false)
		pendingReaderClose = null
	}

	@Synchronized
	private fun closeReaderImmediately() {
		cancelPendingReaderClose()
		closeActiveReader()
		invalidateReadAhead()
		currentUri = null
	}

	@Synchronized
	private fun closeActiveReader() {
		val active = reader ?: return
		reader = null
		runCatching { active.close() }
	}

	@Synchronized
	private fun readFromReadAhead(
		smbReader: SmbClientManager.RandomAccessReader,
		buffer: ByteArray,
		offset: Int,
		length: Int
	): Int {
		if (length <= 0) return 0

		val cacheHit = readAheadStart >= 0L &&
			readPosition >= readAheadStart &&
			readPosition < readAheadStart + readAheadLength

		if (!cacheHit) {
			val warmupRead = smbReader.readAt(readPosition, readAheadBuffer, 0, readAheadBuffer.size)
			if (warmupRead <= 0) return C.RESULT_END_OF_INPUT
			readAheadStart = readPosition
			readAheadLength = warmupRead
		}

		val available = (readAheadStart + readAheadLength - readPosition).toInt().coerceAtLeast(0)
		if (available <= 0) return C.RESULT_END_OF_INPUT

		val copyLength = minOf(length, available)
		val sourceOffset = (readPosition - readAheadStart).toInt()
		System.arraycopy(readAheadBuffer, sourceOffset, buffer, offset, copyLength)
		return copyLength
	}

	@Synchronized
	private fun invalidateReadAhead() {
		readAheadStart = -1L
		readAheadLength = 0
	}
}

