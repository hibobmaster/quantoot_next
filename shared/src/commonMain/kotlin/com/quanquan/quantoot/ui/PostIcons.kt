package com.quanquan.quantoot.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PostIcons {
    val Poll = ImageVector.Builder("Poll", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 20f)
            lineTo(5f, 11f)
            moveTo(12f, 20f)
            lineTo(12f, 4f)
            moveTo(19f, 20f)
            lineTo(19f, 8f)
            moveTo(3f, 20f)
            lineTo(21f, 20f)
        }
    }.build()
    val Public = ImageVector.Builder("Public", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(21f, 12f)
            curveTo(21f, 17f, 17f, 21f, 12f, 21f)
            curveTo(7f, 21f, 3f, 17f, 3f, 12f)
            curveTo(3f, 7f, 7f, 3f, 12f, 3f)
            curveTo(17f, 3f, 21f, 7f, 21f, 12f)
            close()
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(12f, 3f)
            curveTo(7f, 8f, 7f, 16f, 12f, 21f)
            curveTo(17f, 16f, 17f, 8f, 12f, 3f)
        }
    }.build()
    val Unlisted = ImageVector.Builder("Unlisted", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(17f, 10f)
            lineTo(17f, 7f)
            curveTo(17f, 4.8f, 15.2f, 3f, 13f, 3f)
            curveTo(10.8f, 3f, 9f, 4.8f, 9f, 7f)
            moveTo(5f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 21f)
            lineTo(5f, 21f)
            close()
            moveTo(12f, 14f)
            lineTo(12f, 17f)
        }
    }.build()
    val Parent = ImageVector.Builder("Parent", 24.dp, 24.dp, 24f, 24f, autoMirror = true).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(20f, 18f)
            verticalLineTo(14f)
            curveTo(20f, 10.7f, 17.3f, 8f, 14f, 8f)
            horizontalLineTo(4f)
            moveTo(9f, 3f)
            lineTo(4f, 8f)
            lineTo(9f, 13f)
        }
    }.build()
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
    val Bookmarked = ImageVector.Builder("Bookmarked", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 3f)
            lineTo(19f, 3f)
            lineTo(19f, 22f)
            lineTo(12f, 17.5f)
            lineTo(5f, 22f)
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
