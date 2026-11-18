package com.quanquan.quantoot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.CustomEmoji

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerSheet(
    emojis: List<CustomEmoji>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(emojis) { visibleEmojiGroups(emojis) }
    var selected by remember(groups.keys) { mutableStateOf(groups.keys.firstOrNull()) }
    val shown = remember(groups, selected, query) {
        if (query.isBlank()) groups[selected].orEmpty()
        else groups.values.flatten().filter { it.shortcode.contains(query.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("mastodon_emoji"), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("mastodon_close")) }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(66.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        query, { query = it },
                        label = { Text(tr("mastodon_search_emojis")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                if (query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("😀", "🎉", "❤️", "👍", "🌱", "✨").forEach { emoji ->
                            Surface(onClick = { onSelect(emoji) }, shape = CircleShape,
                                modifier = Modifier.semantics { role = Role.Button },
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(6.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
                if (groups.size > 1 && query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groups.keys.forEach { category ->
                            FilterChip(selected = category == selected, onClick = { selected = category },
                                label = { Text(category.ifBlank { tr("mastodon_emoji") }) })
                        }
                    }
                }
                when {
                    loading -> item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
                    error != null -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorNotice(error, onRetry) }
                    shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Empty(tr("mastodon_no_custom_emojis"))
                    }
                    else -> items(shown, key = { it.shortcode }) { emoji ->
                        Surface(
                            onClick = { onSelect(":${emoji.shortcode}:") },
                            modifier = Modifier.semantics { role = Role.Button },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AsyncImage(
                                    safeWebUrl(emoji.staticUrl.ifBlank { emoji.url }),
                                    ":${emoji.shortcode}:", Modifier.size(34.dp),
                                )
                                Text(emoji.shortcode, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
