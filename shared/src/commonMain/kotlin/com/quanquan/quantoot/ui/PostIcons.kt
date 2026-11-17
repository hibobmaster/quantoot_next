package com.quanquan.quantoot.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PostIcons {
    val Replies = ImageVector.Builder("Replies", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(20f, 11.5f)
            curveTo(20f, 16.2f, 16.2f, 20f, 11.5f, 20f)
            lineTo(4f, 20f)
            lineTo(5.5f, 16.3f)
            curveTo(4.5f, 15f, 4f, 13.3f, 4f, 11.5f)
            curveTo(4f, 6.8f, 7.8f, 3f, 12f, 3f)
            curveTo(16.7f, 3f, 20f, 6.8f, 20f, 11.5f)
            moveTo(8f, 9f)
            lineTo(16f, 9f)
            moveTo(8f, 13f)
            lineTo(13f, 13f)
        }
    }.build()
    val Boost = ImageVector.Builder("Boost", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 8f)
            lineTo(4f, 16f)
            curveTo(4f, 17.1f, 4.9f, 18f, 6f, 18f)
            lineTo(18f, 18f)
            moveTo(15f, 15f)
            lineTo(18f, 18f)
            lineTo(15f, 21f)
            moveTo(20f, 16f)
            lineTo(20f, 8f)
            curveTo(20f, 6.9f, 19.1f, 6f, 18f, 6f)
            lineTo(6f, 6f)
            moveTo(9f, 3f)
            lineTo(6f, 6f)
            lineTo(9f, 9f)
        }
    }.build()
    val Bookmark = ImageVector.Builder("Bookmark", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 4f)
            lineTo(18f, 4f)
            lineTo(18f, 21f)
            lineTo(12f, 17f)
            lineTo(6f, 21f)
            close()
        }
    }.build()
    val Quote = ImageVector.Builder("Quote", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 7f)
            lineTo(4f, 7f)
            lineTo(4f, 13f)
            lineTo(9f, 13f)
            lineTo(9f, 16f)
            lineTo(6f, 19f)
            moveTo(20f, 7f)
            lineTo(15f, 7f)
            lineTo(15f, 13f)
            lineTo(20f, 13f)
            lineTo(20f, 16f)
            lineTo(17f, 19f)
        }
    }.build()
}
