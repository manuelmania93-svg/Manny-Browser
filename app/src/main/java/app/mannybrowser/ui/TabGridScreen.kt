package app.mannybrowser.ui

import androidx.compose.foundation.background
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
                        Modifier.clickable {
                            state.currentTabIndex = index
                            state.overlay = Overlay.NONE
                        }
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp).background(
                                CardColors[(label.hashCode() and Int.MAX_VALUE) % CardColors.size],
                                RoundedCornerShape(12.dp)
                            )
                        ) {
                            Text(
                                label.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 36.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            IconButton(
                                onClick = { state.closeTab(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        tab.url?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
