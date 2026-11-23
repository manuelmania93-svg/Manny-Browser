package app.mannybrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mannybrowser.BrowserState
import app.mannybrowser.Overlay
import app.mannybrowser.R
import app.mannybrowser.Tab
import app.mannybrowser.TabGroup
import app.mannybrowser.TabGroups

val GroupColors = listOf(
    Color(0xFF2E6BF0), Color(0xFF8E44AD), Color(0xFF16A085),
    Color(0xFFD35400), Color(0xFFC0392B), Color(0xFF2C3E50)
)

/** One card in the tab overview: a single tab, an automatic same-site group, or a manual group. */
private sealed class Card(val key: String) {
    class Single(val tab: Tab) : Card("tab:${tab.id}")
    class AutoGroup(val site: String, val tabIds: List<Long>) : Card("site:$site")
    class Manual(val group: TabGroup) : Card("group:${group.id}")
}

@Composable
fun TabGridScreen(state: BrowserState) {
    var listMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<Long>()) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    var groupMenuFor by remember { mutableStateOf<Long?>(null) }
    var nameDialogFor by remember { mutableStateOf<Long?>(null) }
    var colorDialogFor by remember { mutableStateOf<Long?>(null) }
    var createDialogFor by remember { mutableStateOf<List<Long>?>(null) }

    val searching = query.isNotBlank()
    val matches: (Tab) -> Boolean = { tab ->
        query.isBlank() || tab.title.contains(query, true) || (tab.url?.contains(query, true) == true)
    }

    // Manual groups own their tabs; the rest falls back to automatic same-site grouping.
    val cards = mutableListOf<Card>()
    if (!searching) {
        state.tabGroups.forEach { g ->
            val alive = g.tabIds.filter { id -> state.tabs.any { it.id == id } }
            if (alive.isNotEmpty()) cards += Card.Manual(g)
        }
    }
    val manualIds = state.tabGroups.flatMap { it.tabIds }.toSet()
    val loose = state.tabs.filter { it.id !in manualIds && matches(it) }
    TabGroups.group(loose.map { TabGroups.Entry(it.id, it.url) }).forEach { g ->
        if (g.tabIds.size > 1 && g.site != null) cards += Card.AutoGroup(g.site, g.tabIds)
        else g.tabIds.forEach { id -> state.tabs.firstOrNull { it.id == id }?.let { cards += Card.Single(it) } }
    }
    if (searching) {
        cards.clear()
        state.tabGroups.filter { it.name.contains(query, true) }.forEach { cards += Card.Manual(it) }
        state.tabs.filter { matches(it) }.forEach { cards += Card.Single(it) }
    }

    val expanded = cards.firstOrNull { it.key == expandedKey }
    BackHandlerCompat(enabled = expanded != null || selecting) {
        when {
            expanded != null -> expandedKey = null
            selecting -> { selecting = false; selected.value = emptySet() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (expanded != null) {
                ExpandedHeader(state, expanded, onBack = { expandedKey = null })
            } else if (selecting) {
                SelectionHeader(
                    count = selected.value.size,
                    onNewGroup = { createDialogFor = selected.value.toList() },
                    onClose = {
                        selected.value.sortedDescending().forEach { id ->
                            val index = state.tabs.indexOfFirst { it.id == id }
                            if (index >= 0) state.closeTab(index)
                        }
                        selecting = false; selected.value = emptySet()
                    },
                    onCancel = { selecting = false; selected.value = emptySet() }
                )
            } else {
                OverviewHeader(
                    tabCount = state.tabs.size,
                    listMode = listMode,
                    menuOpen = headerMenu,
                    onMenu = { headerMenu = it },
                    onNewTab = { state.newTab(); state.overlay = Overlay.NONE },
                    onToggleLayout = { listMode = !listMode },
                    onNewGroup = { selecting = true },
                    onCloseAll = { state.closeAllTabs(); state.overlay = Overlay.NONE },
                    onSelect = { selecting = true }
                )
                SearchBar(query) { query = it }
            }

            val expandMembers: List<Tab>? = when (expanded) {
                is Card.Single -> listOf(expanded.tab)
                is Card.AutoGroup -> expanded.tabIds.mapNotNull { id -> state.tabs.firstOrNull { it.id == id } }
                is Card.Manual -> expanded.group.tabIds.mapNotNull { id -> state.tabs.firstOrNull { it.id == id } }
                null -> null
            }

            if (expanded != null && expandMembers != null) {
                MemberGrid(state, expandMembers, selecting, selected.value,
                    onSelect = { id ->
                        selected.value = selected.value.let { if (id in it) it - id else it + id }
                    },
                    onOpen = { tab ->
                        val index = state.tabs.indexOfFirst { it.id == tab.id }
                        if (index >= 0) { state.activateTab(index); state.overlay = Overlay.NONE }
                    })
            } else if (listMode && !selecting) {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    items(cards, key = { it.key }) { card ->
                        ListRow(state, card, onOpen = {
                            when (card) {
                                is Card.Single -> openTab(state, card.tab)
                                else -> expandedKey = card.key
                            }
                        })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cards, key = { it.key }) { card ->
                        when (card) {
                            is Card.Single -> TabCard(state, card.tab, selecting,
                                checked = card.tab.id in selected.value,
                                onToggleSelect = {
                                    selected.value = selected.value.let {
                                        if (card.tab.id in it) it - card.tab.id else it + card.tab.id
                                    }
                                },
                                onOpen = { openTab(state, card.tab) })
                            is Card.AutoGroup -> AutoGroupCard(state, card, selecting,
                                checked = card.tabIds.all { it in selected.value },
                                onToggleSelect = {
                                    selected.value = if (card.tabIds.all { it in selected.value })
                                        selected.value - card.tabIds.toSet()
                                    else selected.value + card.tabIds.toSet()
                                },
                                onOpen = { expandedKey = card.key })
                            is Card.Manual -> ManualGroupCard(state, card, selecting,
                                checked = card.group.tabIds.all { it in selected.value },
                                menuOpen = groupMenuFor == card.group.id,
                                onMenu = { open -> groupMenuFor = if (open) card.group.id else null },
                                onToggleSelect = {
                                    val ids = card.group.tabIds.toSet()
                                    selected.value = if (ids.all { it in selected.value })
                                        selected.value - ids else selected.value + ids
                                },
                                onEditName = { nameDialogFor = card.group.id },
                                onEditColor = { colorDialogFor = card.group.id },
                                onCloseGroup = { state.closeGroup(card.group.id) },
                                onDeleteGroup = { state.deleteGroup(card.group.id) },
                                onSelectTabs = { selecting = true },
                                onOpen = { expandedKey = card.key })
                        }
                    }
                }
            }
        }
    }

    nameDialogFor?.let { groupId ->
        val group = state.tabGroups.firstOrNull { it.id == groupId }
        if (group != null) GroupNameDialog(group.name, onDismiss = { nameDialogFor = null },
            onSave = { state.renameGroup(groupId, it); nameDialogFor = null })
        else nameDialogFor = null
    }
    colorDialogFor?.let { groupId ->
        val group = state.tabGroups.firstOrNull { it.id == groupId }
        if (group != null) GroupColorDialog(group.colorIndex, onDismiss = { colorDialogFor = null },
            onPick = { state.recolorGroup(groupId, it); colorDialogFor = null })
        else colorDialogFor = null
    }
    createDialogFor?.let { members ->
        GroupNameDialog("", onDismiss = { createDialogFor = null },
            onSave = { name ->
                state.createGroup(name, members.size % GroupColors.size, members)
                createDialogFor = null; selecting = false; selected.value = emptySet()
            })
    }
}

@Composable
private fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

private fun openTab(state: BrowserState, tab: Tab) {
    val index = state.tabs.indexOfFirst { it.id == tab.id }
    if (index >= 0) { state.activateTab(index); state.overlay = Overlay.NONE }
}

@Composable
private fun OverviewHeader(
    tabCount: Int, listMode: Boolean, menuOpen: Boolean,
    onMenu: (Boolean) -> Unit, onNewTab: () -> Unit, onToggleLayout: () -> Unit,
    onNewGroup: () -> Unit, onCloseAll: () -> Unit, onSelect: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(56.dp).clickable(onClick = onNewTab)
                .semantics { contentDescription = "new_tab_tile" }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_tab),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text("$tabCount", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium)
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onToggleLayout) {
            Icon(
                if (listMode) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = stringResource(R.string.toggle_layout)
            )
        }
        Box {
            IconButton(onClick = { onMenu(true) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tab_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.new_tab)) },
                    onClick = { onMenu(false); onNewTab() })
                DropdownMenuItem(text = { Text(stringResource(R.string.new_tab_group)) },
                    onClick = { onMenu(false); onNewGroup() })
                DropdownMenuItem(text = { Text(stringResource(R.string.close_all)) },
                    onClick = { onMenu(false); onCloseAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.select_tabs)) },
                    onClick = { onMenu(false); onSelect() })
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        placeholder = { Text(stringResource(R.string.search_tabs)) },
        singleLine = true,
        shape = RoundedCornerShape(26.dp)
    )
}

@Composable
private fun SelectionHeader(count: Int, onNewGroup: () -> Unit, onClose: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Text(stringResource(R.string.tabs_selected, count), Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onNewGroup, enabled = count > 0) {
            Text(stringResource(R.string.new_group_action, count))
        }
        TextButton(onClick = onClose, enabled = count > 0) {
            Text(stringResource(R.string.close_selected, count))
        }
    }
}

@Composable
private fun ExpandedHeader(state: BrowserState, card: Card, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.all_tabs)) }
        val label = when (card) {
            is Card.Single -> card.tab.title.ifBlank { card.tab.url.orEmpty() }
            is Card.AutoGroup -> card.site
            is Card.Manual -> card.group.name
        }
        val count = when (card) {
            is Card.Single -> 1
            is Card.AutoGroup -> card.tabIds.size
            is Card.Manual -> card.group.tabIds.size
        }
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.tab_count, count))
    }
}

@Composable
private fun MemberGrid(
    state: BrowserState, members: List<Tab>, selecting: Boolean, selected: Set<Long>,
    onSelect: (Long) -> Unit, onOpen: (Tab) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(members, key = { it.id }) { tab ->
            TabCard(state, tab, selecting, checked = tab.id in selected,
                onToggleSelect = { onSelect(tab.id) }, onOpen = { onOpen(tab) })
        }
    }
}

@Composable
private fun SelectBadge(checked: Boolean, label: String) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = stringResource(R.string.select_tab, label),
        tint = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(26.dp)
    )
}

@Composable
private fun TabCard(
    state: BrowserState, tab: Tab, selecting: Boolean, checked: Boolean,
    onToggleSelect: () -> Unit, onOpen: () -> Unit
) {
    val label = tab.title.ifBlank { tab.url ?: stringResource(R.string.new_tab) }
    val active = state.currentTab?.id == tab.id
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = if (selecting) onToggleSelect else onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tab.favicon?.let { icon ->
                Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp))
            } ?: Text(label.take(1).uppercase(), Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selecting) {
                SelectBadge(checked, label)
            } else {
                IconButton(onClick = {
                    val index = state.tabs.indexOfFirst { it.id == tab.id }
                    if (index >= 0) state.closeTab(index)
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_tab),
                        modifier = Modifier.size(20.dp))
                }
            }
        }
        PreviewBox(tab, 240.dp)
    }
}

@Composable
private fun PreviewBox(tab: Tab, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surface)) {
        tab.preview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Page preview",
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize())
        } ?: Text(
            if (tab.url == null) stringResource(R.string.new_tab) else stringResource(R.string.loading_page),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun AutoGroupCard(
    state: BrowserState, card: Card.AutoGroup, selecting: Boolean, checked: Boolean,
    onToggleSelect: () -> Unit, onOpen: () -> Unit
) {
    val tab = card.tabIds.mapNotNull { id -> state.tabs.firstOrNull { it.id == id } }.firstOrNull()
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = if (selecting) onToggleSelect else onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tab?.favicon?.let { icon ->
                Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp))
            } ?: Text(card.site.take(1).uppercase(), Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
            Text(card.site, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selecting) {
                SelectBadge(checked, card.site)
            } else {
                Text(stringResource(R.string.tab_count, card.tabIds.size),
                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Box(Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surface)) {
            tab?.preview?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = "Page preview",
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.open_tab_group),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ManualGroupCard(
    state: BrowserState, card: Card.Manual, selecting: Boolean, checked: Boolean,
    menuOpen: Boolean, onMenu: (Boolean) -> Unit, onToggleSelect: () -> Unit,
    onEditName: () -> Unit, onEditColor: () -> Unit, onCloseGroup: () -> Unit,
    onDeleteGroup: () -> Unit, onSelectTabs: () -> Unit, onOpen: () -> Unit
) {
    val group = card.group
    val color = GroupColors[group.colorIndex % GroupColors.size]
    val members = group.tabIds.mapNotNull { id -> state.tabs.firstOrNull { it.id == id } }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.25f))
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = if (selecting) onToggleSelect else onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(group.name, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.tab_count, members.size),
                    style = MaterialTheme.typography.labelSmall)
            }
            if (selecting) {
                SelectBadge(checked, group.name)
            } else {
                Box {
                    IconButton(onClick = { onMenu(true) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.group_options),
                            modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.select_tabs)) },
                            onClick = { onMenu(false); onSelectTabs() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_group_name)) },
                            onClick = { onMenu(false); onEditName() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_group_color)) },
                            onClick = { onMenu(false); onEditColor() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.close_group)) },
                            onClick = { onMenu(false); onCloseGroup() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_group)) },
                            onClick = { onMenu(false); onDeleteGroup() })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.take(2).forEach { tab -> MiniPreview(tab, Modifier.weight(1f)) }
            if (members.size < 2) Box(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.drop(2).take(2).forEach { tab -> MiniPreview(tab, Modifier.weight(1f)) }
            if (members.size < 4) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniPreview(tab: Tab, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .height(104.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        tab.preview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Page preview",
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize())
        } ?: tab.favicon?.let { icon ->
            Image(icon.asImageBitmap(), contentDescription = null,
                modifier = Modifier.size(28.dp).align(Alignment.Center))
        } ?: Text(tab.title.ifBlank { tab.url.orEmpty() }.take(1).uppercase(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ListRow(state: BrowserState, card: Card, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (card) {
            is Card.Single -> {
                card.tab.favicon?.let { Image(it.asImageBitmap(), null, Modifier.size(22.dp)) }
                    ?: Text(card.tab.title.take(1).uppercase(), Modifier.width(22.dp),
                        color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(card.tab.title.ifBlank { stringResource(R.string.new_tab) },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    card.tab.url?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = {
                    val index = state.tabs.indexOfFirst { it.id == card.tab.id }
                    if (index >= 0) state.closeTab(index)
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_tab),
                        modifier = Modifier.size(20.dp))
                }
            }
            is Card.AutoGroup -> {
                Text(card.site.take(1).uppercase(), Modifier.width(22.dp),
                    color = MaterialTheme.colorScheme.primary)
                Text(card.site, Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.tab_count, card.tabIds.size),
                    style = MaterialTheme.typography.labelMedium)
            }
            is Card.Manual -> {
                val color = GroupColors[card.group.colorIndex % GroupColors.size]
                Box(Modifier.size(16.dp).clip(CircleShape).background(color))
                Text(card.group.name, Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.tab_count, card.group.tabIds.size),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun GroupNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_group_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.group_name_hint)) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun GroupColorDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_group_color)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupColors.forEachIndexed { index, color ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(color)
                            .border(
                                width = if (index == current) 3.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { onPick(index) }
                            .semantics { contentDescription = "color_$index" }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
