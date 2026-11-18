package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal enum class HomeTimeline(val source: Timeline) {
    Home(Timeline.Home),
    Local(Timeline.Public(true)),
    Federated(Timeline.Public(false));

    companion object {
        fun initial(signedIn: Boolean) = if (signedIn) Home else Local
    }
}

@Composable
internal fun HomeScreen(
    ui: UiContext,
    selection: HomeTimeline,
    reselected: SharedFlow<Unit>,
    onSelect: (HomeTimeline) -> Unit,
) {
    val labels = listOf(tr("mastodon_home"), tr("mastodon_local"), tr("mastodon_federated"))
    Column {
        HomeTimelineSelector(labels, selection.ordinal) { onSelect(HomeTimeline.entries[it]) }
        key(selection) {
            if (selection == HomeTimeline.Home && ui.me == null) {
                com.quanquan.quantoot.LoginPrompt(ui, "mastodon_home_timeline_login_title",
                    "mastodon_home_timeline_login_message", Icons.Default.Home)
            } else FeedScreen(ui, selection.source, reselected = reselected)
        }
    }
}

@Composable
private fun HomeTimelineSelector(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val style = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().zIndex(1f).background(MaterialTheme.colorScheme.surface)) {
        val available = with(density) { (maxWidth / labels.size - 24.dp).roundToPx() }
        val scrollable = labels.any {
            measurer.measure(AnnotatedString(it), style = style.copy(fontWeight = FontWeight.Bold),
                softWrap = false).size.width > available
        }
        if (scrollable) {
            PrimaryScrollableTabRow(
                selected, modifier = Modifier.heightIn(min = 48.dp), edgePadding = 0.dp, minTabWidth = 88.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = {}, divider = { HorizontalDivider(thickness = Dp.Hairline) },
            ) {
                labels.forEachIndexed { index, label ->
                    HomeTab(label, selected == index, Modifier, onClick = { onSelect(index) })
                }
            }
        } else {
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), thickness = Dp.Hairline)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    HomeTab(label, selected == index, Modifier.weight(1f), onClick = { onSelect(index) })
                }
            }
        }
    }
}

@Composable
private fun HomeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.selectable(selected, role = Role.Tab, onClick = onClick).heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall, softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) {
            Box(Modifier.align(Alignment.BottomCenter).size(width = 32.dp, height = 3.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

private const val FEED_FOOTER_KEY = "feed:footer"

internal interface FeedViewport {
    val emptyPosition: FeedPosition
    fun position(): FeedPosition
    fun readingOlder(): Boolean
    fun nearEnd(rowCount: Int): Boolean
    suspend fun restore(position: FeedPosition)
    suspend fun revealFirstStatus()
}

private class ListFeedViewport(private val list: LazyListState) : FeedViewport {
    override val emptyPosition = FeedPosition.Status(0, 0)
    private val rowCount: Int
        get() = (list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)

    private fun visiblePosition() = FeedPosition.Status(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)

    override fun position(): FeedPosition {
        val visible = visiblePosition()
        return when {
            rowCount == 0 -> emptyPosition
            visible.index >= rowCount -> FeedPosition.Status(rowCount - 1, 0)
            else -> visible
        }
    }

    override fun readingOlder(): Boolean {
        val visible = visiblePosition()
        return rowCount > 0 && (visible.index >= rowCount || visible.readingOlder())
    }

    override fun nearEnd(rowCount: Int) = feedNeedsMore(list.layoutInfo.visibleItemsInfo.map { it.index }, rowCount, hasLeading = false)

    override suspend fun restore(position: FeedPosition) {
        when (position) {
            is FeedPosition.Leading -> list.scrollToItem(0)
            is FeedPosition.Status -> list.scrollToItem(position.index, position.offset)
        }
    }

    override suspend fun revealFirstStatus() = list.scrollToItem(0)
}

@Composable
private fun <T> rememberFeedPager(
    ui: UiContext,
    identity: String,
    cache: FeedCacheSession<T>,
    viewport: FeedViewport,
    isActive: Boolean,
    id: (T) -> String,
    project: (T) -> T?,
    displayedCount: (List<T>) -> Int = { it.size },
    fetch: suspend (String?) -> Page<T>,
): FeedPager<T> {
    val accountKey = ui.store.state.value.activeKey
    val cacheKey =
        remember(ui.api, accountKey, identity) {
            feedCacheKey(ui.api.instance, accountKey, identity)
        }
    return key(cache, cacheKey, viewport) {
        val scope = rememberCoroutineScope()
        var restored by remember { mutableStateOf(false) }
        var attachment by remember { mutableStateOf<FeedCacheSession<T>.Attachment?>(null) }
        val pager = remember {
            FeedPager(scope, id, project, onClose = { pager ->
                try {
                    attachment?.close(
                        if (restored && pager.ready) pager.snapshot(viewport.position(), viewport.emptyPosition, id)
                        else null,
                    )
                } finally {
                    scope.cancel()
                }
            }, fetch = fetch)
        }
        DisposableEffect(pager) {
            val current = cache.attach(cacheKey)
            attachment = current
            scope.launch {
                val saved = current.read()
                ensureActive()
                if (!current.isCurrent) return@launch
                pager.restore(saved)
                viewport.restore(
                    saved?.restoredPosition(pager.rows, id, viewport.emptyPosition is FeedPosition.Leading)
                        ?: viewport.emptyPosition,
                )
                ensureActive()
                if (!current.isCurrent) return@launch
                restored = true
                snapshotFlow {
                    if (pager.ready) pager.snapshot(viewport.position(), viewport.emptyPosition, id) else null
                }.collect { snapshot -> snapshot?.let(current::checkpoint) }
            }
            onDispose { pager.close() }
        }
        val foreground by ui.platform.foreground.collectAsState()
        DisposableEffect(pager, isActive, foreground) {
            val job = if (isActive && foreground) scope.launch {
                snapshotFlow { restored }.first { it }
                pager.load(defer = viewport.readingOlder())
                launch {
                    while (currentCoroutineContext().isActive) {
                        delay(20_000)
                        if (!pager.busy) pager.load(defer = viewport.readingOlder())
                    }
                }
                snapshotFlow {
                    !pager.busy && pager.ready && pager.error == null && !pager.exhausted &&
                        viewport.nearEnd(displayedCount(pager.rows))
                }.collect { shouldLoad -> if (shouldLoad) pager.load(more = true) }
            } else null
            onDispose {
                job?.cancel()
                pager.cancelLoad()
            }
        }
        pager
    }
}

@Composable
internal fun rememberStatusPager(
    ui: UiContext,
    source: Timeline,
    viewport: FeedViewport,
    isActive: Boolean = true,
): FeedPager<Status> {
    val pager = rememberFeedPager(
        ui, source.toString(), ui.statusFeedCache, viewport, isActive, { it.id }, ui.statuses::project,
    ) { cursor ->
        ui.statuses.read({ ui.api.timeline(source, maxId = cursor) }) { it.items }
    }
    val foreground by ui.platform.foreground.collectAsState()
    val streamSource = source.streamSource()
    DisposableEffect(pager, streamSource, foreground, isActive) {
        val job = if (isActive && foreground) pager.scope.launch {
            if (ui.me != null && streamSource != null && streamSource != StreamSource.User)
                launch { ui.api.events(streamSource).collect { ui.ingest(it) } }
            ui.events.collect { event ->
                pager.apply(event, streamSource, defer = viewport.readingOlder(), active = true)
            }
        } else null
        onDispose { job?.cancel() }
    }
    DisposableEffect(pager, pager.rows.firstOrNull()?.id, isActive, foreground) {
        val job = if (isActive && foreground && source == Timeline.Home && ui.me != null) pager.scope.launch {
            pager.rows.firstOrNull()?.let { status ->
                try {
                    ui.api.markRead("home", status.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        } else null
        onDispose { job?.cancel() }
    }
    return pager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    ui: UiContext,
    source: Timeline,
    isActive: Boolean = true,
    reselected: SharedFlow<Unit>? = null,
) {
    val identity = source.toString()
    key(ui.api, ui.store.state.value.activeKey, identity) {
        val listState = rememberLazyListState()
        val viewport = remember(listState) { ListFeedViewport(listState) }
        val scope = rememberCoroutineScope()
        val pager = rememberStatusPager(ui, source, viewport, isActive)
        if (reselected != null) LaunchedEffect(pager, reselected) {
            reselected.collect {
                pager.reveal()
                viewport.revealFirstStatus()
            }
        }
        Column {
            ErrorNotice(pager.error) {
                pager.load(more = pager.failedMore, defer = viewport.readingOlder())
            }
            PullToRefreshBox(
                isRefreshing = pager.busy && !pager.paging,
                onRefresh = { pager.load() },
                modifier = Modifier.weight(1f).clipToBounds(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(
                        pager.rows,
                        key = { "status:${it.id}" },
                    ) { status ->
                        StatusCard(ui, status)
                    }
                    item(key = FEED_FOOTER_KEY) {
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
                if (pager.pending.isNotEmpty()) {
                    NewPostsButton(pager.pending.size, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                        pager.reveal()
                        scope.launch { viewport.revealFirstStatus() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(ui: UiContext, isActive: Boolean = true) {
    key(ui.api, ui.store.state.value.activeKey) {
        val listState = rememberLazyListState()
        val viewport = remember(listState) { ListFeedViewport(listState) }
        val scope = rememberCoroutineScope()
        val pager =
            rememberFeedPager(
                ui,
                "notifications",
                ui.notificationFeedCache,
                viewport,
                isActive,
                { it.id },
                { notification ->
                    if (notification.status == null) notification
                    else ui.statuses.project(notification.status)?.let { notification.copy(status = it) }
                },
                displayedCount = { groupFeedNotifications(it).size },
            ) { cursor ->
                ui.statuses.read({ ui.api.notifications(cursor) }) { items -> items.mapNotNull { it.status } }
                    .let { items -> Page(items, items.lastOrNull()?.id) }
            }
        val groups = groupFeedNotifications(pager.rows)
        val handling = remember { mutableStateListOf<String>() }
        val foreground by ui.platform.foreground.collectAsState()
        DisposableEffect(pager, isActive, foreground) {
            val job = if (isActive && foreground) pager.scope.launch {
                ui.events.collect { event ->
                    if (event is StreamEvent.Notify && event.source == StreamSource.User)
                        pager.created(event.notification, defer = viewport.readingOlder())
                    if (event is StreamEvent.Deleted || event is StreamEvent.Edited) pager.prune()
                    if (event is StreamEvent.Reconnected && event.source == StreamSource.User)
                        pager.load(defer = viewport.readingOlder())
                }
            } else null
            onDispose { job?.cancel() }
        }
        DisposableEffect(pager, pager.rows.firstOrNull()?.id, isActive, foreground) {
            val job = if (isActive && foreground) pager.scope.launch {
                pager.rows.firstOrNull()?.let {
                    try {
                        ui.api.markRead("notifications", it.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            } else null
            onDispose { job?.cancel() }
        }
        Column {
            ErrorNotice(pager.error) {
                pager.load(more = pager.failedMore, defer = viewport.readingOlder())
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
                ) {
                    items(groups, key = { it.first().id }) { group ->
                        val notification = group.first()
                        NotificationItem(ui, group, handling = notification.id in handling) { approve ->
                            handling.add(notification.id)
                            scope.launch {
                                try {
                                    ui.api.followRequest(notification.account.id, approve)
                                    pager.remove { it.id == notification.id }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    ui.error = e.message
                                } finally {
                                    handling.remove(notification.id)
                                }
                            }
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
                if (pager.pending.isNotEmpty()) {
                    NewPostsButton(pager.pending.size, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                        pager.reveal()
                        scope.launch { viewport.revealFirstStatus() }
                    }
                }
            }
        }
    }
}


@Composable
private fun NewPostsButton(count: Int, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)) {
        Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${tr("mastodon_new_posts")} ($count)")
    }
}
