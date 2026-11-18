package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.plainText
import com.quanquan.quantoot.model.MediaAttachment
import com.quanquan.quantoot.model.Status

@Composable
internal fun ComposerHeader(
    title: String,
    onClose: () -> Unit,
    closeEnabled: Boolean = true,
    action: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose, enabled = closeEnabled) { Icon(Icons.Default.Close, tr("mastodon_close")) }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        action()
    }
    HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun ComposerChoice(
    label: String,
    value: String,
    text: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(Modifier.clearAndSetSemantics { contentDescription = "$label, $value" },
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
    }
}

internal fun composerVisibilityIcon(visibility: String): ImageVector = when (PostVisibility.from(visibility)) {
    PostVisibility.Public -> PostIcons.Public
    PostVisibility.Unlisted -> PostIcons.Unlisted
    PostVisibility.Followers -> Icons.Default.Lock
    PostVisibility.Direct -> Icons.Default.Email
    PostVisibility.Unknown -> Icons.Default.Info
}

@Composable
internal fun ComposerContext(post: Status, label: String) {
    val excerpt = when {
        post.spoilerText.isNotBlank() -> post.spoilerText
        post.sensitive -> tr("mastodon_sensitive_content")
        else -> plainText(post.content).trim()
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (excerpt.isNotBlank()) Text(excerpt, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ComposerToolbar(
    count: Int,
    limit: Int,
    enabled: Boolean,
    canAttach: Boolean,
    canPoll: Boolean,
    showWarning: Boolean,
    showPoll: Boolean,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onWarning: (Boolean) -> Unit,
    onPoll: (Boolean) -> Unit,
    onOptions: () -> Unit,
) {
    HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach, enabled = enabled && canAttach) {
                Icon(AttachmentIcon, tr("mastodon_add_media"))
            }
            IconButton(onClick = onEmoji, enabled = enabled) {
                Icon(Icons.Default.Face, tr("mastodon_emoji"))
            }
            IconToggleButton(showPoll, onPoll, enabled = enabled && canPoll) {
                Icon(PostIcons.Poll, tr("mastodon_poll"))
            }
            val warning = tr("mastodon_content_warning")
            IconToggleButton(showWarning, onWarning, enabled = enabled) {
                Text(tr("mastodon_cw_short"), Modifier.clearAndSetSemantics { contentDescription = warning },
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onOptions, enabled = enabled) {
                Icon(Icons.Default.MoreVert, tr("mastodon_post_options"), Modifier.rotate(90f))
            }
        }
        val ink = if (count > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        val description = tr("mastodon_compose_character_count", "count" to count.toString(), "limit" to limit.toString())
        Row(Modifier.padding(start = 8.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(
                progress = { (count.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                color = if (count > limit) ink else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text("$count / $limit", color = ink, style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
internal fun DraftAttachment(media: MediaAttachment, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(mediaPreviewUrl(media), media.description,
            Modifier.size(70.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            if (!media.description.isNullOrBlank()) Text(media.description, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(enabled = enabled, onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(tr(if (media.description.isNullOrBlank()) "mastodon_add_alt_text" else "mastodon_edit_description"))
            }
        }
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(Icons.Default.Close, tr("mastodon_remove_attachment"), Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun PollEditor(draft: ComposerDraft, firstOptionFocus: FocusRequester, enabled: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("mastodon_poll"), style = MaterialTheme.typography.titleMedium)
        draft.options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = tr("mastodon_poll_option_hint", "n" to (index + 1).toString())
                OutlinedTextField(
                    option,
                    { text -> draft.options = draft.options.toMutableList().also { it[index] = text } },
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(label) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).then(if (index == 0) Modifier.focusRequester(firstOptionFocus) else Modifier),
                )
                if (draft.options.size > 2) IconButton(
                    enabled = enabled,
                    onClick = { draft.options = draft.options.filterIndexed { current, _ -> current != index } },
                ) { Icon(Icons.Default.Close, tr("mastodon_remove_poll_option", "n" to (index + 1).toString())) }
            }
        }
        if (draft.options.size < 4) TextButton(onClick = { draft.options = draft.options + "" }, enabled = enabled) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text(tr("mastodon_add_poll_option"))
        }
        Toggle(tr("mastodon_compose_poll_multiple_choice"), draft.multiple) { if (enabled) draft.multiple = it }
        Toggle(tr("mastodon_poll_hide_totals"), draft.hideTotals) { if (enabled) draft.hideTotals = it }
        Choice(tr("mastodon_poll_length"), draft.duration.toString(), listOf(
            "300" to tr("mastodon_minutes", "n" to "5"), "1800" to tr("mastodon_minutes", "n" to "30"),
            "3600" to tr("mastodon_one_hour"), "21600" to tr("mastodon_hours", "n" to "6"),
            "86400" to tr("mastodon_one_day"), "259200" to tr("mastodon_days", "n" to "3"),
            "604800" to tr("mastodon_days", "n" to "7"),
        )) { if (enabled) draft.duration = it.toInt() }
    }
}

private val AttachmentIcon = ImageVector.Builder("Media", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 3.5f)
        lineTo(19f, 3.5f)
        quadTo(21f, 3.5f, 21f, 5.5f)
        lineTo(21f, 18.5f)
        quadTo(21f, 20.5f, 19f, 20.5f)
        lineTo(5f, 20.5f)
        quadTo(3f, 20.5f, 3f, 18.5f)
        lineTo(3f, 5.5f)
        quadTo(3f, 3.5f, 5f, 3.5f)
        close()
        moveTo(3.5f, 16f)
        lineTo(8.5f, 11f)
        lineTo(12.5f, 15f)
        lineTo(15.5f, 12f)
        lineTo(20.5f, 17f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(15.5f, 6.25f)
        arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 3f)
        arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, -3f)
        close()
    }
}.build()
