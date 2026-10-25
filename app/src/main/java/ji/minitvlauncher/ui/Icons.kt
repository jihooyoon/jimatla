package ji.minitvlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons copied from the design's inline SVGs (24×24 viewBox, round caps and joins).
 * Drawn in black; tint them with the Icon/Image color filter.
 */
object Icons {
    val Play = stroke(1.8f, "M8 5.5v13l10-6.5z")
    val Star = stroke(1.8f, "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z")
    val StarOff = stroke(1.8f, "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z", "M3 3l18 18")
    val Arrange = stroke(1.8f, "M7 8l-4 4 4 4", "M17 8l4 4-4 4", "M3 12h18")
    val Info = stroke(1.8f, circle(12f, 12f, 9f), "M12 11v5", fill = listOf(circle(12f, 7.8f, 0.6f)))
    val ChevronLeft = stroke(2.2f, "M15 5l-7 7 7 7")
    val ChevronRight = stroke(2.2f, "M9 5l7 7-7 7")

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun stroke(width: Float, vararg paths: String, fill: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach {
                    addPath(
                        PathParser().parsePathString(it).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
                fill.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
            }
            .build()
}
