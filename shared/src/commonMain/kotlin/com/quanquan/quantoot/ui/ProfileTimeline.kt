package com.quanquan.quantoot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Status
import com.quanquan.quantoot.model.Timeline
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTimeline(
    ui: UiContext,
    person: Account,
    header: @Composable () -> Unit,
    about: @Composable () -> Unit,
) {
    val state = rememberProfileScrollState()
    val scope = rememberCoroutineScope()
    val body = state.attachment
    val feed = key(body) {
        body.selection.source(person.id)?.let { source ->
            val viewport = remember { ProfileFeedViewport(state, body) }
            val pager = rememberStatusPager(ui, source, viewport)
            DisposableEffect(pager) { onDispose { pager.close() } }
            remember(pager) {
                viewport.attach(pager)
                ProfileFeed(pager, viewport)
            }
        }
    }
    val onSelect: (ProfileSelection) -> Unit = { next ->
        scope.launch {
            if (state.attachment === body && next != body.selection) {
                feed?.pager?.close()
                state.select(next)
            }
        }
    }
    val refresh = rememberPullToRefreshState()
    val refreshing = feed?.pager?.let { it.busy && !it.paging } == true
    Box(
        Modifier.fillMaxSize().pullToRefresh(
            isRefreshing = refreshing,
            state = refresh,
            enabled = feed != null,
            onRefresh = { feed?.pager?.load() },
        ),
    ) {
        ProfileScrollLayout(
            state,
            modifier = Modifier.fillMaxSize().testTag("profile:viewport"),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            onSelect = onSelect,
            header = header,
        ) { selection ->
            if (feed == null) {
                item("profile:about") { about() }
            } else {
                val pager = feed.pager
                val viewport = feed.viewport
                viewport.controls.forEach { control ->
                    item(control.key) {
                        when (control) {
                            ProfileFeedControl.Activity -> ProfileActivityFilters(selection, onSelect)
                            ProfileFeedControl.Pending -> TextButton(
                                onClick = {
                                    pager.reveal()
                                    scope.launch { viewport.revealFirstStatus() }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) { Text("${tr("mastodon_new_posts")} (${pager.pending.size})") }
                            ProfileFeedControl.Error -> ErrorNotice(pager.error) {
                                pager.load(more = pager.failedMore, defer = viewport.readingOlder())
                            }
                        }
                    }
                }
                pager.rows.forEach { status ->
                    item(profileStatusKey(status.id)) { StatusCard(ui, status) }
                }
                item(viewport.footerKey) {
                    when {
                        pager.busy || !pager.ready -> Loading()
                        pager.rows.isEmpty() && pager.error == null -> Empty()
                        !pager.exhausted && pager.rows.isNotEmpty() -> OutlinedButton(
                            onClick = { pager.load(true) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) { Text(tr(if (pager.error != null) "mastodon_retry" else "mastodon_posts")) }
                    }
                }
            }
        }
        if (feed != null) PullToRefreshDefaults.Indicator(
            state = refresh,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private data class ProfileFeed(val pager: FeedPager<Status>, val viewport: ProfileFeedViewport)

@Composable
private fun ProfileActivityFilters(selection: ProfileSelection, onSelect: (ProfileSelection) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selection.showReplies,
            { onSelect(selection.copy(showReplies = !selection.showReplies)) },
            label = { Text(tr("mastodon_filter_show_replies")) },
        )
        FilterChip(
            selection.showBoosts,
            { onSelect(selection.copy(showBoosts = !selection.showBoosts)) },
            label = { Text(tr("mastodon_filter_show_boosts")) },
        )
    }
}

internal fun ProfileSelection.source(id: String): Timeline.AccountPosts? = when (category) {
    ProfileCategory.Posts -> Timeline.AccountPosts(id, excludeReplies = true, excludeReblogs = true)
    ProfileCategory.Media -> Timeline.AccountPosts(id, onlyMedia = true)
    ProfileCategory.Featured -> Timeline.AccountPosts(id, pinned = true)
    ProfileCategory.Activity -> Timeline.AccountPosts(id, excludeReplies = !showReplies, excludeReblogs = !showBoosts)
    ProfileCategory.About -> null
}
