package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.data.ApiException
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(ui: UiContext, id: String, focusReplyId: String? = null) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val thread = remember(ui, id) {
        ThreadState(id, scope, ui.statuses::project, initialRoot = ui.statuses.known(id)) {
            loadThread(ui, id)
        }
    }
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
    }
    val root = thread.root
    val ancestors = thread.ancestors
    val descendants = thread.descendants
    Column(Modifier.fillMaxSize()) {
        ErrorNotice(thread.error, thread::refresh)
        PullToRefreshBox(
            isRefreshing = thread.busy,
            onRefresh = thread::refresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ancestors, key = { "ancestor:${it.id}" }) { StatusCard(ui, it) }
                item(key = "focal:$id") {
                    when {
                        root != null -> StatusCard(ui, root, highlighted = true)
                        thread.rootUnavailable -> Empty("This post is no longer available.")
                        thread.busy -> Loading()
                    }
                }
                item(key = "replies") {
                    Text(
                        tr("mastodon_replies"),
                        Modifier.padding(horizontal = 22.dp, vertical = 12.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(descendants, key = { "reply:${it.id}" }) { status ->
                    val depth = thread.depth(status.id)
                    Column(Modifier.padding(start = ((depth - 1).coerceIn(0, 3) * 12).dp)) {
                        if (depth > 1) {
                            val parent = descendants.firstOrNull { it.id == status.inReplyToId }
                            if (parent != null)
                                Text(
                                    tr("mastodon_reply_to", "name" to "@${parent.account.acct}"),
                                    Modifier.padding(start = 22.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                        StatusCard(ui, status, highlighted = status.id == focusReplyId)
                    }
                }
                if (thread.loaded && descendants.isEmpty() && thread.error == null)
                    item(key = "empty-replies") { Empty("No replies yet.") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Button(
                onClick = { root?.let { ui.requireLogin { ui.navigate(Screen.Compose(reply = it)) } } },
                enabled = root != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(tr("mastodon_button_reply"))
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
