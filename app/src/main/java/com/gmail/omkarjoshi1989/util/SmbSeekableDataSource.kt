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

@OptIn(UnstableApi::class)
class SmbSeekableDataSourceFactory(
	private val context: Context
) : DataSource.Factory {
	override fun createDataSource(): DataSource {
		return SmbSeekableDataSource(DefaultDataSource.Factory(context).createDataSource())
	}
}

@OptIn(UnstableApi::class)
private class SmbSeekableDataSource(
	private val fallback: DataSource
) : DataSource {

	private var openedWithSmb = false
	private var currentUri: Uri? = null
	private var reader: SmbClientManager.RandomAccessReader? = null
	private var readPosition: Long = 0L
	private var bytesRemaining: Long = 0L

	override fun addTransferListener(transferListener: TransferListener) {
		fallback.addTransferListener(transferListener)
	}

	override fun open(dataSpec: DataSpec): Long {
		closeActiveReader()
		currentUri = dataSpec.uri

		val streamEntry = SmbStreamRegistry.findByUri(dataSpec.uri)
		if (streamEntry == null) {
			openedWithSmb = false
			return fallback.open(dataSpec)
		}

		openedWithSmb = true
		val smbReader = SmbClientManager.openRandomAccessReader(
			config = streamEntry.connection,
			shareName = streamEntry.shareName,
			remotePath = streamEntry.remotePath
		)
		reader = smbReader

		val fileLength = smbReader.length
		if (dataSpec.position > fileLength) {
			throw IOException("Seek position ${dataSpec.position} is beyond SMB file length $fileLength")
		}

		readPosition = dataSpec.position
		bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
			fileLength - readPosition
		} else {
			minOf(dataSpec.length, fileLength - readPosition)
		}.coerceAtLeast(0L)

		return bytesRemaining
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (!openedWithSmb) {
			return fallback.read(buffer, offset, length)
		}

		val smbReader = reader ?: return C.RESULT_END_OF_INPUT
		if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

		val toRead = minOf(length.toLong(), bytesRemaining).toInt()
		val read = smbReader.readAt(readPosition, buffer, offset, toRead)
		if (read <= 0) return C.RESULT_END_OF_INPUT

		readPosition += read
		bytesRemaining -= read
		return read
	}

	override fun getUri(): Uri? {
		return if (openedWithSmb) currentUri else fallback.uri
	}

	override fun close() {
		if (openedWithSmb) {
			closeActiveReader()
			openedWithSmb = false
			currentUri = null
			return
		}
		fallback.close()
	}

	private fun closeActiveReader() {
		val active = reader ?: return
		reader = null
		runCatching { active.close() }
	}
}

