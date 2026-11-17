package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.ServerInfo

@Composable
internal fun ServerInformation(ui: UiContext, server: ServerInfo, description: String) {
    var selected by remember(server.domain) { mutableIntStateOf(0) }
    val tabs = listOf(
        tr("mastodon_settings_server_about_tab"),
        tr("mastodon_settings_server_rules"),
        tr("mastodon_settings_server_contact"),
    )
    safeWebUrl(server.thumbnail.orEmpty())?.let { thumbnail ->
        var failed by remember(thumbnail) { mutableStateOf(false) }
        var loading by remember(thumbnail) { mutableStateOf(true) }
        if (!failed) Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.91f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(28.dp))
                AsyncImage(
                    thumbnail,
                    null,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { loading = false },
                    onError = { failed = true },
                )
            }
        }
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(server.title.ifBlank { server.domain }, style = MaterialTheme.typography.headlineSmall)
            Text(server.domain, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (server.version.isNotBlank())
                Text(
                    server.version,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
    PrimaryTabRow(selectedTabIndex = selected) {
        tabs.forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { selected = index }, text = { Text(label) })
        }
    }
    when (selected) {
        0 -> {
            server.activeMonthlyUsers?.let { users ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tr("mastodon_settings_users"), Modifier.weight(1f))
                        Text(users.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            val about = description.ifBlank { server.description }
            if (about.isNotBlank()) HtmlContent(ui, about)
            Text(
                "${server.maxCharacters} characters per post",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { ui.open("https://${ui.api.instance.removePrefix("https://")}/about") }
            ) {
                Text(tr("mastodon_open_in_browser"))
            }
        }
        1 -> {
            if (server.rules.isEmpty()) Empty(tr("mastodon_no_server_rules"))
            server.rules.forEachIndexed { index, rule ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(rule.text, style = MaterialTheme.typography.bodyLarge)
                            if (rule.hint.isNotBlank()) HtmlContent(ui, rule.hint)
                        }
                    }
                }
            }
        }
        2 -> {
            server.contactAccount?.let { account ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Box(Modifier.padding(8.dp)) {
                        AccountRow(account) { ui.navigate(Screen.Profile(account.id)) }
                    }
                }
            }
            if (server.contactEmail.isNotBlank())
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary)
                        SelectionContainer {
                            Text(server.contactEmail, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
        }
    }
}
