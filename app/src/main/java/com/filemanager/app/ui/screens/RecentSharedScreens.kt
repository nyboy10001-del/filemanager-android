package com.filemanager.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.FileCategory
import com.filemanager.app.data.FileItem
import com.filemanager.app.data.OpKind
import com.filemanager.app.data.db.ShareLog
import com.filemanager.app.ui.components.AppBottomBar
import com.filemanager.app.ui.components.EmptyState
import com.filemanager.app.ui.components.FileRow
import com.filemanager.app.ui.components.GovChip
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.ItemSheet
import com.filemanager.app.ui.components.ListRow
import com.filemanager.app.ui.components.LoadingBlock
import com.filemanager.app.ui.components.NavTab
import com.filemanager.app.ui.components.SheetAction
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class FileListState(val loading: Boolean = true, val items: List<FileItem> = emptyList(), val error: String? = null)

// ---------------------------------------------------------------- Recent (screen 16)

class RecentViewModel(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow<FileCategory?>(null)
    private val tick = MutableStateFlow(0)
    private val _state = MutableStateFlow(FileListState())
    val state: StateFlow<FileListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(filter, tick) { f, _ -> f }.collectLatest { f ->
                _state.value = FileListState(loading = true)
                _state.value = try {
                    FileListState(loading = false, items = c.files.recent(f))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    FileListState(loading = false, error = e.message ?: "Couldn't load recent files.")
                }
            }
        }
        viewModelScope.launch { c.files.changes.collect { tick.value++ } }
    }

    fun setFilter(f: FileCategory?) { filter.value = f }
}

@Composable
fun RecentScreen(onTab: (NavTab) -> Unit, onShowInFolder: (String) -> Unit, onDetails: (String) -> Unit, onOperation: (OpKind, List<String>) -> Unit) {
    RequireStorageAccess {
        val container = LocalContainer.current
        val context = LocalContext.current
        val vm = rememberVm { RecentViewModel(container) }
        val state by vm.state.collectAsStateWithLifecycle()
        val filter by vm.filter.collectAsStateWithLifecycle()
        var sheet by remember { mutableStateOf<FileItem?>(null) }

        fun open(item: FileItem) {
            if (!Intents.open(context, item.path)) container.messages.tryEmit("No app on this device can open this file.")
        }

        Scaffold(
            topBar = { GovTopBar("Recent") },
            bottomBar = { AppBottomBar(NavTab.RECENT, onTab) }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GovChip("All", filter == null) { vm.setFilter(null) }
                    listOf(FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO, FileCategory.DOCUMENTS).forEach {
                        GovChip(it.label, filter == it) { vm.setFilter(it) }
                    }
                }
                HorizontalDivider()
                when {
                    state.loading -> LoadingBlock()
                    state.error != null -> EmptyState("Couldn't load recent files", state.error ?: "")
                    state.items.isEmpty() -> EmptyState("No recent files", "Files you add or change will appear here.")
                    else -> LazyColumn {
                        items(state.items, key = { it.path }) { item ->
                            FileRow(
                                item, "${Fmt.size(item.sizeBytes)} · ${Fmt.relative(item.modified)}",
                                selected = false, selecting = false,
                                onClick = { open(item) }, onLongClick = { sheet = item }, onMore = { sheet = item }
                            )
                        }
                    }
                }
            }
        }

        sheet?.let { item ->
            ItemSheet(
                item, Fmt.size(item.sizeBytes),
                listOf(
                    SheetAction("Open", Icons.Outlined.FileOpen) { open(item) },
                    SheetAction("Show in folder", Icons.Outlined.Folder) { onShowInFolder(File(item.path).parent.orEmpty()) },
                    SheetAction("Share", Icons.Outlined.Share) { onOperation(OpKind.SHARE, listOf(item.path)) },
                    SheetAction("Details", Icons.Outlined.Info) { onDetails(item.path) }
                ),
                onDismiss = { sheet = null }
            )
        }
    }
}

// ---------------------------------------------------------------- Shared (screen 17)

class SharedViewModel(private val c: AppContainer) : ViewModel() {
    private val _received = MutableStateFlow(FileListState())
    val received: StateFlow<FileListState> = _received.asStateFlow()
    val sent: StateFlow<List<ShareLog>> = c.shareDao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        load()
        viewModelScope.launch { c.files.changes.collect { load() } }
    }

    private fun load() {
        viewModelScope.launch {
            _received.value = try {
                FileListState(loading = false, items = c.files.received())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FileListState(loading = false, error = e.message ?: "Couldn't load received files.")
            }
        }
    }

    fun clearSent() { viewModelScope.launch { c.shareDao.clear() } }
}

@Composable
fun SharedScreen(onTab: (NavTab) -> Unit) {
    RequireStorageAccess {
        val container = LocalContainer.current
        val context = LocalContext.current
        val vm = rememberVm { SharedViewModel(container) }
        val received by vm.received.collectAsStateWithLifecycle()
        val sent by vm.sent.collectAsStateWithLifecycle()
        var tab by remember { mutableIntStateOf(0) }

        Scaffold(
            topBar = { GovTopBar("Shared") },
            bottomBar = { AppBottomBar(NavTab.SHARED, onTab) }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Received") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sent") })
                }
                if (tab == 0) {
                    when {
                        received.loading -> LoadingBlock()
                        received.items.isEmpty() -> EmptyState(
                            "No received files", "Files you receive by Bluetooth or download appear here."
                        )
                        else -> LazyColumn {
                            items(received.items, key = { it.path }) { item ->
                                FileRow(
                                    item, "${Fmt.size(item.sizeBytes)} · ${Fmt.relative(item.modified)}",
                                    selected = false, selecting = false,
                                    onClick = {
                                        if (!Intents.open(context, item.path))
                                            container.messages.tryEmit("No app on this device can open this file.")
                                    },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                } else {
                    if (sent.isEmpty()) {
                        EmptyState("Nothing shared yet", "Files you share from this app are listed here.")
                    } else {
                        LazyColumn {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = vm::clearSent) { Text("Clear history") }
                                }
                            }
                            items(sent, key = { it.id }) { log ->
                                val names = log.names.split("\n")
                                val title = if (names.size == 1) names.first() else "${names.first()} and ${names.size - 1} more"
                                ListRow(
                                    title = title,
                                    subtitle = "Shared with ${log.target} · ${Fmt.relative(log.timestamp)}",
                                    onClick = {
                                        val first = log.paths.split("\n").first()
                                        if (!File(first).exists()) container.messages.tryEmit("This file has been moved or deleted.")
                                        else if (!Intents.open(context, first)) container.messages.tryEmit("No app on this device can open this file.")
                                    },
                                    trailing = {}
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
