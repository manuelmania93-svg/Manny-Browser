package app.mannybrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import app.mannybrowser.TabGroups
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R
import kotlin.math.abs

private val CardColors = listOf(
    Color(0xFF2E6BF0), Color(0xFF8E44AD), Color(0xFF16A085),
    Color(0xFFD35400), Color(0xFFC0392B), Color(0xFF2C3E50)
)

@Composable
fun TabGridScreen(state: BrowserState) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val groups = TabGroups.group(state.tabs.map { TabGroups.Entry(it.id, it.url) })
    val expanded = groups.firstOrNull { it.key == expandedKey && it.tabIds.size > 1 }
    BackHandler(enabled = expanded != null) { expandedKey = null }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (expanded != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { expandedKey = null }) { Text(stringResource(R.string.all_tabs)) }
                    Text(expanded.site.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.tab_count, expanded.tabIds.size))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cards = if (expanded == null) groups else expanded.tabIds.map { id ->
                    TabGroups.Group("member:$id", expanded.site, listOf(id))
                }
                items(cards.size, key = { cards[it].key }) { cardIndex ->
                    val group = cards[cardIndex]
                    val tab = state.tabs.firstOrNull { it.id in group.tabIds }
                    if (tab != null) {
                        val grouped = group.tabIds.size > 1
                        val label = if (grouped) group.site.orEmpty() else tab.title.ifBlank {
                            tab.url ?: stringResource(R.string.new_tab)
                        }
                        val active = state.currentTab?.id in group.tabIds
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    if (grouped) expandedKey = group.key
                                    else {
                                        val index = state.tabs.indexOfFirst { it.id == tab.id }
                                        if (index >= 0) {
                                            state.currentTabIndex = index
                                            state.overlay = Overlay.NONE
                                        }
                                    }
                                }
                        ) {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                tab.favicon?.let { icon -> Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp)) }
                                    ?: Text(label.take(1).uppercase(), modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
                                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (grouped) {
                                    Text(stringResource(R.string.tab_count, group.tabIds.size),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium)
                                } else {
                                    IconButton(onClick = {
                                        val index = state.tabs.indexOfFirst { it.id == tab.id }
                                        if (index >= 0) state.closeTab(index)
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_tab), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surface)) {
                                tab.preview?.let { bitmap ->
                                    Image(bitmap.asImageBitmap(), contentDescription = "Page preview", contentScale = ContentScale.Crop,
                                        alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                                } ?: Text(if (grouped) stringResource(R.string.open_tab_group) else if (tab.url == null)
                                    stringResource(R.string.new_tab) else stringResource(R.string.loading_page),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                                if (grouped) Text(stringResource(R.string.open_tab_group),
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    state.closeAllTabs()
                    state.overlay = Overlay.NONE
                }) { Text(stringResource(R.string.close_all)) }
                TextButton(onClick = {
                    state.newTab()
                    state.overlay = Overlay.NONE
                }) { Text(stringResource(R.string.new_tab)) }
            }
        }
    }
}
