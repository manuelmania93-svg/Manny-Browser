package app.mannybrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Persists page icons as 96px PNGs keyed by site host, so start-page tiles
 * (favorites and shortcuts) can show the site symbol instead of a letter.
 */
class FaviconStore(context: Context) {
    private val dir = File(context.filesDir, "favicons")
    private val memory = LruCache<String, Bitmap>(64)

    private fun fileFor(url: String): File? = StartTiles.faviconKey(url)?.let { File(dir, "$it.png") }

    @Synchronized
    fun save(url: String, icon: Bitmap) {
        val key = StartTiles.faviconKey(url) ?: return
        val file = fileFor(url) ?: return
        try {
            val scaled = Bitmap.createScaledBitmap(icon, 96, 96, true)
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            memory.put(key, scaled)
        } catch (_: Exception) { /* tile keeps the letter fallback */ }
    }

    fun load(url: String): Bitmap? {
        val key = StartTiles.faviconKey(url) ?: return null
        memory.get(key)?.let { return it }
        val file = fileFor(url) ?: return null
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.also { memory.put(key, it) }
        } catch (_: Exception) { null }
    }
}
