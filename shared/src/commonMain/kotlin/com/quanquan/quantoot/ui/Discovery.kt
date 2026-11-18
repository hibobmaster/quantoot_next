package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.LoginPrompt
import com.quanquan.quantoot.data.ApiException
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*

internal class DiscoveryState(private val ui: UiContext) {
    val list = LazyListState()
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf(SearchResults())
        private set
    var busy by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set

    private var started = false
    private var offset = 0
    private var request: Job? = null
    private var trends: SearchResults? = null

    fun open() {
        if (!started) submit()
    }

    fun edit(value: String) {
        query = value
        if (value.isEmpty() && submitted.isNotEmpty()) submit()
    }

    fun submit() {
        request?.cancel()
        started = true
        submitted = query.trim()
        offset = 0
        hasMore = false
        busy = false
        refreshing = false
        error = null
        list.requestScrollToItem(0)
        val cached = trends.takeIf { submitted.isEmpty() }
        results = cached ?: SearchResults()
        if (cached == null) load()
    }

    fun refresh() {
        if (busy) return
        offset = 0
        hasMore = false
        refreshing = true
        load()
    }

    fun load() {
        if (busy) return
        busy = true
        error = null
        val term = submitted
        val page = offset
        request = ui.scope.launch {
            try {
                if (term.isEmpty()) {
                    coroutineScope {
                        val tags = async { trendRequest { ui.api.trendingTags() } }
                        val posts = trendRequest { ui.statuses.read({ ui.api.trends() }) { it } }
                        val topics = tags.await()
                        ensureActive()
                        results = SearchResults(
                            hashtags = topics.getOrDefault(results.hashtags),
                            statuses = posts.getOrDefault(results.statuses),
                        )
                        if (posts.isSuccess || topics.isSuccess) trends = results
                        error = posts.exceptionOrNull() ?: topics.exceptionOrNull()
                    }
                } else {
                    val next = ui.statuses.read({ ui.api.search(term, offset = page) }) { it.statuses }
                    val previous = if (page == 0) SearchResults() else results
                    val combined = SearchResults(
                        accounts = (previous.accounts + next.accounts).distinctBy { it.id },
                        statuses = (previous.statuses + next.statuses).distinctBy { it.id },
                        hashtags = (previous.hashtags + next.hashtags).distinctBy { it.name },
                    )
                    hasMore = combined != previous
                    results = combined
                    offset = page + 20
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                if (isActive) {
                    busy = false
                    refreshing = false
                }
            }
        }
    }
}

private suspend fun <T> trendRequest(fetch: suspend () -> List<T>): Result<List<T>> =
    try {
        Result.success(fetch())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveryScreen(ui: UiContext, state: DiscoveryState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val searchLabel = tr("mastodon_search_instance", "instance" to ui.api.instance)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(state) { state.open() }
    LaunchedEffect(state, state.submitted) {
        snapshotFlow {
            val layout = state.list.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.hasMore && !state.busy && state.error == null && last >= 0 && last >= layout.totalItemsCount - 3
        }.collect { more -> if (more) state.load() }
    }
    val search: () -> Unit = {
        if (state.query.isNotBlank()) {
            keyboard?.hide()
            state.submit()
        }
    }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = state.query, onValueChange = state::edit, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = searchLabel },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxWidth().background(colors.surfaceContainerLow, MaterialTheme.shapes.medium)
                        .heightIn(min = 50.dp).padding(start = 14.dp, end = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.size(21.dp), tint = colors.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) Text(searchLabel, style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        field()
                    }
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { state.edit("") }) {
                            Icon(Icons.Filled.Close, tr("discovery_clear"), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = search, enabled = state.query.isNotBlank()) {
                            Box(Modifier.size(31.dp).background(colors.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, tr("mastodon_explore"),
                                    Modifier.size(17.dp), tint = colors.surface)
                            }
                        }
                    }
                }
            },
        )
        val trending = state.submitted.isEmpty()
        val failure = state.error
        val needsAccount = ui.me == null && failure is ApiException && failure.statusCode == 401
        if (needsAccount && !trending) {
            LoginPrompt(ui, "discovery_login_title", "discovery_login_message", Icons.Filled.Search)
            return@Column
        }
        if (!needsAccount) ErrorNotice(failure?.let { it.message ?: it.toString() }, state::load)
        val results = state.results
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = state::refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = state.list, contentPadding = PaddingValues(bottom = 24.dp)) {
                if (results.accounts.isNotEmpty()) {
                    item("people-heading") { DiscoveryHeading(tr("discovery_people")) }
                    items(results.accounts, key = { "account:${it.id}" }) { account ->
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            AccountRow(account) { ui.navigate(Screen.Profile(account.id)) }
                        }
                    }
                }
                if (trending && results.hashtags.isNotEmpty()) {
                    item("topics") {
                        DiscoveryHeading(tr("discovery_trending_topics"))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results.hashtags, key = { it.name }) { tag ->
                                Surface(
                                    onClick = { ui.navigate(Screen.Feed(Timeline.Hashtag(tag.name), "#${tag.name}")) },
                                    shape = CircleShape, color = colors.surfaceContainerLow,
                                ) {
                                    Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("#", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                                        Text(tag.name, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }
                } else if (results.hashtags.isNotEmpty()) {
                    item("tags-heading") { DiscoveryHeading(tr("mastodon_tags")) }
                    items(results.hashtags, key = { "tag:${it.name}" }) { tag ->
                        val title = "#${tag.name}"
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            ui.navigate(Screen.Feed(Timeline.Hashtag(tag.name), title))
                        }.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(thickness = Dp.Hairline)
                    }
                }
                if (results.statuses.isNotEmpty()) {
                    item("posts-heading") { DiscoveryHeading(tr(if (trending) "discovery_trending_posts" else "mastodon_posts")) }
                    items(results.statuses, key = { "status:${it.id}" }) { StatusCard(ui, it) }
                }
                item("footer") {
                    when {
                        state.refreshing -> Unit
                        state.busy -> Loading()
                        results == SearchResults() && trending -> {
                            DiscoveryHeading(tr("discovery_timelines"))
                            listOf(HomeTimeline.Local, HomeTimeline.Federated).forEach { source ->
                                val name = tr(if (source == HomeTimeline.Local) "mastodon_local" else "mastodon_federated")
                                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { ui.navigate(Screen.Feed(source.source, name)) }
                                    .heightIn(min = 60.dp).padding(horizontal = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(PostIcons.Public, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
                                    Text(name, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                                }
                                HorizontalDivider(thickness = Dp.Hairline)
                            }
                        }
                        state.error != null -> Unit
                        results == SearchResults() -> Empty(tr("discovery_no_matches", "query" to state.submitted))
                        state.hasMore -> TextButton(onClick = state::load, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("discovery_more"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryHeading(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
        .semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
