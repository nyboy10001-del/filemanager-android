package com.filemanager.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.AppSettings
import com.filemanager.app.data.FileItem
import com.filemanager.app.data.OpKind
import com.filemanager.app.data.SortOrder
import com.filemanager.app.ui.components.AppBottomBar
import com.filemanager.app.ui.components.EmptyState
import com.filemanager.app.ui.components.FileRow
import com.filemanager.app.ui.components.GovTextField
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.ItemSheet
import com.filemanager.app.ui.components.LoadingBlock
import com.filemanager.app.ui.components.NavTab
import com.filemanager.app.ui.components.SheetAction
import com.filemanager.app.ui.components.govClickable
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUi(
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val selected: Set<String> = emptySet()
)

class BrowserViewModel(private val c: AppContainer, val path: String) : ViewModel() {
    private val _ui = MutableStateFlow(BrowserUi())
    val ui: StateFlow<BrowserUi> = _ui.asStateFlow()
    private val tick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(c.settings.settings, tick) { s, _ -> s }.collectLatest { load(it) }
        }
        viewModelScope.launch {
            c.files.changes.collect {
                _ui.update { it.copy(selected = emptySet()) }
                tick.update { it + 1 }
            }
        }
    }

    private suspend fun load(s: AppSettings) {
        try {
            val items = c.files.list(path, s.sort, s.showHidden)
            _ui.update { st ->
                val live = items.map { it.path }.toSet()
                st.copy(items = items, loading = false, error = null, selected = st.selected.filter { it in live }.toSet())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.message ?: "This folder can't be opened.") }
        }
    }

    fun toggle(p: String) {
        _ui.update { st ->
            val s = st.selected.toMutableSet()
            if (!s.add(p)) s.remove(p)
            st.copy(selected = s)
        }
    }

    fun selectAll() { _ui.update { st -> st.copy(selected = st.items.map { it.path }.toSet()) } }
    fun clearSelection() { _ui.update { it.copy(selected = emptySet()) } }

    fun delete(paths: List<String>) {
        viewModelScope.launch {
            val r = c.files.delete(paths)
            c.messages.tryEmit(r.fold({ "Deleted ${Fmt.items(it)}" }, { it.message ?: "Couldn't delete." }))
        }
    }

    fun rename(p: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val r = c.files.rename(p, name)
            onResult(r.exceptionOrNull()?.message)
            if (r.isSuccess) c.messages.tryEmit("Renamed to ${name.trim()}")
        }
    }
}

class BrowserActions(
    val onBack: () -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onSearch: () -> Unit,
    val onDetails: (String) -> Unit,
    val onNewFolder: (String) -> Unit,
    val onOperation: (OpKind, List<String>) -> Unit,
    val onTab: (NavTab) -> Unit
)

/** Screens 8, 9 and 10: folder view, selection mode and the file actions sheet. */
@Composable
fun BrowserScreen(path: String, actions: BrowserActions) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val vm = rememberVm(key = path) { BrowserViewModel(container, path) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val selecting = ui.selected.isNotEmpty()
    BackHandler(enabled = selecting) { vm.clearSelection() }

    var menuOpen by remember { mutableStateOf(false) }
    var sheetItem by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }

    val title = if (path == container.files.root.absolutePath) "Internal storage" else java.io.File(path).name

    fun openItem(item: FileItem) {
        if (item.isDirectory) actions.onOpenFolder(item.path)
        else if (!Intents.open(context, item.path)) container.messages.tryEmit("No app on this device can open this file.")
    }

    fun requestDelete(paths: List<String>) {
        if (settings.confirmDelete) deleteTargets = paths else vm.delete(paths)
    }

    Scaffold(
        topBar = {
            if (selecting) {
                GovTopBar(
                    title = "${ui.selected.size} selected",
                    onBack = vm::clearSelection,
                    navIcon = Icons.Outlined.Close,
                    navDescription = "Clear selection",
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, contentDescription = "Select all") }
                    }
                )
            } else {
                GovTopBar(title, onBack = actions.onBack, actions = {
                    IconButton(onClick = actions.onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("New folder") },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                                onClick = { menuOpen = false; actions.onNewFolder(path) }
                            )
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                                onClick = { menuOpen = false; vm.selectAll() }
                            )
                            HorizontalDivider()
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text("Sort by ${order.label.lowercase()}") },
                                    leadingIcon = {
                                        if (settings.sort == order) Icon(Icons.Filled.Check, contentDescription = "Selected")
                                    },
                                    onClick = { menuOpen = false; scope.launch { container.settings.setSort(order) } }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (settings.showHidden) "Hide hidden files" else "Show hidden files") },
                                onClick = { menuOpen = false; scope.launch { container.settings.setShowHidden(!settings.showHidden) } }
                            )
                        }
                    }
                })
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    count = ui.selected.size,
                    onShare = { actions.onOperation(OpKind.SHARE, ui.selected.toList()) },
                    onMove = { actions.onOperation(OpKind.MOVE, ui.selected.toList()) },
                    onDelete = { requestDelete(ui.selected.toList()) },
                    onCopy = { actions.onOperation(OpKind.COPY, ui.selected.toList()) },
                    onCompress = { actions.onOperation(OpKind.COMPRESS, ui.selected.toList()) },
                    onRename = if (ui.selected.size == 1) {
                        { renameItem = ui.items.firstOrNull { it.path == ui.selected.first() } }
                    } else null,
                    onDetails = if (ui.selected.size == 1) {
                        { actions.onDetails(ui.selected.first()) }
                    } else null
                )
            } else {
                AppBottomBar(NavTab.FILES, actions.onTab)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                com.filemanager.app.util.Fmt.breadcrumb(path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            when {
                ui.loading -> LoadingBlock()
                ui.error != null -> EmptyState("Can't open this folder", ui.error ?: "")
                ui.items.isEmpty() -> EmptyState(
                    "This folder is empty", "Create a folder or copy files into it.",
                    actionLabel = "New folder", onAction = { actions.onNewFolder(path) }
                )
                else -> LazyColumn {
                    items(ui.items, key = { it.path }) { item ->
                        val sub = if (item.isDirectory) "${Fmt.items(item.childCount)} · ${Fmt.relative(item.modified)}"
                        else "${Fmt.size(item.sizeBytes)} · Modified ${Fmt.relative(item.modified)}"
                        FileRow(
                            item = item,
                            subtitle = sub,
                            selected = item.path in ui.selected,
                            selecting = selecting,
                            onClick = { if (selecting) vm.toggle(item.path) else openItem(item) },
                            onLongClick = { vm.toggle(item.path) },
                            onMore = { sheetItem = item }
                        )
                    }
                }
            }
        }
    }

    sheetItem?.let { item ->
        val one = listOf(item.path)
        ItemSheet(
            item = item,
            subtitle = if (item.isDirectory) Fmt.items(item.childCount) else Fmt.size(item.sizeBytes),
            actions = listOf(
                SheetAction("Open", Icons.Outlined.FileOpen) { openItem(item) },
                SheetAction("Share", Icons.Outlined.Share) { actions.onOperation(OpKind.SHARE, one) },
                SheetAction("Move", Icons.Outlined.DriveFileMove) { actions.onOperation(OpKind.MOVE, one) },
                SheetAction("Copy", Icons.Outlined.ContentCopy) { actions.onOperation(OpKind.COPY, one) },
                SheetAction("Rename", Icons.Outlined.Edit) { renameItem = item },
                SheetAction("Compress", Icons.Outlined.FolderZip) { actions.onOperation(OpKind.COMPRESS, one) },
                SheetAction("Details", Icons.Outlined.Info) { actions.onDetails(item.path) },
                SheetAction("Delete", Icons.Outlined.Delete, destructive = true) { requestDelete(one) }
            ),
            onDismiss = { sheetItem = null }
        )
    }

    renameItem?.let { item ->
        RenameDialog(item.name, onDismiss = { renameItem = null }) { name, setError ->
            vm.rename(item.path, name) { err -> if (err == null) renameItem = null else setError(err) }
        }
    }

    deleteTargets?.let { paths ->
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text("Delete ${Fmt.items(paths.size)}?") },
            text = { Text("This can't be undone. The ${if (paths.size == 1) "item" else "items"} will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(paths); deleteTargets = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTargets = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, setError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            GovTextField(
                label = "New name", value = name,
                onValueChange = { name = it; error = null },
                errorText = error,
                onImeAction = { onConfirm(name) { error = it } }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) { error = it } }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCompress: () -> Unit,
    onRename: (() -> Unit)?,
    onDetails: (() -> Unit)?
) {
    var more by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BarAction("Share", Icons.Outlined.Share, onShare)
                BarAction("Move", Icons.Outlined.DriveFileMove, onMove)
                BarAction("Delete", Icons.Outlined.Delete, onDelete)
                Box {
                    BarAction("More", Icons.Outlined.MoreVert) { more = true }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Copy") }, onClick = { more = false; onCopy() })
                        DropdownMenuItem(text = { Text("Compress") }, onClick = { more = false; onCompress() })
                        if (onRename != null) DropdownMenuItem(text = { Text("Rename") }, onClick = { more = false; onRename() })
                        if (onDetails != null) DropdownMenuItem(text = { Text("Details") }, onClick = { more = false; onDetails() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .heightIn(min = 56.dp)
            .govClickable(onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
