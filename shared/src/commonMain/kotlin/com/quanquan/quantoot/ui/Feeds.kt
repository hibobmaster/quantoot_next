package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer

@Composable
fun HomeScreen(ui: UiContext) {
    var selection by remember { mutableIntStateOf(if (ui.me == null) 1 else 0) }
    val labels = listOf(tr("mastodon_home"), tr("mastodon_local"), tr("mastodon_federated"))
    Column {
        ScrollableTabRow(selection) {
            labels.forEachIndexed { i, title ->
                Tab(selected = selection == i, onClick = { selection = i }, text = { Text(title) })
            }
        }
        key(selection) {
            if (selection == 0 && ui.me == null) com.quanquan.quantoot.LoginPrompt(ui)
            else
                FeedScreen(
                    ui,
                    when (selection) {
                        0 -> Timeline.Home
                        1 -> Timeline.Public(true)
                        else -> Timeline.Public(false)
                    },
                )
        }
    }
}

@Composable
private fun <T> rememberFeedPager(
    ui: UiContext,
    identity: String,
    serializer: KSerializer<T>,
    listState: LazyListState,
    isActive: Boolean,
    id: (T) -> String,
    project: (T) -> T?,
    fetch: suspend (String?) -> Page<T>,
): FeedPager<T> {
    val scope = rememberCoroutineScope()
    val accountKey = ui.store.state.value.activeKey
    val key =
        remember(ui.api, accountKey, identity) {
            feedCacheKey(ui.api.instance, accountKey, identity)
        }
    val cache = remember(key) { FeedCache(ui.platform.preferences, key, serializer) }
    val pager = remember(key) { FeedPager(scope, id, project, fetch) }
    val foreground by ui.platform.foreground.collectAsState()
    val active by rememberUpdatedState(isActive && foreground)
    fun readingOlder() = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160
    LaunchedEffect(pager) {
        val saved = cache.read()
        pager.restore(saved)
        if (saved != null && pager.rows.isNotEmpty()) {
            val anchor = saved.items.getOrNull(saved.index)?.let(id)
            val restoredIndex = pager.rows.indexOfFirst { id(it) == anchor }
                .takeIf { it >= 0 } ?: saved.index.coerceAtMost(pager.rows.lastIndex)
            listState.scrollToItem(restoredIndex, saved.offset)
        }
        if (active) pager.load(defer = readingOlder())
    }
    LaunchedEffect(pager) {
        snapshotFlow {
            pager.snapshot(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .collectLatest { snapshot ->
                if (pager.ready) {
                    delay(400)
                    cache.write(snapshot)
                }
            }
    }
    var wasActive by remember(pager) { mutableStateOf(isActive && foreground) }
    LaunchedEffect(pager, foreground, isActive) {
        if (active && !wasActive && pager.ready) pager.load(defer = readingOlder())
        wasActive = active
        if (active) while (isActive) {
            delay(20_000)
            if (pager.ready && !pager.busy) pager.load(defer = readingOlder())
        }
    }
    LaunchedEffect(pager, isActive) {
        snapshotFlow {
            Triple(
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                listState.layoutInfo.totalItemsCount,
                pager.rows.size,
            )
        }
            .collect { (last, total, _) ->
                if (
                    isActive && total > 0 && last >= total - 4 && pager.ready && pager.error == null
                )
                    pager.load(more = true)
            }
    }
    return pager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(ui: UiContext, source: Timeline, isActive: Boolean = true) {
    val identity = source.toString()
    key(ui.api, ui.store.state.value.activeKey, identity) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val pager =
            rememberFeedPager(
                ui, identity, Status.serializer(), listState, isActive, { it.id }, ui.statuses::project,
            ) { cursor ->
                ui.statuses.read({ ui.api.timeline(source, maxId = cursor) }) { it.items }
            }
        val foreground by ui.platform.foreground.collectAsState()
        val streamSource = source.streamSource()
        fun readingOlder() = listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset > 160 || !isActive
        LaunchedEffect(ui, streamSource, foreground, isActive) {
            if (ui.me != null && foreground && isActive && streamSource != null && streamSource != StreamSource.User)
                ui.api.events(streamSource).collect { ui.ingest(it) }
        }
        LaunchedEffect(pager, isActive, foreground) {
            ui.events.collect { event ->
                when (event) {
                    is StreamEvent.Created -> when (event.source) {
                        StreamSource.LocalWrite -> if (isActive && foreground) pager.load(defer = readingOlder())
                        streamSource -> pager.created(event.status, defer = readingOlder())
                        else -> Unit
                    }
                    is StreamEvent.Edited, is StreamEvent.Deleted -> pager.prune()
                    is StreamEvent.Reconnected ->
                        if (isActive && foreground && (streamSource == null || event.source == streamSource))
                            pager.load(defer = readingOlder())
                    is StreamEvent.Notify -> Unit
                }
            }
        }
        LaunchedEffect(pager.rows.firstOrNull()?.id, isActive) {
            if (isActive && source == Timeline.Home && ui.me != null)
                pager.rows.firstOrNull()?.let { status ->
                    try {
                        ui.api.markRead("home", status.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (pager.pending.isNotEmpty())
                    TextButton(
                        onClick = {
                            pager.reveal()
                            scope.launch { listState.scrollToItem(0) }
                        }
                    ) {
                        Text("${tr("mastodon_new_posts")} (${pager.pending.size})")
                    }
            }
            ErrorNotice(pager.error) {
                pager.load(more = pager.failedMore, defer = listState.firstVisibleItemIndex > 0)
            }
            PullToRefreshBox(
                isRefreshing = pager.busy && !pager.paging,
                onRefresh = { pager.load() },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        pager.rows,
                        key = { it.id },
                    ) { status ->
                        StatusCard(ui, status)
                    }
                    item {
                        when {
                            pager.busy -> Loading()
                            pager.rows.isEmpty() && pager.error == null -> Empty()
                            !pager.exhausted && pager.rows.isNotEmpty() ->
                                OutlinedButton(
                                    onClick = { pager.load(true) },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                ) {
                                    Text(
                                        tr(
                                            if (pager.error != null) "mastodon_retry"
                                            else "mastodon_posts"
                                        )
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreadScreen(ui: UiContext, id: String, focusReplyId: String? = null) {
    var posts by remember(id, ui.revision) { mutableStateOf<List<Status>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, attempt, ui.revision) {
        try {
            val status = ui.statuses.read({ ui.api.status(id) }) { listOf(it) }
            val context = ui.statuses.read({ ui.api.context(id) }) { it.ancestors + it.descendants }
            posts = context.ancestors + status + context.descendants
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    Column {
        ErrorNotice(error) { attempt++ }
        if (posts == null && error == null) Loading()
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(posts.orEmpty().mapNotNull(ui.statuses::project), key = { it.id }) {
                StatusCard(ui, it, highlighted = it.id == id)
            }
        }
    }
}

@Composable
fun DiscoveryScreen(ui: UiContext) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    var trends by remember { mutableStateOf<List<Status>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var submittedQuery by remember { mutableStateOf("") }
    var exhausted by remember { mutableStateOf(false) }
    var failedMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun load(more: Boolean = false) {
        scope.launch {
            if (busy || (more && exhausted)) return@launch
            val requestedQuery = if (more) submittedQuery else query.trim()
            busy = true
            error = null
            failedMore = more
            try {
                if (requestedQuery.isBlank()) {
                    trends = ui.statuses.read({ ui.api.trends() }) { it }
                    tags = ui.api.trendingTags()
                    searched = false
                } else {
                    val next = ui.statuses.read({ ui.api.search(requestedQuery, offset = if (more) offset else 0) }) { it.statuses }
                    val merged =
                        if (more)
                            SearchResults(
                                (results.accounts + next.accounts).distinctBy { it.id },
                                (results.statuses + next.statuses).distinctBy { it.id },
                                (results.hashtags + next.hashtags).distinctBy { it.name },
                            )
                        else next
                    exhausted =
                        (next.accounts.isEmpty() &&
                            next.statuses.isEmpty() &&
                            next.hashtags.isEmpty()) || (more && merged == results)
                    results = merged
                    offset = if (more) offset + 20 else 20
                    searched = true
                    submittedQuery = requestedQuery
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(ui.api) { load() }
    LaunchedEffect(searched, submittedQuery) {
        snapshotFlow {
            Triple(
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                listState.layoutInfo.totalItemsCount,
                results,
            )
        }
            .collect { (last, total, _) ->
                if (searched && total > 0 && last >= total - 4 && error == null) load(true)
            }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(tr("mastodon_search_instance", "instance" to ui.api.instance)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { load() }, enabled = !busy) { Text(tr("mastodon_explore")) }
        }
        ErrorNotice(error) { load(failedMore) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searched) {
                items(results.accounts, key = { "account:${it.id}" }) {
                    AccountRow(it) { ui.navigate(Screen.Profile(it.id)) }
                }
                items(results.hashtags, key = { "tag:${it.name}" }) { tag ->
                    TextButton(
                        onClick = {
                            ui.navigate(Screen.Feed(Timeline.Hashtag(tag.name), "#${tag.name}"))
                        }
                    ) {
                        Text("#${tag.name}")
                    }
                }
                items(results.statuses, key = { "status:${it.id}" }) {
                    StatusCard(ui, it)
                }
                item {
                    if (!exhausted)
                        OutlinedButton(
                            onClick = { load(true) },
                            enabled = !busy,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(tr(if (error != null) "mastodon_retry" else "mastodon_posts"))
                        }
                }
            } else {
                items(tags, key = { "tag:${it.name}" }) { tag ->
                    TextButton(
                        onClick = {
                            ui.navigate(Screen.Feed(Timeline.Hashtag(tag.name), "#${tag.name}"))
                        }
                    ) {
                        Text("#${tag.name}")
                    }
                }
                items(trends, key = { it.id }) { StatusCard(ui, it) }
            }
            item {
                if (busy) Loading()
                else if (
                    (searched &&
                        results.accounts.isEmpty() &&
                        results.statuses.isEmpty() &&
                        results.hashtags.isEmpty()) ||
                        (!searched && trends.isEmpty() && tags.isEmpty())
                )
                    Empty()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(ui: UiContext, isActive: Boolean = true) {
    key(ui.api, ui.store.state.value.activeKey) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val pager =
            rememberFeedPager(
                ui,
                "notifications",
                Notification.serializer(),
                listState,
                isActive,
                { it.id },
                { notification ->
                    if (notification.status == null) notification
                    else ui.statuses.project(notification.status)?.let { notification.copy(status = it) }
                },
            ) { cursor ->
                ui.statuses.read({ ui.api.notifications(cursor) }) { items -> items.mapNotNull { it.status } }
                    .let { items -> Page(items, items.lastOrNull()?.id) }
            }
        val groups = groupFeedNotifications(pager.rows)
        val handling = remember { mutableStateListOf<String>() }
        LaunchedEffect(pager, isActive) {
            ui.events.collect { event ->
                if (event is StreamEvent.Notify)
                    pager.created(
                        event.notification,
                        defer =
                            listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 160 ||
                                !isActive,
                    )
                if (event is StreamEvent.Deleted || event is StreamEvent.Edited) pager.prune()
                if (event is StreamEvent.Reconnected && event.source == StreamSource.User)
                    pager.load(defer = listState.firstVisibleItemIndex > 0 || !isActive)
            }
        }
        LaunchedEffect(pager.rows.firstOrNull()?.id, isActive) {
            if (isActive)
                pager.rows.firstOrNull()?.let {
                    try {
                        ui.api.markRead("notifications", it.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
        }
        Column {
            Row {
                if (pager.pending.isNotEmpty())
                    TextButton(
                        onClick = {
                            pager.reveal()
                            scope.launch { listState.scrollToItem(0) }
                        }
                    ) {
                        Text("${tr("mastodon_new_posts")} (${pager.pending.size})")
                    }
            }
            ErrorNotice(pager.error) {
                pager.load(more = pager.failedMore, defer = listState.firstVisibleItemIndex > 0)
            }
            PullToRefreshBox(
                isRefreshing = pager.busy && !pager.paging,
                onRefresh = { pager.load() },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.first().id }) { group ->
                        val notification = group.first()
                        Column(Modifier.padding(12.dp)) {
                            group.forEach { item ->
                                AccountRow(item.account) {
                                    ui.navigate(Screen.Profile(item.account.id))
                                }
                            }
                            val label =
                                when (notification.type) {
                                    "mention" -> "mastodon_replied"
                                    "favourite" -> "mastodon_favorited"
                                    "reblog" -> "mastodon_boosted"
                                    "follow" -> "mastodon_followed_you"
                                    "follow_request" -> "mastodon_follow_request"
                                    "poll" -> "mastodon_poll_ended"
                                    "update" -> "mastodon_edited_post"
                                    else -> null
                                }
                            Text(label?.let { tr(it) } ?: notification.type)
                            if (notification.type == "follow_request")
                                Row {
                                    listOf(true, false).forEach { approve ->
                                        TextButton(
                                            enabled = notification.id !in handling,
                                            onClick = {
                                                handling.add(notification.id)
                                                scope.launch {
                                                    try {
                                                        ui.api.followRequest(
                                                            notification.account.id,
                                                            approve,
                                                        )
                                                        pager.remove { it.id == notification.id }
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        ui.error = e.message
                                                    } finally {
                                                        handling.remove(notification.id)
                                                    }
                                                }
                                            },
                                        ) {
                                            Text(
                                                tr(
                                                    if (approve) "mastodon_follow_request_approve"
                                                    else "mastodon_follow_request_reject"
                                                )
                                            )
                                        }
                                    }
                                }
                            notification.status?.let { StatusCard(ui, it) }
                        }
                    }
                    item {
                        if (pager.busy) Loading()
                        else if (groups.isEmpty() && pager.error == null)
                            Empty(tr("mastodon_empty_notifications"))
                        else if (!pager.exhausted)
                            TextButton(onClick = { pager.load(true) }) {
                                Text(
                                    tr(
                                        if (pager.error != null) "mastodon_retry"
                                        else "mastodon_posts"
                                    )
                                )
                            }
                    }
                }
            }
        }
    }
}
