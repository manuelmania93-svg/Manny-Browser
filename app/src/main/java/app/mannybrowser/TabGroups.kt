package app.mannybrowser

import java.net.URI
import java.util.Locale

/** Same normalized HTTP(S) host, not a guessed registrable domain. */
object TabGroups {
    data class Entry(val id: Long, val url: String?)
    data class Group(val key: String, val site: String?, val tabIds: List<Long>)

    fun site(url: String?): String? = runCatching {
        val uri = URI(url ?: return null)
        if (uri.scheme?.lowercase(Locale.ROOT) !in listOf("http", "https")) return null
        uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')?.removePrefix("www.")?.ifBlank { null }
    }.getOrNull()

    /** Linked order follows the first tab. Blank/internal/invalid URLs stay separate. */
    fun group(entries: List<Entry>): List<Group> {
        val groups = linkedMapOf<String, Pair<String?, MutableList<Long>>>()
        entries.forEach { entry ->
            val host = site(entry.url)
            val key = host?.let { "site:$it" } ?: "tab:${entry.id}"
            groups.getOrPut(key) { host to mutableListOf() }.second.add(entry.id)
        }
        return groups.map { (key, value) -> Group(key, value.first, value.second.toList()) }
    }
}
