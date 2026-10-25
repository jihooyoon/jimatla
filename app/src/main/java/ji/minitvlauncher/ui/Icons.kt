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
    val Check = stroke(2f, "M5 12.5l4.5 4.5L19 7.5")

    // Top bar (stroke 1.6)
    val Input = stroke(1.6f, "M14 4h5a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-5", "M3 12h11", "M10 8l4 4-4 4")
    val Bluetooth = stroke(1.6f, "M7 7l10 10-5 5V2l5 5L7 17")
    val Wifi = stroke(1.6f, WIFI_1, WIFI_2, WIFI_3, fill = listOf(circle(12f, 20f, 1f)))
    val WifiWeak = layered(1.6f, faint = listOf(WIFI_1, WIFI_2), solid = listOf(WIFI_3), fill = listOf(circle(12f, 20f, 1f)))
    val WifiFaint = layered(1.6f, faint = listOf(WIFI_1, WIFI_2, WIFI_3), solid = emptyList(), fill = listOf(circle(12f, 20f, 1f)))
    val WifiOff = layered(1.6f, faint = listOf(WIFI_1, WIFI_2, WIFI_3), solid = listOf("M3 3l18 18"))
    val Ethernet = stroke(1.6f, "M4 5h16v11h-4v3H8v-3H4z", "M8.5 9v3", "M12 9v3", "M15.5 9v3")
    val Bell = stroke(1.6f, "M6 9.5a6 6 0 0 1 12 0c0 5 2 6.5 2 6.5H4s2-1.5 2-6.5", "M10 19.5a2.2 2.2 0 0 0 4 0")
    val Wallpaper = stroke(1.6f, "M3 4.5h18v15H3z", "M3 16l5-5 4 4 3-3 6 6", circle(16f, 9f, 1.6f))
    val Settings = stroke(
        1.6f, circle(12f, 12f, 3.2f), "M12 2.5v3", "M12 18.5v3", "M2.5 12h3", "M18.5 12h3",
        "M5.3 5.3l2.1 2.1", "M16.6 16.6l2.1 2.1", "M5.3 18.7l2.1-2.1", "M16.6 7.4l2.1-2.1",
    )
    val Search = stroke(1.8f, circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2")
    val Mic = stroke(1.6f, "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21")
    val Close = stroke(1.8f, "M6 6l12 12", "M18 6L6 18")

    private const val WIFI_1 = "M2 8.8a15 15 0 0 1 20 0"
    private const val WIFI_2 = "M5 12.6a10 10 0 0 1 14 0"
    private const val WIFI_3 = "M8.5 16.4a5 5 0 0 1 7 0"

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun stroke(width: Float, vararg paths: String, fill: List<String> = emptyList()): ImageVector =
        layered(width, faint = emptyList(), solid = paths.toList(), fill = fill)

    /** [faint] strokes are drawn at a third of the opacity, like the design's weak-signal arcs. */
    private fun layered(width: Float, faint: List<String>, solid: List<String>, fill: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                fun line(d: String, alpha: Float) = addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeAlpha = alpha,
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                faint.forEach { line(it, 0.33f) }
                solid.forEach { line(it, 1f) }
                fill.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
            }
            .build()
}
