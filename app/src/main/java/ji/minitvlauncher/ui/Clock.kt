package ji.minitvlauncher.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import ji.minitvlauncher.R
import ji.minitvlauncher.weather.Condition
import ji.minitvlauncher.weather.Weather
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
internal fun Clock(collapse: () -> Float, now: () -> Date, weather: Weather? = null) {
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
    val dateText = dateFormat.format(date)
    val ink = rememberInk(time, dateText)
    // Optical alignment: digits have a wide left side bearing. Shift each rendering left by its own
    // bearing so the ink starts where the date's ink does, and at x = 120 (the rows' edge) once small.
    fun left(bearingEm: Float, e: Float) = SIDE - bearingEm * size(e) + ink.dateBearing * (1f - e)

    Box(Modifier.fillMaxSize()) {
        // Compose sets the trimmed line lower than CSS line-height: 1 does; lift the digits so their
        // baseline lands where the design's does (345 at 128 px, from Manrope's 2132/600 metrics).
        fun digitsTop(e: Float) = top(e) - BASELINE_LIFT * size(e)
        ClockText(time, ClockLarge, collapse) { e -> ClockPlace(left(ink.largeBearing, e), digitsTop(e), size(e) / 128f, 1f - e) }
        ClockText(time, ClockSmall, collapse) { e -> ClockPlace(left(ink.smallBearing, e), digitsTop(e), size(e) / 44f, e) }
        Text(
            text = dateText,
            style = Type.Date,
            color = Palette.TextMuted,
            modifier = Modifier.graphicsLayer {
                val e = collapse()
                translationX = SIDE.px.toPx()
                translationY = (top(e) + size(e) + 14f - e * 16f).px.toPx()
                alpha = (1f - e * 2.2f).coerceAtLeast(0f)
            },
        )
        if (weather != null) {
            WeatherLine(
                weather,
                now = date.time,
                modifier = Modifier.graphicsLayer {
                    val e = collapse()
                    translationX = SIDE.px.toPx()
                    translationY = (top(e) + size(e) + 14f + 50f - e * 20f).px.toPx()
                    alpha = (1f - e * 2.6f).coerceAtLeast(0f)
                },
            )
        }
    }
}

private const val BASELINE_LIFT = 24f / 128f

private val WeatherText = TextStyle(fontFamily = Manrope, fontSize = 24.tx, lineHeight = 34.tx)
private const val STALE_MS = 2 * 60 * 60 * 1000L

/** "☁ 24°  Nhiều mây  ·  Hà Nội  ·  27° / 21°", plus when it was fetched if that was a while ago. */
@Composable
private fun WeatherLine(weather: Weather, now: Long, modifier: Modifier) {
    val icon = when (weather.condition) {
        Condition.Clear -> if (weather.isDay) Icons.Sun else Icons.Moon
        Condition.PartlyCloudy -> if (weather.isDay) Icons.CloudSun else Icons.Cloud
        Condition.Cloudy -> Icons.Cloud
        Condition.Fog -> Icons.Fog
        Condition.Drizzle, Condition.Rain -> Icons.Rain
        Condition.Snow -> Icons.Snow
        Condition.Storm -> Icons.Storm
    }
    val details = buildList {
        weather.place?.let(::add)
        add("${weather.high}° / ${weather.low}°")
        if (now - weather.fetchedAt > STALE_MS) {
            add(stringResource(R.string.weather_updated, DateUtils.getRelativeTimeSpanString(weather.fetchedAt, now, DateUtils.MINUTE_IN_MILLIS)))
        }
    }
    Row(modifier.height(34.px), horizontalArrangement = Arrangement.spacedBy(14.px), verticalAlignment = Alignment.CenterVertically) {
        Image(
            rememberVectorPainter(icon), null,
            Modifier.size(34.px).offset(y = (-3).px),
            colorFilter = ColorFilter.tint(Palette.TextSecondary),
        )
        Text("${weather.temperature}°", style = WeatherText, color = Palette.Text)
        Text(stringResource(weather.condition.label), style = WeatherText, color = Palette.TextSecondary)
        Text(details.joinToString(prefix = "·  ", separator = "  ·  "), style = WeatherText, color = Palette.TextDim)
    }
}

private class ClockPlace(val left: Float, val top: Float, val scale: Float, val alpha: Float)

/**
 * Left side bearings measured from the font itself (Paint.getTextBounds), per string: the first
 * digit decides it. Large and small clocks are measured at their own weight and spacing.
 */
private class Ink(val largeBearing: Float, val smallBearing: Float, val dateBearing: Float, val smallWidth: Float)

@Composable
private fun rememberInk(time: String, date: String): Ink {
    val context = LocalContext.current
    val typeface = remember { runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() }
    return remember(time, date, typeface) {
        fun paint(size: Float, weight: Int, spacingPx: Float, tabular: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            fontVariationSettings = "'wght' $weight"
            letterSpacing = spacingPx / size
            if (tabular) fontFeatureSettings = "tnum"
        }
        fun bearing(p: Paint, text: String) = Rect().also { p.getTextBounds(text, 0, text.length, it) }.left.coerceAtLeast(0).toFloat()
        val large = paint(128f, 300, -4f, true)
        val small = paint(44f, 600, -0.5f, true)
        Ink(
            largeBearing = bearing(large, time) / 128f,
            smallBearing = bearing(small, time) / 44f,
            dateBearing = bearing(paint(28f, 400, 0f, false), date),
            smallWidth = small.measureText(time),
        )
    }
}

/** Where the search pill sits beside the shrunken clock: past its ink, plus 32. */
@Composable
internal fun collapsedPillShift(now: () -> Date): Float {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale) }.format(now())
    val ink = rememberInk(time, "")
    return -ink.smallBearing * 44f + ink.smallWidth + 32f
}

@Composable
private fun ClockText(time: String, style: TextStyle, collapse: () -> Float, place: (Float) -> ClockPlace) {
    Text(
        text = time,
        style = style,
        color = Palette.Text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            val p = place(collapse())
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = p.left.px.toPx()
            translationY = p.top.px.toPx()
            scaleX = p.scale
            scaleY = p.scale
            alpha = p.alpha
        },
    )
}
