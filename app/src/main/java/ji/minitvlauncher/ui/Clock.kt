package ji.minitvlauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.util.lerp
import androidx.tv.material3.Text
import ji.minitvlauncher.ui.theme.Manrope
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

private val ClockLarge = TextStyle(
    fontFamily = Manrope,
    fontSize = 128.tx,
    lineHeight = 128.tx,
    fontWeight = FontWeight(300),
    letterSpacing = (-4).tx,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)
private val ClockSmall = ClockLarge.copy(fontSize = 44.tx, lineHeight = 44.tx, fontWeight = FontWeight(600), letterSpacing = (-0.5).tx)

/**
 * Big clock that shrinks into the top-left corner as rows scroll up. Instead of re-laying out text at
 * every size and weight, a light large rendering and a bold small one are scaled and cross-faded.
 */
@Composable
internal fun Clock(collapse: () -> Float, now: () -> Date) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale) }
    val dateFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }

    val date by produceState(now()) {
        while (true) {
            value = now()
            delay(60_000 - value.time % 60_000)
        }
    }
    val time = timeFormat.format(date)

    fun size(e: Float) = lerp(128f, 44f, e)
    fun top(e: Float) = lerp(232f, 74f, e)

    Box(Modifier.fillMaxSize()) {
        ClockText(time, ClockLarge, collapse) { e -> Triple(top(e), size(e) / 128f, 1f - e) }
        ClockText(time, ClockSmall, collapse) { e -> Triple(top(e), size(e) / 44f, e) }
        Text(
            text = dateFormat.format(date),
            style = Type.Date,
            color = Palette.TextMuted,
            modifier = Modifier.graphicsLayer {
                val e = collapse()
                translationX = SIDE.px.toPx()
                translationY = (top(e) + size(e) + 14f - e * 16f).px.toPx()
                alpha = (1f - e * 2.2f).coerceAtLeast(0f)
            },
        )
    }
}

@Composable
private fun ClockText(time: String, style: TextStyle, collapse: () -> Float, place: (Float) -> Triple<Float, Float, Float>) {
    Text(
        text = time,
        style = style,
        color = Palette.Text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            val (top, scale, a) = place(collapse())
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = SIDE.px.toPx()
            translationY = top.px.toPx()
            scaleX = scale
            scaleY = scale
            alpha = a
        },
    )
}
