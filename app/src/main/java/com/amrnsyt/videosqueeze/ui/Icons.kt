package com.amrnsyt.videosqueeze.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {

    /** Two chevrons squeezing a line. */
    val Compress: ImageVector by lazy {
        ImageVector.Builder(
            name = "compress",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 4f); lineTo(12f, 9f); lineTo(17f, 4f)
            moveTo(7f, 20f); lineTo(12f, 15f); lineTo(17f, 20f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }.build()
    }

    /** Photo frame with a badge pill. */
    val Watermark: ImageVector by lazy {
        ImageVector.Builder(
            name = "watermark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        }.path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 15f); lineTo(14f, 15f)
        }.build()
    }
}
