package app.mannybrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.CookieManager
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import app.mannybrowser.adblock.AdBlocker
import app.mannybrowser.adblock.FilterLists
import app.mannybrowser.update.AppUpdate
import app.mannybrowser.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class Overlay { NONE, TABS, BOOKMARKS, HISTORY, DOWNLOADS, UPDATE }

class Tab(val id: Long, val webView: WebView) {
    var url by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    /** Restored tab not yet loaded; the URL loads on first activation so a big session does not spawn N parallel page loads. */
    var pendingUrl: String? = null
    var preview by mutableStateOf<Bitmap?>(null)
    var favicon by mutableStateOf<Bitmap?>(null)
}

/** Manually created, named and colored tab group. Lives as long as its tabs (in-memory, like tabs). */
class TabGroup(val id: Long, name: String, colorIndex: Int, members: List<Long>) {
    var name by mutableStateOf(name)
    var colorIndex by mutableIntStateOf(colorIndex)
    val tabIds = mutableStateListOf<Long>().apply { addAll(members) }
}

@SuppressLint("SetJavaScriptEnabled")
class BrowserState(private val context: Context) {
    val stores = Stores(context)
    val favicons = app.mannybrowser.data.FaviconStore(context)
    /** Bumped whenever a favicon is saved so start-page tiles reload their icon. */
    var faviconTick by mutableIntStateOf(0)
    var darkModeOverride by mutableStateOf(stores.darkModeOverride)
    val tabs = mutableStateListOf<Tab>()
    var currentTabIndex by mutableIntStateOf(-1)
    var overlay by mutableStateOf(Overlay.NONE)
    var fullscreen by mutableStateOf(false)
    var findBarOpen by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    // --- Ad & popup blocker ---
    val adBlocker = AdBlocker()
    private val filterLists = FilterLists(context)
    var adBlockEnabled by mutableStateOf(stores.adBlockEnabled)
    var popupBlockEnabled by mutableStateOf(stores.popupBlockEnabled)
    var blockerPausedSites by mutableStateOf(stores.blockerPausedSites())
    var blockedInSession by mutableIntStateOf(0)
    var adBlockReady by mutableStateOf(false)
    private var lastPopupToast = 0L

    fun isBlockerPaused(url: String?): Boolean {
        val host = AdBlocker.hostOf(url) ?: return false
        return AdBlocker.matches(blockerPausedSites, host)
    }

    fun toggleAdBlock() {
        adBlockEnabled = !adBlockEnabled
        stores.adBlockEnabled = adBlockEnabled
        currentTab?.let { if (it.url != null) it.webView.reload() }
    }

    fun toggleSitePause() {
        val t = currentTab ?: return
        val host = AdBlocker.hostOf(t.url) ?: return
        val paused = AdBlocker.matches(blockerPausedSites, host)
        stores.setBlockerPaused(host, !paused)
        blockerPausedSites = stores.blockerPausedSites()
        t.webView.reload()
    }

    private fun onPopupBlocked() {
        val now = System.currentTimeMillis()
        if (now - lastPopupToast < 2500) return
        lastPopupToast = now
        Toast.makeText(context, context.getString(R.string.popup_blocked), Toast.LENGTH_SHORT).show()
    }

    private fun injectCosmeticFilters(view: WebView) {
        if (!adBlockEnabled || isBlockerPaused(view.url)) return
        val js = adBlocker.cosmeticJs() ?: return
        view.evaluateJavascript(js, null)
    }

    var shortcuts by mutableStateOf(stores.shortcuts())
    var bookmarks by mutableStateOf(stores.bookmarks())
    var history by mutableStateOf(stores.history())
    private var nextId = 1L
    val tabGroups = mutableStateListOf<TabGroup>()
    private var nextGroupId = 1L

    fun groupOf(tabId: Long): TabGroup? = tabGroups.firstOrNull { tabId in it.tabIds }

    fun createGroup(name: String, colorIndex: Int, members: List<Long>): TabGroup? {
        val ids = members.distinct().filter { id -> tabs.any { it.id == id } }
        if (ids.isEmpty()) return null
        tabGroups.forEach { g -> g.tabIds.removeAll(ids.toSet()) }
        tabGroups.removeAll { it.tabIds.isEmpty() }
        val group = TabGroup(nextGroupId++, name.trim().ifBlank { "Group" }, colorIndex, ids)
        tabGroups.add(group)
        persistSession()
        return group
    }

    fun renameGroup(groupId: Long, name: String) {
        tabGroups.firstOrNull { it.id == groupId }?.let { it.name = name.trim().ifBlank { it.name } }
        persistSession()
    }

    fun recolorGroup(groupId: Long, colorIndex: Int) {
        tabGroups.firstOrNull { it.id == groupId }?.colorIndex = colorIndex
        persistSession()
    }

    /** Closes every tab in the group and removes it (Chrome's "Close group"). */
    fun closeGroup(groupId: Long) {
        val group = tabGroups.firstOrNull { it.id == groupId } ?: return
        group.tabIds.toList().forEach { id ->
            val index = tabs.indexOfFirst { it.id == id }
            if (index >= 0) closeTab(index)
        }
        tabGroups.removeAll { it.id == groupId || it.tabIds.isEmpty() }
        persistSession()
    }

    /** Removes the group but keeps its tabs as ungrouped tabs (Chrome's "Delete group"). */
    fun deleteGroup(groupId: Long) {
        tabGroups.removeAll { it.id == groupId }
        persistSession()
    }

    // --- In-app update flow (same semantics as File Explorer CL: user-tap only, never silent) ---
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    val installedVersionCode: Long = AppUpdate.currentVersionCode(context)
    val installedVersionName: String = AppUpdate.currentVersionName(context)
    private var pendingGrantReturn = false
    private var updateCheckedThisProcess = false

    fun checkForUpdate(silent: Boolean) {
        if (updateState == UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        updateScope.launch {
            val latest = AppUpdate.fetchLatest()
            updateState = when {
                latest == null -> if (silent) UpdateState.Idle else UpdateState.Failed(null)
                AppUpdate.isNewer(latest, installedVersionCode) -> UpdateState.Available(latest)
                else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
            }
        }
    }

    fun checkForUpdateOnce() {
        if (!updateCheckedThisProcess) {
            updateCheckedThisProcess = true
            checkForUpdate(silent = true)
        }
    }

    /** Load cached filter lists and refresh them in the background when stale. */
    private var adBlockInitStarted = false
    fun initAdBlock() {
        if (adBlockInitStarted) return
        adBlockInitStarted = true
        filterLists.loadAndRefresh(updateScope, adBlocker) {
            adBlockReady = adBlocker.isReady()
        }
    }

    fun downloadUpdate() {
        val info = when (val s = updateState) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        } ?: return
        updateScope.launch {
            updateState = UpdateState.Downloading(info, 0)
            updateState = runCatching {
                AppUpdate.download(context, info) { percent ->
                    updateState = UpdateState.Downloading(info, percent)
                }
            }.fold(
                onSuccess = { apk ->
                    if (AppUpdate.verifyApk(context, apk, installedVersionCode)) UpdateState.ReadyToInstall(info, apk)
                    else UpdateState.Failed(info)
                },
                onFailure = { UpdateState.Failed(info) },
            )
        }
    }

    fun installUpdate() {
        val ready = updateState as? UpdateState.ReadyToInstall ?: return
        if (AppUpdate.canInstall(context)) {
            AppUpdate.promptInstall(context, ready.apk)
        } else {
            // First update needs the one-time unknown-sources grant; onResumeAfterGrant
            // offers the install sheet straight away when the user returns.
            pendingGrantReturn = true
            AppUpdate.openUnknownSourcesSettings(context)
        }
    }

    fun onResumeAfterGrant() {
        if (!pendingGrantReturn) return
        pendingGrantReturn = false
        val state = updateState
        if (AppUpdate.shouldAutoPromptOnResume(true, state, AppUpdate.canInstall(context))) {
            AppUpdate.promptInstall(context, (state as UpdateState.ReadyToInstall).apk)
        }
    }

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

    fun captureCurrentPreview() {
        val tab = currentTab ?: return
        val view = tab.webView
        if (tab.url == null || view.width <= 0 || view.height <= 0) return
        val width = 360
        val height = (view.height * width.toFloat() / view.width).toInt().coerceIn(1, 720)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width.toFloat() / view.width, width.toFloat() / view.width)
        view.draw(canvas)
        tab.preview = bitmap
    }

    fun showTabs() { captureCurrentPreview(); overlay = Overlay.TABS }

    fun newTab(url: String? = null): Tab {
        captureCurrentPreview()
        val wv = object : WebView(context) {
            override fun onKeyPreIme(keyCode: Int, event: android.view.KeyEvent): Boolean {
                if (fullscreen && keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (event.action == android.view.KeyEvent.ACTION_UP) fullscreen = false
                    return true
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }
        val tab = Tab(nextId++, wv)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        applyDark(wv)
        wv.webViewClient = object : WebViewClient() {
            @Volatile private var pageUrlCache: String? = null
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // Runs on a WebView IO thread: never touch view.url here, use the cached page URL.
                val pageUrl = if (request.isForMainFrame) request.url.toString() else pageUrlCache
                if (adBlockEnabled && !isBlockerPaused(pageUrl) &&
                    adBlocker.shouldBlock(pageUrl, request.url.toString())
                ) {
                    blockedInSession = adBlocker.blockedCount
                    return EMPTY_RESPONSE
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun doUpdateVisitedHistory(view: WebView, visitedUrl: String?, isReload: Boolean) {
                visitedUrl ?: return
                pageUrlCache = visitedUrl
                if (visitedUrl == "about:blank") return
                tab.url = visitedUrl
                view.title?.let { if (it.isNotBlank()) tab.title = it }
                stores.addHistory(tab.title.ifBlank { visitedUrl }, visitedUrl)
                history = stores.history()
                persistSession()
            }
            override fun onPageFinished(view: WebView, finishedUrl: String?) {
                view.title?.let { if (it.isNotBlank()) tab.title = it }
                injectCosmeticFilters(view)
                // Catch late-injected ad containers once; cheap because the JS no-ops when already applied.
                view.postDelayed({ injectCosmeticFilters(view) }, 2000)
            }
        }
        wv.settings.setSupportMultipleWindows(true)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
            ): Boolean {
                // Popup rule: windows without a user gesture are ads - swallow them.
                // Real taps (links, buttons) open in a normal Manny tab.
                if (popupBlockEnabled && !isBlockerPaused(view.url) && !isUserGesture) {
                    onPopupBlocked()
                    return false
                }
                val child = newTab()
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = child.webView
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) {
                val idx = tabs.indexOfFirst { it.webView === window }
                if (idx >= 0) closeTab(idx)
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                tab.favicon = icon
                view.url?.let { pageUrl ->
                    favicons.save(pageUrl, icon)
                    faviconTick++
                }
            }
            override fun onReceivedTitle(view: WebView, title: String) { tab.title = title }
        }
        wv.setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(dlUrl))
                CookieManager.getInstance().getCookie(dlUrl)?.let { req.addRequestHeader("Cookie", it) }
                userAgent?.let { req.addRequestHeader("User-Agent", it) }
                val name = URLUtil.guessFileName(dlUrl, contentDisposition, mimeType)
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            } catch (_: Exception) { Toast.makeText(context, "Download could not be started", Toast.LENGTH_LONG).show() }
        }
        tabs.add(tab)
        currentTabIndex = tabs.size - 1
        if (url != null) navigate(url)
        persistSession()
        return tab
    }

    fun closeTab(index: Int) {
        val t = tabs.getOrNull(index) ?: return
        t.preview = null
        t.webView.destroy()
        tabs.removeAt(index)
        tabGroups.forEach { g -> g.tabIds.remove(t.id) }
        tabGroups.removeAll { it.tabIds.isEmpty() }
        if (tabs.isEmpty()) { newTab() }
        else if (index < currentTabIndex) currentTabIndex--
        else if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        persistSession()
    }

    fun closeAllTabs() {
        tabs.forEach { it.preview = null; it.webView.destroy() }
        tabs.clear()
        tabGroups.clear()
        newTab()
    }

    /** Switches to a tab, loading a restored tab's page on first activation. */
    fun activateTab(index: Int) {
        if (index !in tabs.indices) return
        currentTabIndex = index
        val t = tabs[index]
        t.pendingUrl?.let { t.webView.loadUrl(it); t.pendingUrl = null }
    }

    // --- Session persistence: tabs and manual groups survive process death and updates ---

    fun persistSession() {
        if (!sessionPersistenceEnabled) return
        stores.saveSession(
            app.mannybrowser.data.Session.of(
                tabs = tabs.map { app.mannybrowser.data.SessionTab(it.url, it.title) },
                tabIds = tabs.map { it.id },
                groups = tabGroups.map { Triple(it.name, it.colorIndex, it.tabIds.toList()) },
                current = currentTabIndex
            )
        )
    }

    /**
     * Recreates tabs and groups from the last saved session. Only the active tab loads its page;
     * the rest load on first activation. Returns false when there is nothing to restore.
     */
    fun restoreSession(): Boolean {
        if (!sessionPersistenceEnabled) return false
        val session = stores.loadSession() ?: return false
        if (session.tabs.isEmpty()) return false
        session.tabs.forEach { saved ->
            val tab = newTab()
            tab.url = saved.url
            tab.title = saved.title
            tab.pendingUrl = saved.url
        }
        session.groups.forEach { g ->
            val ids = g.members.mapNotNull { tabs.getOrNull(it)?.id }
            if (ids.isNotEmpty()) createGroup(g.name, g.colorIndex, ids)
        }
        activateTab(session.current)
        return true
    }

    fun navigate(input: String) {
        val t = currentTab ?: return
        val target = toUrl(input)
        t.pendingUrl = null
        t.url = target
        t.webView.loadUrl(target)
        persistSession()
    }

    fun goHome() {
        val t = currentTab ?: return
        t.preview = null
        t.webView.stopLoading()
        t.url = null
        t.title = ""
        t.pendingUrl = null
        persistSession()
    }

    fun openInNewTab(url: String) {
        overlay = Overlay.NONE
        newTab(url)
    }

    fun toggleBookmark() {
        val t = currentTab ?: return
        val u = t.url ?: return
        if (stores.isBookmarked(u)) stores.removeBookmark(u)
        else {
            stores.addBookmark(t.title.ifBlank { u }, u)
            t.favicon?.let { icon ->
                favicons.save(u, icon)
                faviconTick++
            }
        }
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

    fun enterFullscreen() {
        findBarOpen = false
        currentTab?.webView?.clearMatches()
        (context as? android.app.Activity)?.let { activity ->
            val focus = activity.currentFocus
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(focus?.windowToken, 0)
            focus?.clearFocus()
        }
        fullscreen = true
    }

    fun shareCurrent() {
        val u = currentTab?.url ?: return
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, u)
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun destroyAll() {
        tabs.forEach { it.preview = null; it.webView.destroy() }
        tabs.clear()
    }

    companion object {
        /** Instrumentation tests switch persistence off so one test's session never leaks into the next. */
        @Volatile var sessionPersistenceEnabled = true
        private val EMPTY_RESPONSE = WebResourceResponse(
            "text/plain", "utf-8", 403, "Forbidden", emptyMap(),
            java.io.ByteArrayInputStream(ByteArray(0))
        )

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
