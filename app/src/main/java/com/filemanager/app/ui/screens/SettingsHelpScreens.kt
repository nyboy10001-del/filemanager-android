package com.filemanager.app.ui.screens

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filemanager.app.BuildConfig
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.AppSettings
import com.filemanager.app.data.SortOrder
import com.filemanager.app.data.ThemeMode
import com.filemanager.app.ui.components.AppBottomBar
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.ListRow
import com.filemanager.app.ui.components.NavTab
import com.filemanager.app.ui.components.SectionHeading
import com.filemanager.app.ui.components.govClickable
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "support@example.com" // TODO: replace with your real address

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = o == selected, onClick = { onSelect(o); onDismiss() }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Text(label(o), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        title = title, subtitle = subtitle,
        onClick = { onChange(!checked) }, role = Role.Switch,
        trailing = { Switch(checked = checked, onCheckedChange = null) }
    )
}

/** Screen 18. Account, cloud and notification rows from the mockup are left out: this app has no server. */
@Composable
fun SettingsScreen(onTab: (NavTab) -> Unit, onStorage: () -> Unit, onHelp: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val stats = remember { container.files.storageStats() }
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { GovTopBar("Settings") },
        bottomBar = { AppBottomBar(NavTab.MORE, onTab) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeading("Device")
            ListRow(
                "Storage", icon = Icons.Outlined.Storage,
                subtitle = "${Fmt.size(stats.usedBytes)} / ${Fmt.size(stats.totalBytes)}", onClick = onStorage
            )
            HorizontalDivider()
            SectionHeading("Display")
            ListRow("Appearance", icon = Icons.Outlined.DarkMode, subtitle = settings.theme.label, onClick = { dialog = "theme" })
            ListRow("Sort files by", icon = Icons.Outlined.SortByAlpha, subtitle = settings.sort.label, onClick = { dialog = "sort" })
            SwitchRow("Show hidden files", "Files and folders that start with a dot", settings.showHidden) {
                scope.launch { container.settings.setShowHidden(it) }
            }
            HorizontalDivider()
            SectionHeading("Safety")
            SwitchRow("Confirm before deleting", "Ask first. Deleted items can't be recovered.", settings.confirmDelete) {
                scope.launch { container.settings.setConfirmDelete(it) }
            }
            HorizontalDivider()
            SectionHeading("Privacy")
            ListRow("Clear search history", icon = Icons.Outlined.History, onClick = {
                scope.launch { container.searchDao.clear(); container.messages.tryEmit("Search history cleared") }
            }, trailing = {})
            ListRow("Clear sharing history", icon = Icons.Outlined.Delete, onClick = {
                scope.launch { container.shareDao.clear(); container.messages.tryEmit("Sharing history cleared") }
            }, trailing = {})
            HorizontalDivider()
            SectionHeading("Support")
            ListRow("Help and feedback", icon = Icons.Outlined.SupportAgent, onClick = onHelp)
            ListRow("About", icon = Icons.Outlined.Info, onClick = { dialog = "about" })
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Appearance", ThemeMode.entries, settings.theme, { it.label },
            { scope.launch { container.settings.setTheme(it) } }, { dialog = null })
        "sort" -> ChoiceDialog("Sort files by", SortOrder.entries, settings.sort, { it.label },
            { scope.launch { container.settings.setSort(it) } }, { dialog = null })
        "about" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("About File Manager") },
            text = { Text("Version ${BuildConfig.VERSION_NAME}\n\nYour files stay on your device. This app has no account and sends no data anywhere.") },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } }
        )
    }
}

// ---------------------------------------------------------------- Help (screen 20)

private val faqs = listOf(
    "Why does this app need access to all files?" to
        "A file manager has to see every folder to let you browse, move and delete files. Your files are never uploaded.",
    "How do I move or copy files?" to
        "Press and hold a file to select it, choose more files if you need to, then use Move or More > Copy. Pick the destination folder and confirm.",
    "Can I get back a file I deleted?" to
        "No. Deleted files are removed straight away. Keep 'Confirm before deleting' turned on in Settings to avoid mistakes.",
    "How do I share more than one file?" to
        "Select the files, then choose Share. Folders can't be shared directly, so compress them into a .zip file first.",
    "Why is my storage total different from my phone's Settings?" to
        "This app can only count files it can see. System files and app data are grouped under 'Other'."
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    var expanded by remember { mutableStateOf<Int?>(null) }
    var doc by remember { mutableStateOf<String?>(null) }

    fun email(subject: String, body: String) {
        try { context.startActivity(Intents.email(SUPPORT_EMAIL, subject, body)) }
        catch (e: ActivityNotFoundException) { container.messages.tryEmit("No email app found. Write to $SUPPORT_EMAIL.") }
    }

    Scaffold(topBar = { GovTopBar("Help and feedback", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeading("Frequently asked questions")
            faqs.forEachIndexed { i, (q, a) ->
                val open = expanded == i
                Column {
                    ListRow(
                        title = q,
                        onClick = { expanded = if (open) null else i },
                        trailing = { Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Collapse" else "Expand") }
                    )
                    if (open) Text(a, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
                    HorizontalDivider()
                }
            }
            SectionHeading("Get in touch")
            ListRow("Contact us", icon = Icons.Outlined.Email, subtitle = "Get in touch with our team",
                onClick = { email("File Manager enquiry", "") })
            ListRow("Report a problem", icon = Icons.Outlined.BugReport, subtitle = "Send feedback or a bug report",
                onClick = {
                    email(
                        "File Manager problem report",
                        "Describe what happened:\n\n\n---\nApp ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                })
            SectionHeading("Legal")
            ListRow("Privacy policy", icon = Icons.Outlined.PrivacyTip, subtitle = "How we handle your data", onClick = { doc = "privacy" })
            ListRow("Terms of service", icon = Icons.Outlined.Gavel, subtitle = "Our terms and conditions", onClick = { doc = "terms" })
        }
    }

    doc?.let { which ->
        val (title, text) = if (which == "privacy") {
            "Privacy policy" to "File Manager works on your device. It doesn't collect your files or personal information, and it doesn't send anything to a server.\n\nSearch history and sharing history are stored only on your device. You can clear them at any time in Settings."
        } else {
            "Terms of service" to "You use this app at your own risk. Deleting, moving or compressing files can't always be undone, so check before you confirm.\n\nReplace this text with your own terms before you publish the app."
        }
        AlertDialog(
            onDismissRequest = { doc = null },
            title = { Text(title) },
            text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { doc = null }) { Text("Close") } }
        )
    }
}
