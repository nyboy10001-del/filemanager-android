package com.filemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.FileDetails
import com.filemanager.app.ui.components.EmptyState
import com.filemanager.app.ui.components.FileThumb
import com.filemanager.app.ui.components.GovTopBar
import com.filemanager.app.ui.components.LoadingBlock
import com.filemanager.app.ui.components.PrimaryButton
import com.filemanager.app.ui.components.SecondaryButton
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Ready(val details: FileDetails) : DetailsState
    data class Failed(val message: String) : DetailsState
}

class DetailsViewModel(c: AppContainer, path: String) : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                DetailsState.Ready(c.files.details(path))
            } catch (e: Exception) {
                DetailsState.Failed(e.message ?: "Couldn't read details.")
            }
        }
    }
}

/** Screen 15 */
@Composable
fun DetailsScreen(path: String, onClose: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val vm = rememberVm(key = path) { DetailsViewModel(container, path) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { GovTopBar("File details", onBack = onClose) }) { padding ->
        when (val s = state) {
            DetailsState.Loading -> LoadingBlock(Modifier.padding(padding))
            is DetailsState.Failed -> EmptyState("Can't show details", s.message, Modifier.padding(padding))
            is DetailsState.Ready -> {
                val d = s.details
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FileThumb(d.item, boxSize = 88.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(d.item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Fmt.size(d.totalSizeBytes)} · ${d.typeLabel}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    SummaryRow("Location", Fmt.pretty(d.parentPath))
                    SummaryRow("Modified", Fmt.date(d.item.modified))
                    if (d.item.isDirectory) SummaryRow("Contains", Fmt.items(d.item.childCount))
                    d.dimensions?.let { SummaryRow("Dimensions", it) }
                    SummaryRow("Full path", d.item.path)
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!d.item.isDirectory) {
                            PrimaryButton("Open", {
                                if (!Intents.open(context, d.item.path))
                                    container.messages.tryEmit("No app on this device can open this file.")
                            }, Modifier.fillMaxWidth())
                        }
                        SecondaryButton("Close", onClose, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** GOV.UK summary-list row: label on the left, value on the right. */
@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(0.35f))
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
        }
        HorizontalDivider()
    }
}
