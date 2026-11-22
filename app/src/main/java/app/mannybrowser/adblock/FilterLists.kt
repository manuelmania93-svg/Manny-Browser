package app.mannybrowser.adblock

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and caches ABP filter lists (EasyList, EasyPrivacy).
 *
 * Lists are fetched at runtime (never bundled), refreshed when older than
 * [STALE_MS], and compiled into [AdBlocker] on a background thread. Blocking
 * starts as soon as the first compile finishes; before that the engine
 * simply allows everything.
 *
 * Licenses: EasyList/EasyPrivacy are GPLv3 / CC BY-SA 4.0 - runtime
 * download with attribution (see About/readme) is the standard practice.
 */
class FilterLists(private val context: Context) {

    data class Source(val name: String, val url: String)

    private val dir: File by lazy { File(context.filesDir, "filters").apply { mkdirs() } }
    private val stampFile: File by lazy { File(dir, "updated-at") }

    fun cachedTexts(): List<String> =
        SOURCES.mapNotNull { src ->
            val f = File(dir, "${src.name}.txt")
            if (f.isFile && f.length() > 0) runCatching { f.readText() }.getOrNull() else null
        }

    fun hasCache(): Boolean = cachedTexts().size == SOURCES.size

    fun isStale(): Boolean {
        val stamp = runCatching { stampFile.readText().trim().toLong() }.getOrDefault(0L)
        return System.currentTimeMillis() - stamp > STALE_MS
    }

    /** Download every list; true when all succeeded. Caller must be off the main thread. */
    fun refreshNow(): Boolean {
        var allOk = true
        for (src in SOURCES) {
            val ok = runCatching { download(src.url, File(dir, "${src.name}.txt")); true }.getOrDefault(false)
            if (!ok) allOk = false
        }
        if (allOk) stampFile.writeText(System.currentTimeMillis().toString())
        return allOk
    }

    /**
     * Compile the engine from cache, then refresh in the background when stale.
     * [onReady] fires on the main scope after every successful (re)load.
     */
    fun loadAndRefresh(scope: CoroutineScope, engine: AdBlocker, onReady: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            val cached = cachedTexts()
            if (cached.isNotEmpty()) {
                engine.load(cached)
                launch(Dispatchers.Main) { onReady() }
            }
            if (!hasCache() || isStale()) {
                if (refreshNow()) {
                    engine.load(cachedTexts())
                    launch(Dispatchers.Main) { onReady() }
                }
            }
        }
    }

    private fun download(url: String, dest: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val tmp = File(dest.parentFile, "${dest.name}.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() < 10_000) throw java.io.IOException("list too small: ${tmp.length()}")
            tmp.renameTo(dest)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val STALE_MS = 7L * 24 * 60 * 60 * 1000
        val SOURCES = listOf(
            Source("easylist", "https://easylist.to/easylist/easylist.txt"),
            Source("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        )
    }
}
