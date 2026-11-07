package app.mannybrowser.ui

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R
import java.text.DateFormat
import java.util.Date

@Composable
private fun ListShell(title: String, state: BrowserState, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.overlay = Overlay.NONE }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun UrlRow(
    title: String,
    url: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { url }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun BookmarksScreen(state: BrowserState) {
    ListShell(stringResource(R.string.bookmarks), state) {
        if (state.bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_bookmarks))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.bookmarks) { b ->
                    UrlRow(b.title, b.url, onClick = {
                        state.navigate(b.url)
                        state.overlay = Overlay.NONE
                    }) {
                        IconButton(onClick = { state.removeBookmark(b.url) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: BrowserState) {
    ListShell(stringResource(R.string.history), state) {
        if (state.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { }
                    TextButton(onClick = { state.clearHistory() }) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(state.history) { h ->
                        UrlRow(h.title, h.url, onClick = {
                            state.navigate(h.url)
                            state.overlay = Overlay.NONE
                        }) {
                            Text(
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(state: BrowserState) {
    val context = LocalContext.current
    val downloads = queryDownloads(context)
    ListShell(stringResource(R.string.downloads), state) {
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_downloads))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(downloads) { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun queryDownloads(context: Context): List<String> {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val out = mutableListOf<String>()
    try {
        val c = dm.query(DownloadManager.Query())
        c?.use {
            val idx = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            while (it.moveToNext()) {
                if (idx >= 0) out.add(it.getString(idx) ?: "?")
            }
        }
    } catch (_: Exception) { }
    return out
}

@Composable
fun UpdateOverlay(state: BrowserState) {
    ListShell(stringResource(R.string.update_title), state) {
        UpdateScreen(state)
    }
}
