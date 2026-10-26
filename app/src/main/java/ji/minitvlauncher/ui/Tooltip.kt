package ji.minitvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.tv.material3.Text
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import kotlin.math.roundToInt

private val TooltipText = TextStyle(fontFamily = appFont(20), fontSize = 20.tx, fontWeight = FontWeight.Medium)

/** Keeps tooltips this far from the screen's left and right edges (design px). */
private const val SAFE_EDGE = 48

/**
 * Wraps a round button and, while [label] is non-null, shows it underneath: 80 px below the
 * button's top, centred on it, and nudged sideways only as far as needed to stay inside the
 * 48 px safe edge — measured from the tooltip's real width, since labels such as Wi‑Fi names vary.
 * Takes no layout space, so it never moves neighbouring buttons.
 */
@Composable
internal fun WithTooltip(label: String?, content: @Composable () -> Unit) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val screenWidth = LocalWindowInfo.current.containerSize.width
    Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        content()
        val a = anchor
        if (label != null && a != null) {
            Text(
                label,
                style = TooltipText,
                color = Palette.Background,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .layout { measurable, _ ->
                        val tip = measurable.measure(Constraints())
                        val edge = SAFE_EDGE.px.toPx()
                        val centred = a.center.x - tip.width / 2f
                        val left = centred.coerceIn(edge, (screenWidth - edge - tip.width).coerceAtLeast(edge))
                        layout(0, 0) { tip.place((left - a.left).roundToInt(), 80.px.roundToPx()) }
                    }
                    .background(Palette.TextBright, RoundedCornerShape(10.px))
                    .padding(horizontal = 16.px, vertical = 8.px),
            )
        }
    }
}
