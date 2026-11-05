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
import androidx.compose.runtime.Composable
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.tabs) { index, tab ->
                    val label = tab.title.ifBlank { tab.url ?: stringResource(R.string.new_tab) }
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable {
                            state.currentTabIndex = index
                            state.overlay = Overlay.NONE
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label.take(1).uppercase(), modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { state.closeTab(index) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(20.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surface)) {
                            tab.preview?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), contentDescription = "Page preview", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                            } ?: Text(if (tab.url == null) "New tab" else "Loading page", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
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
