package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.data.ApiException
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun ThreadScreen(ui: UiContext, id: String, focusReplyId: String? = null) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val thread = remember(ui, id) {
        ThreadState(id, scope, ui.statuses::project, initialRoot = ui.statuses.known(id)) {
            loadThread(ui, id)
        }
    }
    var highlightFocus by remember(thread, focusReplyId) { mutableStateOf(false) }
    val foreground by ui.platform.foreground.collectAsState()
    DisposableEffect(thread) { onDispose { thread.setActive(false) } }
    LaunchedEffect(thread) { ui.events.collect { thread.apply(it) } }
    LaunchedEffect(thread, foreground) {
        thread.setActive(foreground)
        if (foreground) {
            thread.refresh()
            while (isActive) {
                delay(20_000)
                if (!thread.busy) thread.refresh()
            }
        }
    }
    LaunchedEffect(thread, focusReplyId) {
        focusReplyId?.let { ui.statuses.known(it) }?.let(thread::includePublished)
        val index = snapshotFlow {
            if (!thread.loaded) null
            else if (focusReplyId == null) thread.ancestors.size
            else thread.descendants.indexOfFirst { it.id == focusReplyId }
                .takeIf { it >= 0 }?.let { thread.ancestors.size + 2 + it }
        }.filterNotNull().first()
        listState.scrollToItem(index)
        highlightFocus = true
        delay(650)
        highlightFocus = false
    }
    val root = thread.root
    val ancestors = thread.ancestors
    val descendants = thread.descendants
    val indentStep = if (LocalDensity.current.fontScale > 1.3f) 0.dp else 8.dp
    Column(Modifier.fillMaxSize()) {
        ErrorNotice(thread.error, thread::refresh)
        PullToRefreshBox(
            isRefreshing = thread.busy && root != null,
            onRefresh = thread::refresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(ancestors, key = { "ancestor:${it.id}" }) { StatusCard(ui, it, filterContext = "thread") }
                item(key = "focal:$id") {
                    when {
                        root != null -> StatusCard(
                            ui, root, highlighted = highlightFocus && focusReplyId == null,
                            presentation = StatusPresentation.Focal,
                            onViewReplies = { scope.launch { listState.animateScrollToItem(thread.ancestors.size + 1) } },
                            filterContext = "thread",
                        )
                        thread.rootUnavailable -> Empty(tr("mastodon_post_gone"))
                        thread.busy -> Loading()
                    }
                }
                item(key = "replies") {
                    Text(
                        tr("mastodon_replies"),
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(descendants, key = { "reply:${it.id}" }) { status ->
                    val levels = (thread.depth(status.id) - 1).coerceIn(0, 3)
                    Box(Modifier.padding(start = indentStep * levels)) {
                        StatusCard(ui, status, highlighted = highlightFocus && status.id == focusReplyId,
                            filterContext = "thread")
                    }
                }
                if (thread.loaded && descendants.isEmpty() && thread.error == null)
                    item(key = "empty-replies") { Empty(tr("mastodon_empty_replies")) }
            }
        }
        HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccountAvatar(ui.me, 36.dp)
            FilledTonalButton(
                onClick = { root?.let { ui.requireLogin { ui.navigate(Screen.Compose(reply = it)) } } },
                enabled = root != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(tr("mastodon_button_reply"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private suspend fun loadThread(ui: UiContext, id: String): ThreadResult {
    val root = try {
        ui.statuses.read({ ui.api.status(id) }) { listOf(it) }
    } catch (e: ApiException) {
        if (e.statusCode == 404) null else throw e
    }
    return try {
        val context = ui.statuses.read({ ui.api.context(id) }) { it.ancestors + it.descendants }
        ThreadResult(root, context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ThreadResult(root, null, if (root == null && e is ApiException && e.statusCode == 404) null else e.message)
    }
}
