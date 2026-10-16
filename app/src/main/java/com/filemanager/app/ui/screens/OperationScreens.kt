package com.filemanager.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.CompressionLevel
import com.filemanager.app.data.FileItem
import com.filemanager.app.data.OpKind
import com.filemanager.app.data.SortOrder
import com.filemanager.app.data.db.ShareLog
import com.filemanager.app.ui.components.ErrorSummary
import com.filemanager.app.ui.components.GovTextField
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.InsetText
import com.filemanager.app.ui.components.ListRow
import com.filemanager.app.ui.components.PrimaryButton
import com.filemanager.app.ui.components.SecondaryButton
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// ---------------------------------------------------------------- Create folder (screen 11)

@Composable
fun CreateFolderScreen(parentPath: String, onClose: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        scope.launch {
            busy = true
            val r = container.files.createFolder(parentPath, name)
            busy = false
            r.fold(
                onSuccess = { container.messages.tryEmit("Created folder '${it.name}'"); onClose() },
                onFailure = { error = it.message ?: "Couldn't create the folder." }
            )
        }
    }

    Scaffold(topBar = { GovTopBar("New folder", onBack = onClose) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            error?.let { ErrorSummary(listOf(it)) }
            GovTextField(
                label = "Folder name", value = name,
                onValueChange = { name = it; error = null },
                errorText = error, onImeAction = ::submit
            )
            GovTextField(
                label = "Location", value = Fmt.pretty(parentPath),
                onValueChange = {}, readOnly = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", onClose, Modifier.weight(1f))
                PrimaryButton("Create", ::submit, Modifier.weight(1f), enabled = !busy)
            }
        }
    }
}

// ---------------------------------------------------------------- Copy / move (screen 12)

class DestinationViewModel(private val c: AppContainer) : ViewModel() {
    private val _stack = MutableStateFlow<List<String>>(emptyList())
    val stack: StateFlow<List<String>> = _stack.asStateFlow()
    private val _folders = MutableStateFlow<List<FileItem>>(emptyList())
    val folders: StateFlow<List<FileItem>> = _folders.asStateFlow()

    init {
        viewModelScope.launch {
            _stack.collectLatest { s ->
                _folders.value = if (s.isEmpty()) emptyList() else
                    runCatching { c.files.list(s.last(), SortOrder.NAME, false).filter { it.isDirectory } }
                        .getOrDefault(emptyList())
            }
        }
    }

    fun open(path: String) { _stack.update { it + path } }

    /** Returns false when already at the top-level list. */
    fun back(): Boolean {
        if (_stack.value.isEmpty()) return false
        _stack.update { it.dropLast(1) }
        return true
    }
}

@Composable
fun DestinationScreen(kind: OpKind, onClose: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val vm = rememberVm { DestinationViewModel(container) }
    val stack by vm.stack.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val paths = remember { container.pending.value?.paths.orEmpty() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val verb = if (kind == OpKind.MOVE) "Move" else "Copy"
    val current = stack.lastOrNull()

    BackHandler(enabled = stack.isNotEmpty()) { vm.back() }

    fun run() {
        val dest = current ?: return
        busy = true
        val job = container.background {
            if (kind == OpKind.MOVE) container.files.move(paths, dest) else container.files.copy(paths, dest)
        }
        scope.launch {
            val r = job.await()
            busy = false
            r.fold(
                onSuccess = {
                    container.messages.tryEmit("${if (kind == OpKind.MOVE) "Moved" else "Copied"} ${Fmt.items(it)} to ${File(dest).name}")
                    onClose()
                },
                onFailure = { error = it.message ?: "Something went wrong." }
            )
        }
    }

    Scaffold(
        topBar = { GovTopBar("$verb to", onBack = { if (!vm.back()) onClose() }) },
        bottomBar = {
            if (current != null) {
                Column {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Cancel", onClose, Modifier.weight(1f))
                        PrimaryButton("$verb here", ::run, Modifier.weight(1f), enabled = !busy)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            error?.let { ErrorSummary(listOf(it), Modifier.padding(16.dp)) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (current == null) {
                LazyColumn {
                    items(container.files.quickLocations.filter { it.second.exists() || it.first == "Internal storage" }, key = { it.first }) { (label, dir) ->
                        ListRow(
                            title = label, icon = Icons.Filled.Folder,
                            iconTint = com.filemanager.app.ui.theme.Gov.Folder,
                            onClick = { error = null; vm.open(dir.absolutePath) }
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                Text(
                    Fmt.breadcrumb(current), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                if (folders.isEmpty()) {
                    Text(
                        "This folder has no subfolders. You can ${verb.lowercase()} here.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
                    )
                }
                LazyColumn {
                    items(folders, key = { it.path }) { f ->
                        ListRow(
                            title = f.name, icon = Icons.Filled.Folder,
                            iconTint = com.filemanager.app.ui.theme.Gov.Folder,
                            onClick = { error = null; vm.open(f.path) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Compress (screen 13)

@Composable
fun CompressScreen(onClose: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val paths = remember { container.pending.value?.paths.orEmpty() }
    val defaultName = remember {
        val first = File(paths.firstOrNull() ?: "Archive")
        if (paths.size == 1) first.nameWithoutExtension.ifEmpty { first.name } + ".zip"
        else (first.parentFile?.name ?: "Archive") + ".zip"
    }
    var name by remember { mutableStateOf(defaultName) }
    var level by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run() {
        if (busy || paths.isEmpty()) return
        busy = true
        val job = container.background { container.files.compress(paths, name, level) }
        scope.launch {
            val r = job.await()
            busy = false
            r.fold(
                onSuccess = { container.messages.tryEmit("Created ${it.name}"); onClose() },
                onFailure = { error = it.message ?: "Couldn't create the archive." }
            )
        }
    }

    Scaffold(topBar = { GovTopBar("Compress", onBack = onClose) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            error?.let { ErrorSummary(listOf(it)) }
            GovTextField(
                label = "Archive name", value = name,
                onValueChange = { name = it; error = null },
                hint = "The .zip file is saved next to the original ${if (paths.size == 1) "item" else "items"}.",
                errorText = error
            )
            Column {
                Text("Compression level", style = MaterialTheme.typography.titleSmall)
                CompressionLevel.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .selectable(selected = level == option, onClick = { level = option }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = level == option, onClick = null)
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", onClose, Modifier.weight(1f), enabled = !busy)
                PrimaryButton("Compress", ::run, Modifier.weight(1f), enabled = !busy)
            }
        }
    }
}

private fun Modifier.padding(vertical = 4.dp): Modifier = this.then(Modifier.padding(vertical = 4.dp))

// ---------------------------------------------------------------- Share (screen 14)

private data class ShareTarget(val label: String, val pkg: String?, val icon: ImageVector)

private val shareTargets = listOf(
    ShareTarget("Nearby Share", "com.google.android.gms", Icons.Outlined.NearMe),
    ShareTarget("Google Drive", "com.google.android.apps.docs", Icons.Outlined.AddToDrive),
    ShareTarget("Gmail", "com.google.android.gm", Icons.Outlined.Email),
    ShareTarget("Bluetooth", "com.android.bluetooth", Icons.Outlined.Bluetooth),
    ShareTarget("Messages", "com.google.android.apps.messaging", Icons.Outlined.Sms),
    ShareTarget("More", null, Icons.Outlined.MoreHoriz)
)

@Composable
fun ShareScreen(onClose: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paths = remember { container.pending.value?.paths.orEmpty() }
    val files = remember { paths.map(::File).filter { it.isFile } }
    val skippedFolders = paths.size - files.size
    var error by remember { mutableStateOf<String?>(null) }

    fun share(target: ShareTarget) {
        if (files.isEmpty()) return
        val intent = Intents.shareIntent(context, files)
        try {
            if (target.pkg == null) {
                context.startActivity(Intent.createChooser(intent, "Share ${Fmt.items(files.size)}"))
            } else {
                intent.setPackage(target.pkg)
                context.startActivity(intent)
            }
            scope.launch {
                container.shareDao.insert(
                    ShareLog(
                        names = files.joinToString("\n") { it.name },
                        paths = files.joinToString("\n") { it.absolutePath },
                        target = target.label,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            onClose()
        } catch (e: ActivityNotFoundException) {
            error = "${target.label} isn't installed on this device. Choose 'More' to see other apps."
        } catch (e: SecurityException) {
            error = "Couldn't share these files with ${target.label}."
        }
    }

    Scaffold(topBar = { GovTopBar("Share ${Fmt.items(files.size)}", onBack = onClose) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            error?.let { ErrorSummary(listOf(it), Modifier.padding(16.dp)) }
            if (skippedFolders > 0) {
                InsetText(
                    if (files.isEmpty()) "Folders can't be shared directly. Compress the folder first, then share the .zip file."
                    else "${Fmt.items(skippedFolders)} will be left out because folders can't be shared directly. Compress them first.",
                    Modifier.padding(16.dp)
                )
            }
            if (files.isNotEmpty()) {
                shareTargets.forEach { t ->
                    ListRow(title = t.label, icon = t.icon, onClick = { error = null; share(t) }, trailing = {})
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Cancel", onClose, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
