package app.mannybrowser.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Shortcut(val name: String, val url: String)
data class Bookmark(val title: String, val url: String)
data class HistoryEntry(val title: String, val url: String, val time: Long)

class Stores(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("manny", Context.MODE_PRIVATE)

    private fun arr(key: String): JSONArray = try {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    } catch (e: Exception) { JSONArray() }

    private fun putArr(key: String, a: JSONArray) {
        prefs.edit().putString(key, a.toString()).apply()
    }

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("adblock", true)
        set(v) { prefs.edit().putBoolean("adblock", v).apply() }

    var popupBlockEnabled: Boolean
        get() = prefs.getBoolean("popupblock", true)
        set(v) { prefs.edit().putBoolean("popupblock", v).apply() }

    /** Hosts where the ad/popup blocker is paused (user's per-site off switch). */
    fun blockerPausedSites(): Set<String> {
        val a = arr("blocker_paused")
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet()
    }

    fun setBlockerPaused(host: String, paused: Boolean) {
        val sites = blockerPausedSites().toMutableSet()
        if (paused) sites.add(host) else sites.remove(host)
        val a = JSONArray()
        sites.forEach { a.put(it) }
        putArr("blocker_paused", a)
    }

    var darkModeOverride: Boolean?
        get() = if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null
        set(v) {
            if (v == null) prefs.edit().remove("dark").apply()
            else prefs.edit().putBoolean("dark", v).apply()
        }

    fun shortcuts(): List<Shortcut> {
        val a = arr("shortcuts")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Shortcut(o.optString("name"), o.optString("url"))
        }
    }

    fun addShortcut(s: Shortcut) {
        val a = arr("shortcuts")
        a.put(JSONObject().put("name", s.name).put("url", s.url))
        putArr("shortcuts", a)
    }

    fun updateShortcut(old: Shortcut, new: Shortcut) {
        val a = arr("shortcuts")
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("name") == old.name && o.optString("url") == old.url) {
                a.put(i, JSONObject().put("name", new.name).put("url", new.url))
                break
            }
        }
        putArr("shortcuts", a)
    }

    fun removeShortcut(s: Shortcut) {
        val a = arr("shortcuts")
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (!(o.optString("name") == s.name && o.optString("url") == s.url)) out.put(o)
        }
        putArr("shortcuts", out)
    }

    fun bookmarks(): List<Bookmark> {
        val a = arr("bookmarks")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Bookmark(o.optString("title"), o.optString("url"))
        }
    }

    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    fun addBookmark(title: String, url: String) {
        if (isBookmarked(url)) return
        val a = arr("bookmarks")
        a.put(JSONObject().put("title", title).put("url", url))
        putArr("bookmarks", a)
    }

    fun removeBookmark(url: String) {
        val a = arr("bookmarks")
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("url") != url) out.put(o)
        }
        putArr("bookmarks", out)
    }

    fun history(): List<HistoryEntry> {
        val a = arr("history")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryEntry(o.optString("title"), o.optString("url"), o.optLong("time"))
        }
    }

    fun addHistory(title: String, url: String) {
        val a = arr("history")
        val out = JSONArray()
        out.put(JSONObject().put("title", title).put("url", url).put("time", System.currentTimeMillis()))
        var count = 1
        for (i in 0 until a.length()) {
            if (count >= 500) break
            val o = a.getJSONObject(i)
            if (o.optString("url") != url) { out.put(o); count++ }
        }
        putArr("history", out)
    }

    fun clearHistory() = putArr("history", JSONArray())

    /** Persists the tab session (tabs, manual groups, active tab) as one JSON object. */
    fun saveSession(s: Session) {
        val tabs = JSONArray()
        s.tabs.forEach { t ->
            tabs.put(JSONObject().put("url", t.url ?: JSONObject.NULL).put("title", t.title))
        }
        val groups = JSONArray()
        s.groups.forEach { g ->
            val members = JSONArray()
            g.members.forEach { members.put(it) }
            groups.put(JSONObject().put("name", g.name).put("color", g.colorIndex).put("members", members))
        }
        val root = JSONObject().put("v", 1).put("current", s.current).put("tabs", tabs).put("groups", groups)
        prefs.edit().putString("session", root.toString()).apply()
    }

    /** The last saved session, or null when none exists or the payload is unreadable. */
    fun loadSession(): Session? = try {
        val raw = prefs.getString("session", null) ?: return null
        val root = JSONObject(raw)
        val tabsArr = root.optJSONArray("tabs") ?: return null
        val tabs = (0 until tabsArr.length()).map {
            val o = tabsArr.getJSONObject(it)
            SessionTab(if (o.isNull("url")) null else o.optString("url").ifBlank { null }, o.optString("title"))
        }
        val groupsArr = root.optJSONArray("groups") ?: JSONArray()
        val groups = (0 until groupsArr.length()).map {
            val o = groupsArr.getJSONObject(it)
            val m = o.optJSONArray("members") ?: JSONArray()
            SessionGroup(o.optString("name"), o.optInt("color"), (0 until m.length()).map { i -> m.optInt(i) })
        }
        Session(tabs, groups, root.optInt("current", 0)).normalized()
    } catch (e: Exception) { null }
}
