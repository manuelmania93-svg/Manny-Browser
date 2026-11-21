package app.mannybrowser.data

import java.net.URI

/** One start-page tile: a manual shortcut or a favorited (bookmarked) site. */
data class StartTile(val name: String, val url: String, val bookmark: Boolean)

object StartTiles {
    /**
     * Shortcuts first (explicit user arrangement), then favorites, deduped by URL
     * so a site never appears twice.
     */
    fun merge(shortcuts: List<Shortcut>, bookmarks: List<Bookmark>): List<StartTile> {
        val seen = HashSet<String>()
        val out = ArrayList<StartTile>(shortcuts.size + bookmarks.size)
        shortcuts.forEach { if (seen.add(it.url)) out.add(StartTile(it.name, it.url, bookmark = false)) }
        bookmarks.forEach { if (seen.add(it.url)) out.add(StartTile(it.title, it.url, bookmark = true)) }
        return out
    }

    /** Stable favicon-file key for a page URL: its lowercase host, or null when the URL has none. */
    fun faviconKey(url: String): String? = try {
        URI(url).host?.lowercase()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }
}
