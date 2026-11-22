package app.mannybrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.mannybrowser.data.StartTile
import app.mannybrowser.data.StartTiles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mannybrowser.BrowserState
import app.mannybrowser.R
import app.mannybrowser.data.Shortcut
import androidx.compose.foundation.layout.fillMaxWidth

/** Neutral dark circle behind every tile, like Chrome's home: the icon carries the color. */
private val TileBackground = Color(0xFF303134)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPage(state: BrowserState) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var removingFavorite by remember { mutableStateOf<StartTile?>(null) }
    val tick = state.faviconTick

    Column(Modifier.fillMaxSize()) {
        var homeSearch by remember { mutableStateOf("") }
        TextField(
            value = homeSearch,
            onValueChange = { homeSearch = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (homeSearch.isNotBlank()) state.navigate(homeSearch)
            })
        )
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(StartTiles.merge(state.shortcuts, state.bookmarks), key = { it.url }) { t ->
            val icon = remember(t.url, tick) { state.favicons.load(t.url) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    onClick = { state.navigate(t.url) },
                    onLongClick = {
                        if (t.bookmark) removingFavorite = t
                        else editing = Shortcut(t.name, t.url)
                    }
                )
            ) {
                Box(
                    Modifier.size(56.dp).background(TileBackground, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            icon.asImageBitmap(),
                            contentDescription = stringResource(R.string.site_icon),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Text(
                            t.name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                }
                Text(
                    t.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { showAdd = true }
            ) {
                Box(
                    Modifier.size(56.dp).background(TileBackground, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_shortcut),
                        tint = Color.White
                    )
                }
            }
        }
    }
    }

    if (showAdd) {
        ShortcutDialog(
            title = stringResource(R.string.add_shortcut),
            initialName = "",
            initialUrl = "",
            onDismiss = { showAdd = false },
            onSave = { name, url ->
                state.addShortcut(name, url)
                showAdd = false
            }
        )
    }

    removingFavorite?.let { t ->
        AlertDialog(
            onDismissRequest = { removingFavorite = null },
            title = { Text(stringResource(R.string.remove_favorite)) },
            text = { Text(t.name) },
            confirmButton = {
                TextButton(onClick = {
                    state.removeBookmark(t.url)
                    removingFavorite = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removingFavorite = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    editing?.let { s ->
        ShortcutDialog(
            title = stringResource(R.string.edit_shortcut),
            initialName = s.name,
            initialUrl = s.url,
            onDismiss = { editing = null },
            onSave = { name, url ->
                state.updateShortcut(s, name, url)
                editing = null
            },
            onDelete = {
                state.removeShortcut(s)
                editing = null
            }
        )
    }
}

@Composable
private fun ShortcutDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url_label)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onSave(name.trim(), url.trim()) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                onDelete?.let {
                    TextButton(onClick = it) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
