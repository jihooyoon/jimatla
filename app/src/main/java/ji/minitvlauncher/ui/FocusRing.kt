package ji.minitvlauncher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.TILE_RADIUS
import ji.minitvlauncher.ui.theme.px

/**
 * The design's focus ring (box-shadow 0 0 0 4px): 4 px outside the tile, plus 1 px over its edge
 * so the picture's anti-aliased rim never peeks out past the ring. Drawn after the content and
 * outside the tile's clip; [radius] is the tile's corner radius in design pixels.
 */
internal fun Modifier.focusRing(visible: Boolean, radius: Int = TILE_RADIUS) = drawWithContent {
    drawContent()
    if (!visible) return@drawWithContent
    val outside = 4.px.toPx()
    val overlap = 1.px.toPx()
    val width = outside + overlap
    val inset = (overlap - outside) / 2f // centre of the stroke, negative = outside the bounds
    drawRoundRect(
        color = Palette.TextBright,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(radius.px.toPx() - inset),
        style = Stroke(width),
    )
}
