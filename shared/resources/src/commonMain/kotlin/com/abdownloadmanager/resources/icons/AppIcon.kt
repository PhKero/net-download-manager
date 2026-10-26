package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Net Download Manager logo: blue globe with a green download badge (same drawing as icons/icon.ico)
private val GlobeBlue = Color(0xFF1F6FD1)
private val GlobeLines = Color(0xFF9CC7F5)
private val BadgeGreen = Color(0xFF2A9D3A)

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

val ABDMIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon != null) {
            return _AppIcon!!
        }
        _AppIcon = ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 64f,
            viewportHeight = 64f
        ).apply {
            path(fill = SolidColor(GlobeBlue)) { circle(28f, 30f, 22f) }
            // meridian
            path(stroke = SolidColor(GlobeLines), strokeLineWidth = 2.2f) {
                moveTo(19f, 30f)
                arcToRelative(9f, 20.4f, 0f, true, true, 18f, 0f)
                arcToRelative(9f, 20.4f, 0f, true, true, -18f, 0f)
                close()
            }
            // latitudes, shortened to stay inside the globe
            path(stroke = SolidColor(GlobeLines), strokeLineWidth = 2.2f) {
                moveTo(7.6f, 30f); horizontalLineTo(48.4f)
                moveTo(10.5f, 19f); horizontalLineTo(45.5f)
                moveTo(10.5f, 41f); horizontalLineTo(45.5f)
            }
            path(fill = SolidColor(Color.White)) { circle(45f, 45f, 15f) }
            path(fill = SolidColor(BadgeGreen)) { circle(45f, 45f, 13f) }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 3.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(45f, 37f); verticalLineTo(50f)
                moveTo(39.5f, 44.5f); lineTo(45f, 50f); lineTo(50.5f, 44.5f)
            }
        }.build()
        return _AppIcon!!
    }

private var _AppIcon: ImageVector? = null
