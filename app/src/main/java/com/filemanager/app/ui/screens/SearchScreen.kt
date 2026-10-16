package com.filemanager.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.filemanager.app.AppContainer
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.FileItem
import com.filemanager.app.data.SearchFilter
import com.filemanager.app.data.db.RecentSearch
import com.filemanager.app.ui.components.EmptyState
import com.filemanager.app.ui.components.FileRow
import com.filemanager.app.ui.components.GovChip
import com.filemanager.app.ui.components.ListRow
import com.filemanager.app.ui.components.LoadingBlock
import com.filemanager.app.ui.components.SectionHeading
import com.filemanager.app.ui.components.rememberVm
import com.filemanager.app.util.Fmt
import com.filemanager.app.util.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val items: List<FileItem>) : SearchState
    data class Failed(val message: String) : SearchState
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow<SearchFilter?>(null)
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val recents: StateFlow<List<RecentSearch>> = c.searchDao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(query, filter) { q, f -> q to f }
                .debounce(250)
                .collectLatest { (q, f) ->
                    if (q.isBlank() && f == null) {
                        _state.value = SearchState.Idle
                    } else {
                        _state.value = SearchState.Loading
                        try {
                            val hidden = c.settings.settings.first().showHidden
                            _state.value = SearchState.Done(c.files.search(q, f, hidden))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _state.value = SearchState.Failed(e.message ?: "Search failed.")
                        }
                    }
                }
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun toggleFilter(f: SearchFilter) { filter.value = if (filter.value == f) null else f }

    fun commit() {
        val q = query.value.trim()
        if (q.isNotEmpty()) viewModelScope.launch { c.searchDao.upsert(RecentSearch(q, System.currentTimeMillis())) }
    }

    fun deleteRecent(term: String) { viewModelScope.launch { c.searchDao.delete(term) } }
    fun clearRecents() { viewModelScope.launch { c.searchDao.clear() } }
}

/** Screen 7 */
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val vm = rememberVm { SearchViewModel(container) }
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search files and folders") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            SearchFilter.entries.forEach { f -> GovChip(f.label, selected = filter == f, onClick = { vm.toggleFilter(f) }) }
        }
        HorizontalDivider()

        when (val s = state) {
            SearchState.Idle -> LazyColumn(Modifier.fillMaxSize()) {
                if (recents.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeading("Recent searches", Modifier.weight(1f))
                            TextButton(onClick = vm::clearRecents) { Text("Clear all") }
                        }
                    }
                    items(recents, key = { it.term }) { r ->
                        ListRow(
                            title = r.term,
                            icon = Icons.Outlined.History,
                            onClick = { vm.setQuery(r.term) },
                            trailing = {
                                IconButton(onClick = { vm.deleteRecent(r.term) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove ${r.term} from recent searches")
                                }
                            }
                        )
                    }
                } else {
                    item {
                        Text(
                            "Type a name to search, or choose a filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            SearchState.Loading -> LoadingBlock()
            is SearchState.Failed -> EmptyState("Search didn't work", s.message)
            is SearchState.Done -> if (s.items.isEmpty()) {
                EmptyState("No results", "Nothing matches '${query.trim()}'. Check the spelling or try a different filter.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(s.items, key = { it.path }) { item ->
                        val where = Fmt.pretty(java.io.File(item.path).parent.orEmpty())
                        FileRow(
                            item = item,
                            subtitle = if (item.isDirectory) where else "${Fmt.size(item.sizeBytes)} · $where",
                            selected = false, selecting = false,
                            onClick = {
                                vm.commit()
                                if (item.isDirectory) onOpenFolder(item.path)
                                else if (!Intents.open(context, item.path))
                                    container.messages.tryEmit("No app on this device can open this file.")
                            },
                            onLongClick = {}
                        )
                    }
                }
            }
        }
    }
}
