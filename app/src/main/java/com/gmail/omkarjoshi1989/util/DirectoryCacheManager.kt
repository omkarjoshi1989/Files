package com.gmail.omkarjoshi1989.util

import android.content.Context
import android.util.LruCache
import com.gmail.omkarjoshi1989.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-level singleton providing a two-level (memory + disk) cache for
 * directory listings using [FileItem] — which carries all display metadata
 * pre-fetched on the IO thread.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Level 1 – Memory  (LruCache, 60 entries)                                │
 * │    • Sub-microsecond reads; survives ViewModel recreation in the session. │
 * │    • Thread-safe (Android's LruCache is internally synchronized).        │
 * │    • Entries carry an [isStale] flag for stale-while-revalidate.         │
 * │                                                                           │
 * │  Level 2 – Disk  (tab-separated text in cacheDir/dirlst_v3/)             │
 * │    • Reads are vastly cheaper than listFiles() + 3× stat() per file:     │
 * │      sequential I/O vs random I/O, and zero kernel metadata look-ups.    │
 * │    • Survives app restarts → DCIM/Camera appears instantly on cold open.  │
 * │    • Writes dispatched asynchronously via a dedicated IO coroutine scope. │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ## Disk format (version 3)
 * ```
 * Line 0   : folderLastModified (Long, decimal)
 * Lines 1…N: TAB-separated fields per entry
 *            absolutePath \t isDirectory \t size \t lastModified \t extension \t isHidden
 * ```
 * Storing all metadata fields means disk reads produce ready-to-use [FileItem]s
 * with zero additional stat() calls.
 *
 * ## Freshness
 * Freshness is determined by comparing the directory's [File.lastModified] to
 * [CachedListing.folderLastModified].  A directory's lastModified changes only
 * when entries are **added or removed**, not when existing file contents are
 * written — so this is a reliable, cheap staleness indicator immune to camera
 * or media-scanner writes.
 */
object DirectoryCacheManager {

    private const val DISK_CACHE_DIR = "dirlst_v3"   // bump version when format changes

    // ── Data model ───────────────────────────────────────────────────────────

    data class CachedListing(
        val files: List<FileItem>,
        /** Directory [File.lastModified] at the time this listing was recorded. */
        val folderLastModified: Long,
        /**
         * Set to true by [invalidate] after an in-app file operation (paste/delete/rename).
         * The data is still good enough to display instantly while a background reload runs.
         */
        val isStale: Boolean = false
    )

    // ── Memory cache ─────────────────────────────────────────────────────────

    private val mem = LruCache<String, CachedListing>(60)

    // ── Disk write scope ─────────────────────────────────────────────────────

    private val diskScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var diskDir: File? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Call once from [android.app.Application.onCreate]. */
    fun init(context: Context) {
        diskDir = File(context.cacheDir, DISK_CACHE_DIR).also { it.mkdirs() }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun key(path: String, showHidden: Boolean): String = "$path|$showHidden"

    /** Memory-only lookup — instant, no disk I/O. */
    fun getMemory(key: String): CachedListing? = mem.get(key)

    /**
     * Disk lookup.  Must be called from an IO thread.
     * Promotes the entry to the memory cache on success.
     */
    fun getDisk(key: String): CachedListing? {
        val listing = readFromDisk(key) ?: return null
        mem.put(key, listing)
        return listing
    }

    /**
     * Stores [items] in memory immediately and schedules an async disk write.
     * Thread-safe; may be called from any thread.
     */
    fun put(key: String, items: List<FileItem>, folderLastModified: Long) {
        val listing = CachedListing(items, folderLastModified, isStale = false)
        mem.put(key, listing)
        diskScope.launch { writeToDisk(key, listing) }
    }

    /**
     * Marks the memory entry as [isStale] without evicting it, and removes
     * the disk entry.  The UI can still show stale data instantly while a
     * background reload runs.  Called after paste / delete / rename / create.
     */
    fun invalidate(path: String) {
        for (hidden in listOf(true, false)) {
            val k = key(path, hidden)
            mem.get(k)?.let { mem.put(k, it.copy(isStale = true)) }
            diskScope.launch { diskFile(k)?.delete() }
        }
    }

    /**
     * Hard-evicts [path] from both levels.
     * Use only when stale data must absolutely not be shown (e.g. user-initiated
     * pull-to-refresh where they specifically asked for fresh content).
     */
    fun hardEvict(path: String) {
        for (hidden in listOf(true, false)) {
            val k = key(path, hidden)
            mem.remove(k)
            diskScope.launch { diskFile(k)?.delete() }
        }
    }

    // ── Disk helpers ─────────────────────────────────────────────────────────

    private fun diskFile(key: String): File? {
        val dir = diskDir ?: return null
        val name = (key.hashCode().toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        return File(dir, "$name.lst")
    }

    /**
     * Format: line 0 = folderLastModified; lines 1..N = TAB-separated FileItem fields.
     * Fields: absolutePath \t isDirectory \t size \t lastModified \t extension \t isHidden
     */
    private fun writeToDisk(key: String, listing: CachedListing) {
        val file = diskFile(key) ?: return
        try {
            file.bufferedWriter().use { w ->
                w.write(listing.folderLastModified.toString())
                w.newLine()
                listing.files.forEach { item ->
                    w.write(item.absolutePath)
                    w.write('\t'.code)
                    w.write(if (item.isDirectory) "1" else "0")
                    w.write('\t'.code)
                    w.write(item.size.toString())
                    w.write('\t'.code)
                    w.write(item.lastModified.toString())
                    w.write('\t'.code)
                    w.write(item.extension)
                    w.write('\t'.code)
                    w.write(if (item.isHidden) "1" else "0")
                    w.newLine()
                }
            }
        } catch (_: Exception) {
            file.delete()   // Don't leave partial files
        }
    }

    private fun readFromDisk(key: String): CachedListing? {
        val file = diskFile(key) ?: return null
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            if (lines.isEmpty()) return null
            val folderLastModified = lines[0].toLongOrNull() ?: return null
            val items = lines.drop(1).mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 6) return@mapNotNull null
                val absPath  = parts[0]
                val isDir    = parts[1] == "1"
                val size     = parts[2].toLongOrNull() ?: return@mapNotNull null
                val lastMod  = parts[3].toLongOrNull() ?: return@mapNotNull null
                val ext      = parts[4]
                val isHidden = parts[5] == "1"
                val f = File(absPath)
                FileItem(
                    file         = f,
                    name         = f.name,
                    absolutePath = absPath,
                    isDirectory  = isDir,
                    size         = size,
                    lastModified = lastMod,
                    extension    = ext,
                    isHidden     = isHidden
                )
            }
            CachedListing(items, folderLastModified, isStale = false)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }
}
