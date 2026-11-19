package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Platform-reported bounds in window pixels, before the app consumes its safe area. */
data class WindowRegion(val left: Float, val top: Float, val right: Float, val bottom: Float, val separates: Boolean)

enum class VerticalControls { Left, Right }

data class AdaptiveWindow(
    val regions: List<WindowRegion> = emptyList(),
    val verticalControls: VerticalControls? = null,
)

internal val LocalAdaptiveWindow = compositionLocalOf { AdaptiveWindow() }

internal data class PaneBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height

    fun centered(maxWidth: Float): PaneBounds {
        val margin = ((width - maxWidth) / 2f).coerceAtLeast(0f)
        return copy(left = left + margin, right = right - margin)
    }
}

internal data class WorkspaceLayout(
    val primary: PaneBounds,
    val secondary: PaneBounds? = null,
    val rail: PaneBounds? = null,
)

/** Choose reading panes from the usable window, with a physical division taking priority. */
internal fun workspaceLayout(
    width: Float,
    height: Float,
    regions: List<WindowRegion>,
    wantsSecondary: Boolean,
    navigation: Boolean,
    rightToLeft: Boolean,
    controls: VerticalControls?,
    singleMaxWidth: Float = 720f,
    splitUnfolded: Boolean = true,
): WorkspaceLayout {
    var usable = PaneBounds(0f, 0f, width, height)
    for (region in regions) {
        if (region.separates || region.right <= usable.left || region.left >= usable.right ||
            region.bottom <= usable.top || region.top >= usable.bottom) continue
        // Reserve the edge nearest a camera or system overlay without shrinking the opposite edge.
        val inset = listOf(
            region.right - usable.left, region.bottom - usable.top,
            usable.right - region.left, usable.bottom - region.top,
        )
        val smallest = inset.minOrNull() ?: continue
        usable = when (inset.indexOf(smallest)) {
            0 -> usable.copy(left = region.right.coerceIn(usable.left, usable.right))
            1 -> usable.copy(top = region.bottom.coerceIn(usable.top, usable.bottom))
            2 -> usable.copy(right = region.left.coerceIn(usable.left, usable.right))
            else -> usable.copy(bottom = region.top.coerceIn(usable.top, usable.bottom))
        }
    }

    val division = regions.firstNotNullOfOrNull { region ->
        if (!region.separates) return@firstNotNullOfOrNull null
        when {
            region.bottom - region.top >= region.right - region.left &&
                region.left > usable.left && region.right < usable.right &&
                region.bottom > usable.top && region.top < usable.bottom ->
                usable.copy(right = region.left) to usable.copy(left = region.right)
            region.top > usable.top && region.bottom < usable.bottom &&
                region.right > usable.left && region.left < usable.right ->
                usable.copy(bottom = region.top) to usable.copy(top = region.bottom)
            else -> null
        }
    }
    val book = division != null && division.first.top == division.second.top
    val canUseDivision = wantsSecondary && division != null &&
        minOf(division.first.width, division.second.width) >= 300f &&
        minOf(division.first.height, division.second.height) >= if (!splitUnfolded) 145f else if (book) 360f else 220f
    if (division != null && !canUseDivision) usable = maxOf(division.first, division.second, compareBy { it.area })

    val railOnRight = controls?.let { it == VerticalControls.Right } ?: rightToLeft
    val railWidth = if (controls == null) 80f else 64f
    val railFits = navigation && usable.height >= 260f &&
        !(division == null && wantsSecondary && usable.width >= 640f && usable.width < 720f && usable.height >= 410f) &&
        (usable.width >= 600f || (usable.width >= 405f && (controls != null || usable.height < 460f)))
    var first: PaneBounds
    var second: PaneBounds? = null
    if (division != null && canUseDivision) {
        val (before, after) = division
        first = if (book && rightToLeft) after else before
        second = if (book && rightToLeft) before else after
    } else {
        first = usable
    }

    val railHost = when {
        book && second != null && railOnRight != rightToLeft -> second
        book && second == null && controls != null ->
            if (railOnRight) division.second else division.first
        else -> first
    }
    val reservedRail = if (railFits && railHost.width >= 300f + railWidth && (second == null || book)) {
        if (railOnRight) railHost.copy(left = railHost.right - railWidth)
        else railHost.copy(right = railHost.left + railWidth)
    } else null
    if (reservedRail != null) {
        val remaining = if (railOnRight) railHost.copy(right = reservedRail.left)
            else railHost.copy(left = reservedRail.right)
        if (railHost == first) first = remaining
        if (railHost == second) second = remaining
    }

    if (division == null && wantsSecondary && splitUnfolded && first.width >= 640f && first.height >= 410f) {
        val bounds = first.centered(1240f)
        val widthOfPrimary = (bounds.width * 0.54f).coerceIn(320f, bounds.width - 320f)
        first = if (rightToLeft) bounds.copy(left = bounds.right - widthOfPrimary + 0.5f)
            else bounds.copy(right = bounds.left + widthOfPrimary - 0.5f)
        second = if (rightToLeft) bounds.copy(right = first.left - 1f)
            else bounds.copy(left = first.right + 1f)
    }
    return WorkspaceLayout(
        first.centered(if (second == null) singleMaxWidth else max(680f, singleMaxWidth)),
        second?.centered(680f),
        reservedRail,
    )
}

@Composable
internal fun AdaptiveWorkspace(
    wantsSecondary: Boolean,
    navigation: Boolean,
    singleMaxWidth: Float = 720f,
    splitUnfolded: Boolean = true,
    content: @Composable BoxScope.(WorkspaceLayout) -> Unit,
) {
    val window = LocalAdaptiveWindow.current
    val density = LocalDensity.current.density
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val layout = remember(maxWidth, maxHeight, window, origin, density, wantsSecondary, navigation, rightToLeft, singleMaxWidth, splitUnfolded) {
            workspaceLayout(maxWidth.value, maxHeight.value, window.regions.map {
                WindowRegion((it.left - origin.x) / density, (it.top - origin.y) / density,
                    (it.right - origin.x) / density, (it.bottom - origin.y) / density, it.separates)
            }, wantsSecondary, navigation, rightToLeft, window.verticalControls, singleMaxWidth, splitUnfolded)
        }
        if (layout.secondary != null && splitUnfolded) {
            Spacer(Modifier.matchParentSize().background(MaterialTheme.colorScheme.outlineVariant))
        }
        content(layout)
    }
}

@Composable
internal fun WorkspacePane(bounds: PaneBounds, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.absoluteOffset(bounds.left.dp, bounds.top.dp)
            .size(bounds.width.dp, bounds.height.dp).clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        content = content,
    )
}
