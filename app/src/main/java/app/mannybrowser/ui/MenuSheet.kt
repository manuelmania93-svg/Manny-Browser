package app.mannybrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R

private data class MenuItem(val icon: ImageVector, val labelRes: Int, val action: (BrowserState) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: BrowserState) {
    val items = listOf(
        MenuItem(Icons.Filled.History, R.string.history) { it.overlay = Overlay.HISTORY },
        MenuItem(Icons.Filled.ArrowDownward, R.string.downloads) { it.overlay = Overlay.DOWNLOADS },
        MenuItem(Icons.Filled.Add, R.string.new_tab) { it.newTab() },
        MenuItem(Icons.Filled.DarkMode, R.string.dark_mode) { it.toggleDark() },
        MenuItem(Icons.Filled.FindInPage, R.string.find_in_page) { it.findBarOpen = true },
        MenuItem(Icons.Filled.Share, R.string.share) { it.shareCurrent() },
        MenuItem(Icons.Filled.Fullscreen, R.string.fullscreen) { it.fullscreen = true }
    )
    ModalBottomSheet(onDismissRequest = { state.menuOpen = false }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        state.menuOpen = false
                        item.action(state)
                    }
                ) {
                    Icon(item.icon, contentDescription = null)
                    Text(
                        stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
