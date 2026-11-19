package com.quanquan.quantoot.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal enum class UpdateSymbol { Working, Download, Complete, Error }

internal fun <T> updateSpring() = spring<T>(dampingRatio = 0.82f, stiffness = 450f)

@Composable
internal fun UpdateStatusBadge(symbol: UpdateSymbol) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (symbol) {
            UpdateSymbol.Working, UpdateSymbol.Download -> colors.primaryContainer
            UpdateSymbol.Complete -> colors.primary
            UpdateSymbol.Error -> colors.errorContainer
        },
        tween(220),
        label = "Update badge tone",
    )
    val corner by animateDpAsState(
        when (symbol) {
            UpdateSymbol.Working -> 22.dp
            UpdateSymbol.Download, UpdateSymbol.Error -> 16.dp
            UpdateSymbol.Complete -> 28.dp
        },
        updateSpring(),
        label = "Update badge shape",
    )
    Surface(Modifier.size(56.dp), shape = RoundedCornerShape(corner), color = container) {
        AnimatedContent(
            targetState = symbol,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                (fadeIn(tween(160, delayMillis = 45)) + scaleIn(updateSpring(), initialScale = 0.72f))
                    .togetherWith(fadeOut(tween(105)))
            },
            label = "Update badge symbol",
        ) { visibleSymbol ->
            when (visibleSymbol) {
                UpdateSymbol.Working -> UpdateLoadingMark(colors.onPrimaryContainer)
                UpdateSymbol.Download -> UpdateDownloadMark(colors.onPrimaryContainer)
                UpdateSymbol.Complete -> UpdateCheckMark(colors.onPrimary)
                UpdateSymbol.Error -> Icon(Icons.Default.Info, null, Modifier.size(24.dp), tint = colors.onErrorContainer)
            }
        }
    }
}

@Composable
private fun UpdateLoadingMark(color: Color) {
    val loop = rememberInfiniteTransition(label = "Update loading mark")
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "Morph")
    val outline = remember { Path() }
    Canvas(Modifier.size(34.dp)) {
        val turn = phase * 2 * PI
        val morph = (1 - cos(turn)) / 2
        outline.reset()
        repeat(64) { point ->
            val angle = point * 2 * PI / 64
            val lobes = cos(4 * angle) * (1 - morph) + cos(6 * angle) * morph
            val radius = size.width * (0.42 + lobes * 0.065)
            val x = center.x + (radius * cos(angle + turn)).toFloat()
            val y = center.y + (radius * sin(angle + turn)).toFloat()
            if (point == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
        }
        outline.close()
        drawPath(outline, color)
    }
}

@Composable
private fun UpdateDownloadMark(color: Color) {
    Canvas(Modifier.size(26.dp)) {
        val stroke = 2.3.dp.toPx()
        val width = size.width
        drawLine(color, Offset(width * .5f, width * .12f), Offset(width * .5f, width * .66f), stroke, StrokeCap.Round)
        val outline = Path().apply {
            moveTo(width * .28f, width * .45f)
            lineTo(width * .5f, width * .67f)
            lineTo(width * .72f, width * .45f)
            moveTo(width * .20f, width * .87f)
            lineTo(width * .80f, width * .87f)
        }
        drawPath(outline, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun UpdateCheckMark(color: Color) {
    val trace = remember { Animatable(0f) }
    LaunchedEffect(trace) { trace.animateTo(1f, tween(310, delayMillis = 55)) }
    Canvas(Modifier.size(28.dp)) {
        val width = size.width
        val start = Offset(width * .16f, width * .53f)
        val joint = Offset(width * .39f, width * .76f)
        val end = Offset(width * .85f, width * .28f)
        val progress = trace.value
        val stroke = 2.8.dp.toPx()
        val bend = .34f
        if (progress > 0f) {
            drawLine(color, start, lerp(start, joint, min(progress / bend, 1f)), stroke, StrokeCap.Round)
        }
        if (progress > bend) {
            drawLine(color, joint, lerp(joint, end, (progress - bend) / (1f - bend)), stroke, StrokeCap.Round)
        }
    }
}

@Composable
internal fun UpdateWaveProgress(progress: Float) {
    val colors = MaterialTheme.colorScheme
    val displayed by animateFloatAsState(progress, spring(dampingRatio = 1f, stiffness = 300f), label = "Download progress")
    val loop = rememberInfiniteTransition(label = "Download wave")
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(950, easing = LinearEasing)), label = "Wave phase")
    val wave = remember { Path() }
    Canvas(Modifier.fillMaxWidth().height(18.dp).progressSemantics(progress)) {
        val stroke = 3.5.dp.toPx()
        val start = stroke / 2f
        val finish = size.width - start
        val leading = start + (finish - start) * displayed.coerceIn(0f, 1f)
        val amplitude = 3.2.dp.toPx()
        val wavelength = 22.dp.toPx()
        scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
            val trackStart = if (leading > start) leading + 8.dp.toPx() else start
            if (trackStart < finish) {
                drawLine(colors.surfaceContainerHighest, Offset(trackStart, center.y), Offset(finish, center.y), stroke, StrokeCap.Round)
            }
            if (leading > start) {
                val turn = phase * 2 * PI
                wave.reset()
                wave.moveTo(start, center.y)
                var x = start + 2.dp.toPx()
                while (x < leading) {
                    val envelope = min(1f, min(x - start, leading - x) / (wavelength / 3f))
                    val y = center.y + sin((x - start) / wavelength * 2 * PI - turn).toFloat() * amplitude * envelope
                    wave.lineTo(x, y)
                    x += 2.dp.toPx()
                }
                wave.lineTo(leading, center.y)
                drawPath(wave, colors.primary, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
