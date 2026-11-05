package app.mannybrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.mannybrowser.data.Bookmark
import app.mannybrowser.data.HistoryEntry
import app.mannybrowser.data.Shortcut
import app.mannybrowser.data.Stores

enum class Overlay { NONE, TABS, BOOKMARKS, HISTORY, DOWNLOADS }

class Tab(val id: Long, val webView: WebView) {
    var url by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
}

@SuppressLint("SetJavaScriptEnabled")
class BrowserState(private val context: Context) {
    val stores = Stores(context)
    var darkModeOverride by mutableStateOf(stores.darkModeOverride)
    val tabs = mutableStateListOf<Tab>()
    var currentTabIndex by mutableIntStateOf(-1)
    var overlay by mutableStateOf(Overlay.NONE)
    var fullscreen by mutableStateOf(false)
    var findBarOpen by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    var shortcuts by mutableStateOf(stores.shortcuts())
    var bookmarks by mutableStateOf(stores.bookmarks())
    var history by mutableStateOf(stores.history())
    private var nextId = 1L

    val currentTab: Tab? get() = tabs.getOrNull(currentTabIndex)

    fun isDark(): Boolean {
        val o = darkModeOverride
        return o ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }

    fun applyDark(wv: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, isDark())
        }
    }

    fun toggleDark() {
        darkModeOverride = !isDark()
        stores.darkModeOverride = darkModeOverride
        tabs.forEach { applyDark(it.webView) }
    }

    fun newTab(url: String? = null): Tab {
        val wv = WebView(context)
        val tab = Tab(nextId++, wv)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        applyDark(wv)
        wv.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, visitedUrl: String?, isReload: Boolean) {
                visitedUrl ?: return
                if (visitedUrl == "about:blank") return
                tab.url = visitedUrl
                view.title?.let { if (it.isNotBlank()) tab.title = it }
                stores.addHistory(tab.title.ifBlank { visitedUrl }, visitedUrl)
                history = stores.history()
            }
            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                view.title?.let { if (it.isNotBlank()) tab.title = it }
            }
        }
        wv.setDownloadListener { dlUrl, _, contentDisposition, mimeType, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(dlUrl))
                val name = URLUtil.guessFileName(dlUrl, contentDisposition, mimeType)
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            } catch (_: Exception) { }
        }
        tabs.add(tab)
        currentTabIndex = tabs.size - 1
        if (url != null) navigate(url)
        return tab
    }

    fun closeTab(index: Int) {
        val t = tabs.getOrNull(index) ?: return
        t.webView.destroy()
        tabs.removeAt(index)
        if (tabs.isEmpty()) { newTab() }
        else if (index < currentTabIndex) currentTabIndex--
        else if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
    }

    fun closeAllTabs() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
        newTab()
    }

    fun navigate(input: String) {
        val t = currentTab ?: return
        val target = toUrl(input)
        t.url = target
        t.webView.loadUrl(target)
    }

    fun goHome() {
        val t = currentTab ?: return
        t.webView.stopLoading()
        t.url = null
        t.title = ""
    }

    fun openInNewTab(url: String) {
        overlay = Overlay.NONE
        newTab(url)
    }

    fun toggleBookmark() {
        val t = currentTab ?: return
        val u = t.url ?: return
        if (stores.isBookmarked(u)) stores.removeBookmark(u)
        else stores.addBookmark(t.title.ifBlank { u }, u)
        bookmarks = stores.bookmarks()
    }

    fun removeBookmark(url: String) {
        stores.removeBookmark(url)
        bookmarks = stores.bookmarks()
    }

    fun clearHistory() {
        stores.clearHistory()
        history = emptyList()
    }

    fun addShortcut(name: String, url: String) {
        stores.addShortcut(Shortcut(name, toUrl(url)))
        shortcuts = stores.shortcuts()
    }

    fun updateShortcut(old: Shortcut, name: String, url: String) {
        stores.updateShortcut(old, Shortcut(name, toUrl(url)))
        shortcuts = stores.shortcuts()
    }

    fun removeShortcut(s: Shortcut) {
        stores.removeShortcut(s)
        shortcuts = stores.shortcuts()
    }

    fun shareCurrent() {
        val u = currentTab?.url ?: return
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, u)
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun destroyAll() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
    }

    companion object {
        fun toUrl(input: String): String {
            val s = input.trim()
            return when {
                s.startsWith("http://") || s.startsWith("https://") -> s
                Regex("^[\\w.-]+\\.[a-zA-Z]{2,}(/.*)?$").matches(s) && !s.contains(" ") -> "https://$s"
                else -> "https://www.google.com/search?q=" + Uri.encode(s)
            }
        }
    }
}
