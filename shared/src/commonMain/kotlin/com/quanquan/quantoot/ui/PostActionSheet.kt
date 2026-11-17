package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.data.plainText
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostActionSheet(
    ui: UiContext,
    status: Status,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    fun choose(action: () -> Unit) { onDismiss(); action() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AccountRow(status.account) { choose { ui.navigate(Screen.Profile(status.account.id)) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PostActionRow(tr("mastodon_reply_to", "name" to "@${status.account.acct}"), PostIcons.Replies) {
                choose { ui.requireLogin { ui.navigate(Screen.Compose(reply = status)) } }
            }
            PostActionRow(tr("mastodon_button_share"), Icons.Default.Share) {
                choose { ui.platform.share(safeWebUrl(status.url.orEmpty()) ?: plainText(status.content)) }
            }
            PostActionRow(tr("mastodon_open_in_browser"), Icons.AutoMirrored.Filled.ArrowForward) {
                choose { ui.open(status.url) }
            }
            if (ui.me?.id == status.account.id) {
                PostActionRow(tr("mastodon_edit"), Icons.Default.Edit) {
                    choose { ui.navigate(Screen.Compose(edit = status)) }
                }
                PostActionRow(tr("mastodon_delete"), Icons.Default.Delete, destructive = true) {
                    choose(onDelete)
                }
            } else if (ui.me != null) {
                PostActionRow(tr("mastodon_mute"), Icons.Default.Notifications) {
                    choose { ui.run {
                        ui.api.relationshipAction(status.account.id, "mute")
                        ui.message = "Muted @${status.account.acct}"
                    } }
                }
                PostActionRow(tr("mastodon_block"), Icons.Default.Close, destructive = true) {
                    choose { ui.run {
                        ui.api.relationshipAction(status.account.id, "block")
                        ui.message = "Blocked @${status.account.acct}"
                    } }
                }
                PostActionRow(tr("mastodon_report"), Icons.Default.Warning, destructive = true) {
                    choose(onReport)
                }
            }
        }
    }
}

@Composable
private fun PostActionRow(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            Text(label, Modifier.weight(1f), color = color)
        }
    }
}
