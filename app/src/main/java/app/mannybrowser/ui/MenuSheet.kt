package app.mannybrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R

/**
 * Chrome-style 3-dot menu: icon row (back, forward, bookmark, downloads, reload),
 * then grouped text rows with separators. Rendered inside a Box anchor in BrowserScreen;
 * the Zoom and Clear-data dialogs are rendered separately at screen level, since the
 * menu itself leaves composition when it closes.
 */
@Composable
fun MenuSheet(state: BrowserState) {
    val tab = state.currentTab
    val hasPage = tab?.url != null
    val bookmarked = tab?.url?.let { url -> state.bookmarks.any { it.url == url } } == true
    val pausedHere = state.isBlockerPaused(tab?.url)

    fun close() { state.menuOpen = false }

    DropdownMenu(
        expanded = state.menuOpen,
        onDismissRequest = { state.menuOpen = false },
        modifier = Modifier.width(300.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        // Top icon row, like Chrome: back, forward, bookmark, downloads, reload.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { close(); tab?.webView?.goBack() },
                enabled = tab?.webView?.canGoBack() == true
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            IconButton(
                onClick = { close(); tab?.webView?.goForward() },
                enabled = tab?.webView?.canGoForward() == true
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward") }
            IconButton(onClick = { close(); state.toggleBookmark() }, enabled = hasPage) {
                Icon(
                    if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.bookmarks)
                )
            }
            IconButton(onClick = { close(); state.overlay = Overlay.DOWNLOADS }) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.downloads))
            }
            IconButton(onClick = { close(); tab?.webView?.reload() }, enabled = hasPage) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload))
            }
        }
        HorizontalDivider()

        @Composable
        fun item(labelRes: Int, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, action: () -> Unit) {
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = { close(); action() },
                enabled = enabled,
                trailingIcon = trailing
            )
        }

        item(R.string.new_tab) { state.newTab() }
        item(R.string.add_tab_to_new_group, enabled = hasPage) { state.addCurrentTabToNewGroup() }
        HorizontalDivider()

        item(R.string.history) { state.overlay = Overlay.HISTORY }
        item(R.string.clear_browsing_data) { state.clearDataDialogOpen = true }
        HorizontalDivider()

        item(R.string.downloads) { state.overlay = Overlay.DOWNLOADS }
        item(R.string.bookmarks) { state.overlay = Overlay.BOOKMARKS }
        HorizontalDivider()

        item(R.string.zoom, enabled = hasPage) { state.zoomDialogOpen = true }
        HorizontalDivider()

        item(R.string.share, enabled = hasPage) { state.shareCurrent() }
        item(R.string.find_in_page, enabled = hasPage) { state.findBarOpen = true }
        item(R.string.add_shortcut, enabled = hasPage) { state.addShortcutForCurrent() }
        item(
            R.string.desktop_site,
            enabled = hasPage,
            trailing = {
                if (tab?.desktopMode == true) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        ) { state.toggleDesktopMode() }
        HorizontalDivider()

        item(if (state.adBlockEnabled) R.string.ad_blocker_on else R.string.ad_blocker_off) { state.toggleAdBlock() }
        item(
            if (pausedHere) R.string.ad_blocker_resume_site else R.string.ad_blocker_pause_site,
            enabled = hasPage
        ) { state.toggleSitePause() }
        item(R.string.dark_mode) { state.toggleDark() }
        item(R.string.fullscreen) { state.enterFullscreen() }
        item(R.string.update_title) { state.overlay = Overlay.UPDATE }
    }
}

/** Chrome's Zoom menu entry: minus/plus steps around the current per-tab text zoom. */
@Composable
fun ZoomDialog(state: BrowserState) {
    val tab = state.currentTab
    AlertDialog(
        onDismissRequest = { state.zoomDialogOpen = false },
        title = { Text(stringResource(R.string.zoom)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { state.changeTextZoom(-10) }, enabled = (tab?.textZoom ?: 100) > 50) {
                    Text("-", style = MaterialTheme.typography.headlineSmall)
                }
                Text("${tab?.textZoom ?: 100}%", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { state.changeTextZoom(10) }, enabled = (tab?.textZoom ?: 100) < 200) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.zoomDialogOpen = false }) { Text(stringResource(R.string.save)) }
        }
    )
}

/** Chrome's "Clear browsing data": pick history, cache and/or cookies, then clear. */
@Composable
fun ClearDataDialog(state: BrowserState) {
    var clearHist by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { state.clearDataDialogOpen = false },
        title = { Text(stringResource(R.string.clear_browsing_data)) },
        text = {
            androidx.compose.foundation.layout.Column {
                @Composable
                fun option(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = onChange)
                        Text(stringResource(labelRes))
                    }
                }
                option(R.string.clear_data_history, clearHist) { clearHist = it }
                option(R.string.clear_data_cache, clearCache) { clearCache = it }
                option(R.string.clear_data_cookies, clearCookies) { clearCookies = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.clearBrowsingData(clearHist, clearCache, clearCookies)
                    state.clearDataDialogOpen = false
                },
                enabled = clearHist || clearCache || clearCookies
            ) { Text(stringResource(R.string.clear_action)) }
        },
        dismissButton = {
            TextButton(onClick = { state.clearDataDialogOpen = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}
