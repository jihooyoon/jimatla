package ji.minitvlauncher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.TILE_RADIUS
import ji.minitvlauncher.ui.theme.px

/**
 * The design's focus ring (box-shadow 0 0 0 4px): 4 px outside the tile, plus 1 px over its edge
 * so the picture's anti-aliased rim never peeks out past the ring. Place it OUTSIDE any scaling
 * layer and pass that layer's [scale] and [origin]: the ring then hugs the scaled tile while staying
 * a crisp 4 px, with the tile's unscaled [radius] (design px) as the design asks.
 */
internal fun Modifier.focusRing(
    visible: Boolean,
    radius: Int = TILE_RADIUS,
    scale: () -> Float = { 1f },
    origin: TransformOrigin = TransformOrigin.Center,
) = drawWithContent {
    drawContent()
    if (!visible) return@drawWithContent
    val s = scale()
    val w = size.width * s
    val h = size.height * s
    val left = origin.pivotFractionX * size.width * (1f - s)
    val top = origin.pivotFractionY * size.height * (1f - s)
    val outside = 4.px.toPx()
    val overlap = 1.px.toPx()
    val inset = (overlap - outside) / 2f // centre of the stroke, negative = outside the tile
    drawRoundRect(
        color = Palette.TextBright,
        topLeft = Offset(left + inset, top + inset),
        size = Size(w - 2 * inset, h - 2 * inset),
        cornerRadius = CornerRadius(radius.px.toPx() - inset),
        style = Stroke(outside + overlap),
    )
}
