package com.quanquan.quantoot.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.scrollableArea
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.getScrollViewportLength
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileScrollLayout(
    state: ProfileScrollState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSelect: (ProfileSelection) -> Unit,
    header: @Composable () -> Unit,
    body: ProfileBodyScope.(ProfileSelection) -> Unit,
) {
    val childBringIntoView = LocalBringIntoViewSpec.current
    Layout(
        modifier = modifier
            .clipToBounds()
            .onPlaced { state.coordinates = it }
            .semantics {
                isTraversalGroup = true
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { state.accessibilityOffset },
                    maxValue = { state.accessibilityOffset + if (state.canScrollForward) 100f else 0f },
                )
                getScrollViewportLength { (state.geometry.viewportHeight - state.geometry.categoryHeight).toFloat() }
            }
            .scrollableArea(state, Orientation.Vertical, bringIntoViewSpec = ParentOwnsBringIntoView),
        content = {
            Box(Modifier.fillMaxWidth().then(ProfileRevealElement(state, ProfileRegion.Header))) { header() }
            Box(Modifier.fillMaxWidth().then(ProfileRevealElement(state, ProfileRegion.Categories))) {
                ProfileCategoryRow(state.selection, onSelect)
            }
            key(state.bodyState) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides ParentOwnsBringIntoView) {
                    LazyColumn(
                        state = state.bodyState,
                        userScrollEnabled = false,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProfileBodyScope(this, state, childBringIntoView).body(state.selection)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        require(constraints.hasBoundedHeight && constraints.hasBoundedWidth)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val natural = Constraints(minWidth = width, maxWidth = width)
        val headerPlaceable = measurables[0].measure(natural)
        val categories = measurables[1].measure(natural)
        val bodyPlaceable = measurables[2].measure(Constraints.fixed(width, (height - categories.height).coerceAtLeast(0)))
        state.measured(headerPlaceable.height, categories.height, height)
        layout(width, height) {
            val geometry = state.geometry
            headerPlaceable.placeRelative(0, -geometry.collapsed.roundToInt())
            bodyPlaceable.placeRelative(0, geometry.bodyTop.roundToInt())
            categories.placeRelative(0, geometry.categoryTop.roundToInt())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCategoryRow(selection: ProfileSelection, onSelect: (ProfileSelection) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selection.category.ordinal,
        modifier = Modifier.testTag("profile:categories"),
        edgePadding = 0.dp,
        minTabWidth = 68.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        ProfileCategory.entries.forEach { category ->
            val active = selection.category == category
            Tab(
                selected = active,
                onClick = { onSelect(selection.copy(category = category)) },
                modifier = Modifier.testTag("profile:category:${category.name}"),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(tr(category.titleKey), style = MaterialTheme.typography.labelLarge, softWrap = false,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

internal class ProfileBodyScope internal constructor(
    private val lazy: LazyListScope,
    private val state: ProfileScrollState,
    private val childBringIntoView: BringIntoViewSpec,
) {
    @OptIn(ExperimentalFoundationApi::class)
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable () -> Unit) {
        lazy.item(key, contentType) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides childBringIntoView) {
                Box(Modifier.fillMaxWidth().then(ProfileRevealElement(state, ProfileRegion.Body))) { content() }
            }
        }
    }
}

// Disabled LazyColumn gestures still install a focus responder. Only the parent may move it.
private object ParentOwnsBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private data class ProfileRevealElement(val state: ProfileScrollState, val region: ProfileRegion) : ModifierNodeElement<ProfileRevealNode>() {
    override fun create() = ProfileRevealNode(state, region)
    override fun update(node: ProfileRevealNode) { node.state = state; node.region = region }
    override fun InspectorInfo.inspectableProperties() { name = "profileBringIntoView" }
}

private class ProfileRevealNode(var state: ProfileScrollState, var region: ProfileRegion) : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        state.reveal(region) {
            val parent = state.coordinates?.takeIf { it.isAttached } ?: return@reveal null
            val rect = boundsProvider() ?: return@reveal null
            if (!childCoordinates.isAttached) return@reveal null
            rect.translate(parent.localPositionOf(childCoordinates))
        }
        bringIntoView {
            val rect = boundsProvider() ?: return@bringIntoView null
            if (!childCoordinates.isAttached) return@bringIntoView null
            rect.translate(requireLayoutCoordinates().localPositionOf(childCoordinates))
        }
    }
}
