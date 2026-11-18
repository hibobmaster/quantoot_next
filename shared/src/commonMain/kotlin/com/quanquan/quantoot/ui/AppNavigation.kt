package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class MainTab(val titleKey: String, val outline: ImageVector, val filled: ImageVector) {
    Home("mastodon_home", Icons.Outlined.Home, Icons.Filled.Home),
    Explore("mastodon_explore", Icons.Outlined.Search, Icons.Filled.Search),
    Notifications("mastodon_notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Profile("mastodon_profile", Icons.Outlined.Person, Icons.Filled.Person),
}

@Composable
internal fun Wordmark() {
    Text(
        "QuanToot", style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.25).sp, maxLines = 1,
    )
}

@Composable
internal fun ShellHeader(ui: UiContext, tab: MainTab, screen: Screen?, rail: Boolean) {
    val colors = MaterialTheme.colorScheme
    val home = screen == null && tab == MainTab.Home
    val accountsLabel = tr("mastodon_accounts")
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .padding(start = if (screen == null) 16.dp else 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (screen != null) {
                IconButton(onClick = ui::requestBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("mastodon_close"))
                }
            }
            if (home) {
                if (!rail) {
                    BrandMark(Modifier.size(40.dp))
                    Spacer(Modifier.width(9.dp))
                }
                Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                    Wordmark()
                    Text(ui.api.instance, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            } else {
                val title = when (screen) {
                    null -> tr(tab.titleKey)
                    is Screen.Feed -> screen.title
                    is Screen.Thread -> tr("mastodon_conversation")
                    is Screen.Profile -> tr("mastodon_profile")
                    is Screen.People -> tr(if (screen.following) "mastodon_following_count" else "mastodon_followers_count_label")
                    is Screen.Settings -> tr(screen.section?.titleKey ?: "settings_title")
                    Screen.Accounts -> accountsLabel
                    Screen.Instance -> tr("mastodon_choose_instance")
                    Screen.EditProfile -> tr("mastodon_edit_profile")
                    is Screen.Compose -> return
                }
                Text(title, Modifier.weight(1f).padding(end = 4.dp).testTag("screen-title").semantics { heading() },
                    style = if (screen == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (screen != Screen.Accounts) {
                IconButton(onClick = { ui.navigate(Screen.Accounts) },
                    modifier = Modifier.semantics { contentDescription = accountsLabel }) {
                    AccountAvatar(ui.me, 30.dp)
                }
            }
            if (screen !is Screen.Settings) {
                IconButton(onClick = { ui.navigate(Screen.Settings()) }) {
                    Icon(Icons.Outlined.Settings, tr("settings_title"), Modifier.size(22.dp))
                }
            }
        }
        if (!home) HorizontalDivider(thickness = Dp.Hairline)
    }
}

@Composable
internal fun DestinationBar(selected: MainTab, unread: Int, select: (MainTab) -> Unit) {
    Column {
        HorizontalDivider(thickness = Dp.Hairline)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            MainTab.entries.forEach { tab ->
                Destination(tab, tab == selected, unread, Modifier.weight(1f), select)
            }
        }
    }
}

@Composable
internal fun DestinationRail(
    selected: MainTab,
    unread: Int,
    select: (MainTab) -> Unit,
    compose: () -> Unit,
) {
    BoxWithConstraints(Modifier.width(100.dp).fillMaxHeight()) {
        val compact = maxHeight < 480.dp
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()
                .padding(top = if (compact) 6.dp else 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp),
        ) {
            BrandMark(Modifier.size(36.dp))
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            MainTab.entries.forEach { tab ->
                Destination(tab, tab == selected, unread, Modifier.fillMaxWidth(), select, compact)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            NewPostButton(compose)
        }
    }
    VerticalDivider(thickness = Dp.Hairline)
}

@Composable
private fun Destination(
    tab: MainTab,
    selected: Boolean,
    unread: Int,
    modifier: Modifier,
    select: (MainTab) -> Unit,
    compact: Boolean = false,
) {
    val label = tr(tab.titleKey)
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.selectable(selected, role = Role.Tab, onClick = { select(tab) })
            .heightIn(min = if (compact) 48.dp else 60.dp)
            .padding(horizontal = 2.dp, vertical = if (compact) 2.dp else 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgedBox(badge = {
            if (tab == MainTab.Notifications && unread > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Text(if (unread > 99) "99+" else unread.toString())
                }
            }
        }) {
            Icon(if (selected) tab.filled else tab.outline, null, Modifier.size(24.dp), tint = color)
        }
        Text(label, Modifier.padding(top = 2.dp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun NewPostButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
    ) {
        Icon(Icons.Outlined.Edit, tr("mastodon_new_post"), Modifier.size(25.dp))
    }
}
