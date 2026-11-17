package com.quanquan.quantoot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(emojis) { visibleEmojiGroups(emojis) }
    var selected by remember(groups.keys) { mutableStateOf(groups.keys.firstOrNull()) }
    val shown = if (query.isBlank()) groups[selected].orEmpty()
        else groups.values.flatten().filter { it.shortcode.contains(query, ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("mastodon_emoji"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("mastodon_close")) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("😀", "🎉", "❤️", "👍", "🌱", "✨").forEach { emoji ->
                    FilledTonalButton(onClick = { onSelect(emoji) }, contentPadding = PaddingValues(12.dp)) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(tr("mastodon_search_emojis")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (groups.size > 1 && query.isBlank()) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.keys.forEach { category ->
                    FilterChip(
                        selected = category == selected,
                        onClick = { selected = category },
                        label = { Text(category.ifBlank { tr("mastodon_emoji") }) },
                    )
                }
            }
            when {
                loading -> Loading()
                shown.isEmpty() -> Empty(tr("mastodon_no_custom_emojis"))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(68.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.shortcode }) { emoji ->
                        Surface(
                            onClick = { onSelect(":${emoji.shortcode}:") },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            Column(
                                Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AsyncImage(
                                    safeWebUrl(emoji.staticUrl.ifBlank { emoji.url }),
                                    ":${emoji.shortcode}:", Modifier.size(36.dp),
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
