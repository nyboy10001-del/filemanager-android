package com.filemanager.app.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.FileCategory
import com.filemanager.app.data.StorageStats
import com.filemanager.app.ui.components.AppBottomBar
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.ListRow
import com.filemanager.app.ui.components.NavTab
import com.filemanager.app.ui.components.SectionHeading
import com.filemanager.app.ui.components.StorageRing
import com.filemanager.app.ui.components.govClickable
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.ui.components.tint
import com.filemanager.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeFolder(val title: String, val path: String, val count: Int?, val size: Long?)
data class HomeUi(val stats: StorageStats? = null, val folders: List<HomeFolder> = emptyList())

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()
    private var job: Job? = null

    private val specs = listOf(
        "Documents" to Environment.DIRECTORY_DOCUMENTS,
        "Photos" to Environment.DIRECTORY_DCIM,
        "Videos" to Environment.DIRECTORY_MOVIES,
        "Music" to Environment.DIRECTORY_MUSIC,
        "Downloads" to Environment.DIRECTORY_DOWNLOADS
    )

    init {
        refresh()
        viewModelScope.launch { c.files.changes.collect { refresh() } }
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val base = withContext(Dispatchers.IO) {
                specs.map { (title, type) ->
                    val dir = c.files.publicDir(type).also { it.mkdirs() }
                    HomeFolder(title, dir.absolutePath, null, null)
                }
            }
            _ui.update { it.copy(stats = c.files.storageStats(), folders = base) }
            base.forEach { folder ->
                launch {
                    val (count, size) = c.files.folderSummary(File(folder.path))
                    _ui.update { state ->
                        state.copy(folders = state.folders.map {
                            if (it.path == folder.path) it.copy(count = count, size = size) else it
                        })
                    }
                }
            }
        }
    }
}

/** Screen 5 */
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onStorage: () -> Unit,
    onHelp: () -> Unit,
    onTab: (NavTab) -> Unit
) {
    RequireStorageAccess {
        val container = LocalContainer.current
        val vm = rememberVm { HomeViewModel(container) }
        val ui by vm.ui.collectAsStateWithLifecycle()
        var menu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                GovTopBar("Files", actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.refresh() })
                            DropdownMenuItem(text = { Text("Help and feedback") }, onClick = { menu = false; onHelp() })
                        }
                    }
                })
            },
            bottomBar = { AppBottomBar(NavTab.FILES, onTab) }
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    ui.stats?.let { StorageCard(it, onStorage) }
                }
                item { SectionHeading("Folders") }
                items(ui.folders, key = { it.path }) { f ->
                    val subtitle = if (f.count == null) "Counting items…" else
                        Fmt.items(f.count) + (f.size?.let { " · " + Fmt.size(it) } ?: "")
                    ListRow(
                        title = f.title,
                        icon = Icons.Filled.Folder,
                        iconTint = FileCategory.FOLDER.tint(),
                        subtitle = subtitle,
                        onClick = { onOpenFolder(f.path) }
                    )
                    HorizontalDivider()
                }
                item {
                    ListRow(
                        title = "All files",
                        subtitle = "Browse everything on your device",
                        icon = Icons.Outlined.Storage,
                        onClick = { onOpenFolder(container.files.root.absolutePath) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageCard(stats: StorageStats, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .govClickable(onClick, role = Role.Button)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StorageRing(stats.usedFraction, diameter = 64.dp, stroke = 8.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Internal storage", style = MaterialTheme.typography.titleSmall)
            Text(
                "${Fmt.size(stats.usedBytes)} of ${Fmt.size(stats.totalBytes)} used",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Storage details (screen 6)

class StorageViewModel(private val c: AppContainer) : ViewModel() {
    val stats: StorageStats = c.files.storageStats()
    private val _breakdown = MutableStateFlow<Map<FileCategory, Long>?>(null)
    val breakdown: StateFlow<Map<FileCategory, Long>?> = _breakdown.asStateFlow()

    init {
        viewModelScope.launch { _breakdown.value = c.files.breakdown() }
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm = rememberVm { StorageViewModel(container) }
    val breakdown by vm.breakdown.collectAsStateWithLifecycle()
    val stats = vm.stats

    Scaffold(topBar = { GovTopBar("Storage", onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageRing(stats.usedFraction, diameter = 120.dp, stroke = 14.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Internal storage", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${Fmt.size(stats.usedBytes)} of ${Fmt.size(stats.totalBytes)} used",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            val shown = listOf(FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO, FileCategory.DOCUMENTS, FileCategory.APPS)
            val b = breakdown
            if (b == null) {
                Text("Calculating what's using your storage…", style = MaterialTheme.typography.bodyMedium)
            } else {
                val known = shown.sumOf { b[it] ?: 0L }
                val other = (stats.usedBytes - known).coerceAtLeast(0L)
                // Segmented bar. Every segment is also listed in text below.
                Row(Modifier.fillMaxWidth().height(14.dp)) {
                    shown.forEach { cat ->
                        val v = b[cat] ?: 0L
                        if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxSize().background(cat.tint()))
                    }
                    if (other > 0) Box(Modifier.weight(other.toFloat()).fillMaxSize().background(FileCategory.OTHER.tint()))
                    if (stats.freeBytes > 0) Box(
                        Modifier.weight(stats.freeBytes.toFloat()).fillMaxSize()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                Spacer(Modifier.height(12.dp))
                shown.forEach { cat -> CategoryRow(cat.label, b[cat] ?: 0L, cat.tint()) }
                CategoryRow("Other", other, FileCategory.OTHER.tint())
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .padding(16.dp)
            ) {
                Text("Free space", style = MaterialTheme.typography.titleSmall)
                Text("${Fmt.size(stats.freeBytes)} available", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, bytes: Long, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(Fmt.size(bytes), style = MaterialTheme.typography.bodyLarge)
    }
}
