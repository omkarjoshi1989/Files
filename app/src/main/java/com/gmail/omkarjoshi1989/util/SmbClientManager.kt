package com.gmail.omkarjoshi1989.util
import com.gmail.omkarjoshi1989.model.SmbAuthMode
import com.gmail.omkarjoshi1989.model.SmbConnectionConfig
import com.gmail.omkarjoshi1989.model.SmbRemoteItem
import com.gmail.omkarjoshi1989.viewmodel.ClipboardData
import com.gmail.omkarjoshi1989.viewmodel.ClipboardOperation
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
object SmbClientManager {

    private const val PROGRESS_EMIT_INTERVAL_MS = 2_000L

    interface RandomAccessReader : Closeable {
        val length: Long
        fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
    }

    fun openRandomAccessReader(
        config: SmbConnectionConfig,
        shareName: String,
        remotePath: String
    ): RandomAccessReader {
        val smbConfig = SmbConfig.builder()
            .withDialects(
                SMB2Dialect.SMB_2_0_2,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_1_1
            )
            .build()
        val client = SMBClient(smbConfig)
        val connection = client.connect(config.host, config.port)
        val session = authenticate(connection, config)
        val share = session.connectShare(shareName)
        if (share !is DiskShare) {
            share.close()
            session.close()
            connection.close()
            client.close()
            throw IllegalStateException("Share '$shareName' is not a disk share")
        }

        val smbFile = share.openFile(
            normalizeRemotePath(remotePath),
            setOf(AccessMask.GENERIC_READ),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )

        val fileLength = runCatching { smbFile.fileInformation.standardInformation.endOfFile }
            .getOrElse { -1L }

        return object : RandomAccessReader {
            override val length: Long = fileLength

            override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (position < 0L || length <= 0) return -1
                if (this.length > 0L && position >= this.length) return -1
                val maxLen = if (this.length > 0L) {
                    minOf(length.toLong(), this.length - position).toInt().coerceAtLeast(0)
                } else {
                    length
                }
                if (maxLen == 0) return -1
                return smbFile.read(buffer, position, offset, maxLen)
            }

            override fun close() {
                runCatching { smbFile.close() }
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
                runCatching { client.close() }
            }
        }
    }

    /** Download a single remote file to [localDestFile]. Must be called on IO dispatcher. */
    suspend fun downloadFile(
        config: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        localDestFile: java.io.File
    ) = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) { share.close(); throw IllegalStateException("Not a disk share") }
            share.use {
                val normalized = normalizeRemotePath(remotePath)
                val remoteFile = share.openFile(
                    normalized,
                    setOf(AccessMask.GENERIC_READ),
                    setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    setOf(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                )
                remoteFile.use { smbFile ->
                    localDestFile.parentFile?.mkdirs()
                    smbFile.inputStream.use { input ->
                        localDestFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    /** Download one SMB entry (file or directory) to [localDest]. */
    suspend fun downloadEntryToLocal(
        config: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        isDirectory: Boolean,
        localDest: java.io.File,
        onProgressPercent: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) { share.close(); throw IllegalStateException("Not a disk share") }
            share.use {
                val normalized = normalizeRemotePath(remotePath)
                if (isDirectory) {
                    downloadDirectoryRecursive(share, normalized, localDest)
                } else {
                    downloadFileFromShare(share, normalized, localDest, onProgressPercent)
                }
            }
        }
    }

    /** Delete a file or directory (recursively) on the SMB share. */
    suspend fun deleteEntry(
        config: SmbConnectionConfig,
        shareName: String,
        remotePath: String,
        isDirectory: Boolean
    ) = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) { share.close(); throw IllegalStateException("Not a disk share") }
            share.use {
                val normalized = normalizeRemotePath(remotePath)
                if (isDirectory) share.rmdir(normalized, true) else share.rm(normalized)
            }
        }
    }

    /**
     * Rename (or move) an SMB entry to [newName] within the same directory.
     * Returns the new remote path.
     */
    suspend fun renameEntry(
        config: SmbConnectionConfig,
        shareName: String,
        oldPath: String,
        newName: String
    ): String = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) { share.close(); throw IllegalStateException("Not a disk share") }
            share.use {
                val normalizedOld = normalizeRemotePath(oldPath)
                val parentPath = normalizedOld.substringBeforeLast("\\", "")
                val newPath = if (parentPath.isBlank()) newName else "$parentPath\\$newName"
                // Open the entry (file or directory) and rename via the file handle
                val handle = share.openFile(
                    normalizedOld,
                    setOf(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                    null,
                    setOf(SMB2ShareAccess.FILE_SHARE_DELETE, SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                handle.use { it.rename(newPath, false) }
                newPath
            }
        }
    }

    /** Create a directory (and all intermediate parents) on the SMB share. */
    suspend fun createDirectory(
        config: SmbConnectionConfig,
        shareName: String,
        path: String
    ) = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) { share.close(); throw IllegalStateException("Not a disk share") }
            share.use { ensureDirectoryExists(share, normalizeRemotePath(path)) }
        }
    }

    /**
     * Uploads all files in [clipboardData] to [remoteDirectoryPath] on the given SMB share.
     *
     * [onProgress] is invoked after each top-level entry is fully uploaded, with:
     *   - [current]  — number of entries completed so far (1-based)
     *   - [total]    — total number of entries to upload
     *   - [fileName] — name of the entry that was just finished
     *
     * On a CUT operation the local source is deleted **only after** it has been
     * successfully uploaded, so a mid-batch failure leaves already-uploaded-and-deleted
     * entries on the remote but preserves the remaining local files.
     */
    suspend fun pasteLocalClipboardToRemote(
        config: SmbConnectionConfig,
        shareName: String,
        remoteDirectoryPath: String,
        clipboardData: ClipboardData,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> },
        onEntryProgress: (fileName: String, percent: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) {
                share.close()
                throw IllegalStateException("Share '$shareName' is not a disk share")
            }
            share.use {
                val remoteDir = normalizeRemotePath(remoteDirectoryPath)
                ensureDirectoryExists(share, remoteDir)
                val total = clipboardData.files.size
                clipboardData.files.forEachIndexed { index, localFile ->
                    val remoteTarget = joinRemotePath(remoteDir, localFile.name)
                    onEntryProgress(localFile.name, 0)
                    uploadLocalEntry(share, localFile, remoteTarget) { percent ->
                        onEntryProgress(localFile.name, percent)
                    }
                    onEntryProgress(localFile.name, 100)
                    // Delete source only after successful upload (CUT semantics)
                    if (clipboardData.operation == ClipboardOperation.CUT) {
                        if (localFile.isDirectory) localFile.deleteRecursively() else localFile.delete()
                    }
                    onProgress(index + 1, total, localFile.name)
                }
            }
        }
    }
    suspend fun listShares(config: SmbConnectionConfig): List<String> = withContext(Dispatchers.IO) {
        val preferredShare = config.defaultShareName.trim()
        withSession(config) { session ->
            val enumeratedShares = runCatching {
                enumerateShareNames(config)
                    .filter { shareName -> isBrowsableDiskShare(session, shareName) }
            }.getOrElse { emptyList() }
            val mergedShares = buildList {
                if (preferredShare.isNotBlank()) add(preferredShare)
                addAll(enumeratedShares)
            }.distinctBy { it.lowercase() }
            if (mergedShares.isNotEmpty()) {
                mergedShares
            } else if (preferredShare.isNotBlank() && isBrowsableDiskShare(session, preferredShare)) {
                listOf(preferredShare)
            } else {
                emptyList()
            }
        }
    }
    suspend fun listDirectory(
        config: SmbConnectionConfig,
        shareName: String,
        path: String
    ): List<SmbRemoteItem> = withContext(Dispatchers.IO) {
        withSession(config) { session ->
            val share = session.connectShare(shareName)
            if (share !is DiskShare) {
                share.close()
                return@withSession emptyList()
            }
            val normalizedPath = path.trim('/').replace('/', '\\')
            val queryPath = if (normalizedPath.isBlank()) "" else normalizedPath
            share.use {
                share.list(queryPath)
                    .asSequence()
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { info ->
                        SmbRemoteItem(
                            name = info.fileName,
                            path = if (queryPath.isBlank()) info.fileName else "$queryPath\\${info.fileName}",
                            isDirectory = isDirectory(info),
                            size = info.endOfFile,
                            lastModified = info.changeTime.toEpochMillis()
                        )
                    }
                    .sortedWith(compareBy<SmbRemoteItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .toList()
            }
        }
    }
    private inline fun <T> withSession(config: SmbConnectionConfig, block: (Session) -> T): T {
        val smbConfig = SmbConfig.builder()
            .withDialects(
                SMB2Dialect.SMB_2_0_2,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_1_1
            )
            .build()
        SMBClient(smbConfig).use { client ->
            client.connect(config.host, config.port).use { connection ->
                val session = authenticate(connection, config)
                session.use {
                    return block(session)
                }
            }
        }
    }
    private fun authenticate(connection: Connection, config: SmbConnectionConfig): Session {
        return when (config.authMode) {
            SmbAuthMode.GUEST -> {
                connection.authenticate(AuthenticationContext.guest())
            }
            SmbAuthMode.USERNAME_PASSWORD -> {
                connection.authenticate(
                    AuthenticationContext(
                        config.username,
                        config.password.toCharArray(),
                        config.domain
                    )
                )
            }
        }
    }
    private fun enumerateShareNames(config: SmbConnectionConfig): List<String> {
        val context = createJcifsContext(config)
        val root = SmbFile("smb://${config.host}:${config.port}/", context)
        return root.listFiles()
            .mapNotNull { it.name?.trim()?.trimEnd('/')?.trimEnd('\\') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
    private fun createJcifsContext(config: SmbConnectionConfig): CIFSContext {
        val baseContext = SingletonContext.getInstance()
        return when (config.authMode) {
            SmbAuthMode.GUEST -> {
                val guestAuth = NtlmPasswordAuthenticator(null, "", "")
                baseContext.withCredentials(guestAuth)
            }
            SmbAuthMode.USERNAME_PASSWORD -> {
                val domain = config.domain.ifBlank { null }
                val userAuth = NtlmPasswordAuthenticator(domain, config.username, config.password)
                baseContext.withCredentials(userAuth)
            }
        }
    }
    private fun isBrowsableDiskShare(session: Session, shareName: String): Boolean {
        return runCatching {
            val share = session.connectShare(shareName)
            if (share is DiskShare) {
                share.close()
                true
            } else {
                share.close()
                false
            }
        }.getOrDefault(false)
    }
    private fun isDirectory(info: FileIdBothDirectoryInformation): Boolean {
        return EnumWithValue.EnumUtils.isSet(
            info.fileAttributes,
            com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY
        )
    }
    private fun uploadLocalEntry(
        share: DiskShare,
        localFile: java.io.File,
        remotePath: String,
        onProgressPercent: (Int) -> Unit = {}
    ) {
        if (localFile.isDirectory) {
            if (share.fileExists(remotePath) || share.folderExists(remotePath)) {
                throw IllegalStateException("'${localFile.name}' already exists on SMB destination")
            }
            share.mkdir(remotePath)
            localFile.listFiles().orEmpty().forEach { child ->
                uploadLocalEntry(share, child, joinRemotePath(remotePath, child.name))
            }
            return
        }
        if (share.fileExists(remotePath) || share.folderExists(remotePath)) {
            throw IllegalStateException("'${localFile.name}' already exists on SMB destination")
        }
        val parent = remotePath.substringBeforeLast('\\', "")
        ensureDirectoryExists(share, parent)
        val remoteFile = openRemoteFileForWrite(share, remotePath)
        remoteFile.use { smbFile ->
            BufferedInputStream(FileInputStream(localFile)).use { input ->
                smbFile.outputStream.use { output ->
                    copyStreamWithPercent(
                        input = input,
                        output = output,
                        totalBytes = localFile.length().coerceAtLeast(0L),
                        onProgressPercent = onProgressPercent
                    )
                }
            }
        }
    }

    private fun downloadDirectoryRecursive(
        share: DiskShare,
        remoteDirectoryPath: String,
        localDestDir: java.io.File
    ) {
        localDestDir.mkdirs()
        val normalizedDir = normalizeRemotePath(remoteDirectoryPath)
        share.list(normalizedDir)
            .asSequence()
            .filter { it.fileName != "." && it.fileName != ".." }
            .forEach { info ->
                val childRemotePath = joinRemotePath(normalizedDir, info.fileName)
                val childLocal = java.io.File(localDestDir, info.fileName)
                if (isDirectory(info)) {
                    downloadDirectoryRecursive(share, childRemotePath, childLocal)
                } else {
                    downloadFileFromShare(share, childRemotePath, childLocal)
                }
            }
    }

    private fun downloadFileFromShare(
        share: DiskShare,
        remoteFilePath: String,
        localDestFile: java.io.File,
        onProgressPercent: (Int) -> Unit = {}
    ) {
        val remoteFile = share.openFile(
            normalizeRemotePath(remoteFilePath),
            setOf(AccessMask.GENERIC_READ),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        remoteFile.use { smbFile ->
            val totalBytes = runCatching { smbFile.fileInformation.standardInformation.endOfFile }
                .getOrElse { 0L }
            localDestFile.parentFile?.mkdirs()
            smbFile.inputStream.use { input ->
                localDestFile.outputStream().use { output ->
                    copyStreamWithPercent(
                        input = input,
                        output = output,
                        totalBytes = totalBytes,
                        onProgressPercent = onProgressPercent
                    )
                }
            }
        }
    }

    private fun copyStreamWithPercent(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgressPercent: (Int) -> Unit
    ) {
        if (totalBytes <= 0L) {
            input.copyTo(output)
            return
        }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesCopied = 0L
        var lastPercent = -1
        var lastEmitAt = 0L

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            bytesCopied += read

            val now = System.currentTimeMillis()
            val percent = ((bytesCopied * 100L) / totalBytes)
                .toInt()
                .coerceIn(0, 100)

            val shouldEmit = percent != lastPercent &&
                (now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS || percent == 100)
            if (shouldEmit) {
                onProgressPercent(percent)
                lastPercent = percent
                lastEmitAt = now
            }
        }

        output.flush()
        if (lastPercent < 100) onProgressPercent(100)
    }
    private fun openRemoteFileForWrite(share: DiskShare, remotePath: String): File {
        return share.openFile(
            remotePath,
            setOf(AccessMask.GENERIC_WRITE),
            setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_CREATE,
            setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
    }
    private fun ensureDirectoryExists(share: DiskShare, remoteDirectoryPath: String) {
        val normalized = normalizeRemotePath(remoteDirectoryPath)
        if (normalized.isBlank()) return
        var current = ""
        normalized.split('\\').filter { it.isNotBlank() }.forEach { part ->
            current = if (current.isBlank()) part else "$current\\$part"
            if (!share.folderExists(current)) {
                share.mkdir(current)
            }
        }
    }
    private fun normalizeRemotePath(path: String): String {
        return path.trim().trim('\\', '/').replace('/', '\\')
    }
    private fun joinRemotePath(parent: String, child: String): String {
        val normalizedParent = normalizeRemotePath(parent)
        val normalizedChild = child.trim().trim('\\', '/').replace('/', '\\')
        return if (normalizedParent.isBlank()) normalizedChild else "$normalizedParent\\$normalizedChild"
    }
}
