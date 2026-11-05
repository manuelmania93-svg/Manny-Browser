package app.mannybrowser.ui

import android.view.ViewGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R

@Composable
fun BrowserScreen(state: BrowserState) {
    var urlText by remember(state.currentTab?.id, state.currentTab?.url) { mutableStateOf(state.currentTab?.url.orEmpty()) }
    var findText by remember { mutableStateOf("") }
    val tab = state.currentTab

    BackHandler {
        when {
            state.fullscreen -> state.fullscreen = false
            state.menuOpen -> state.menuOpen = false
            state.findBarOpen -> state.findBarOpen = false
            state.overlay != Overlay.NONE -> state.overlay = Overlay.NONE
            tab?.webView?.canGoBack() == true && tab.url != null -> tab.webView.goBack()
            tab?.url != null -> state.goHome()
            state.tabs.size > 1 -> state.closeTab(state.currentTabIndex)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!state.fullscreen) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bookmarked = tab?.url?.let { state.bookmarks.any { b -> b.url == it } } == true
                    IconButton(onClick = { state.toggleBookmark() }) {
                        Icon(
                            if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(R.string.bookmarks)
                        )
                    }
                    TextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (urlText.isNotBlank()) state.navigate(urlText)
                        }),
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = {
                        tab?.url?.let { tab.webView.reload() } ?: state.navigate(urlText)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == null || tab.url == null) {
                    StartPage(state)
                } else {
                    androidx.compose.runtime.key(tab.id) {
                        AndroidView(
                            factory = { ctx ->
                                tab.webView.apply {
                                    (parent as? ViewGroup)?.removeView(this)
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (state.findBarOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = findText,
                        onValueChange = {
                            findText = it
                            tab?.webView?.findAllAsync(it)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.find_in_page)) },
                        singleLine = true
                    )
                    IconButton(onClick = { tab?.webView?.findNext(false) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(onClick = { tab?.webView?.findNext(true) }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    IconButton(onClick = {
                        state.findBarOpen = false
                        tab?.webView?.clearMatches()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
            }

            if (!state.fullscreen) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tab?.webView?.goBack() }, enabled = tab?.webView?.canGoBack() == true) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { tab?.webView?.goForward() }, enabled = tab?.webView?.canGoForward() == true) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { state.goHome() }) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { state.overlay = Overlay.BOOKMARKS }) {
                        Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.bookmarks))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { state.showTabs() }, modifier = Modifier.semantics { contentDescription = "Tabs" }) {
                            Box(
                                Modifier.size(26.dp).background(
                                    MaterialTheme.colorScheme.surfaceVariant, CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${state.tabs.size}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    IconButton(onClick = { state.overlay = Overlay.NONE; state.menuOpen = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            }
        }

        when (state.overlay) {
            Overlay.TABS -> TabGridScreen(state)
            Overlay.BOOKMARKS -> BookmarksScreen(state)
            Overlay.HISTORY -> HistoryScreen(state)
            Overlay.DOWNLOADS -> DownloadsScreen(state)
            Overlay.NONE -> { }
        }

        if (state.menuOpen) MenuSheet(state)
    }
}
}
