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
}
