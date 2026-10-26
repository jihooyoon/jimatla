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
import android.graphics.Typeface
import androidx.compose.runtime.derivedStateOf
import kotlin.math.roundToInt
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
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

// As rows scroll the clock goes from 128 px / weight 300 / -4 px spacing to 44 px / 600 / -0.5 px,
// with the optical size following the size. Weight and spacing need new text layout, so they move
// in CLOCK_STEPS steps; size and position follow scrolling smoothly through the graphics layer.
private const val CLOCK_STEPS = 12

private fun clockSize(e: Float) = lerp(128f, 44f, e)
private fun clockWeight(e: Float) = lerp(300f, 600f, e).roundToInt()
private fun clockSpacing(e: Float) = lerp(-4f, -0.5f, e)

/** The clock is always laid out at 128 px and scaled down; spacing is expressed in those units. */
private fun clockStyle(e: Float) = TextStyle(
    fontFamily = appFont(clockWeight(e), clockSize(e).roundToInt()),
    fontWeight = FontWeight(clockWeight(e)),
    fontSize = 128.tx,
    lineHeight = 128.tx,
    letterSpacing = (clockSpacing(e) * 128f / clockSize(e)).tx,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Big clock that shrinks into the top-left corner as rows scroll up, with the date and weather. */
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

    fun size(e: Float) = clockSize(e)
    fun top(e: Float) = lerp(232f, 74f, e)
    val dateText = dateFormat.format(date)
    val step by remember { derivedStateOf { (collapse() * CLOCK_STEPS).roundToInt() / CLOCK_STEPS.toFloat() } }
    val ink = rememberInk(time, dateText, step)

    Box(Modifier.fillMaxSize()) {
        Text(
            text = time,
            style = clockStyle(step),
            color = Palette.Text,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                val e = collapse()
                transformOrigin = TransformOrigin(0f, 0f)
                // Optical alignment: shift left by the digits' own left side bearing so their ink
                // starts where the date's does, and at x = 120 (the rows' edge) once small.
                translationX = (SIDE - ink.clockBearing * size(e) + ink.dateBearing * (1f - e)).px.toPx()
                // Compose trims the line to the font's ascent; CSS line-height: 1 centres it instead.
                translationY = (top(e) - ink.baselineLift * size(e)).px.toPx()
                scaleX = size(e) / 128f
                scaleY = scaleX
            },
        )
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

private val WeatherText = TextStyle(fontFamily = appFont(24), fontSize = 24.tx, lineHeight = 34.tx)
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

/**
 * Measured from the font itself with Paint, as the clock is drawn at this step: the left side
 * bearing (per string, since the first digit decides it), the date's bearing, and how far to lift
 * the line so the baseline sits where CSS line-height: 1 puts it, all in units of the font size.
 */
private class Ink(val clockBearing: Float, val dateBearing: Float, val baselineLift: Float)

private fun flexPaint(typeface: Typeface?, size: Float, weight: Int, spacingPx: Float = 0f, tabular: Boolean = false) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        fontVariationSettings = "'wght' $weight, 'opsz' ${size.roundToInt().coerceIn(14, 128)}"
        letterSpacing = spacingPx / size
        if (tabular) fontFeatureSettings = "tnum"
    }

private fun Paint.leftBearing(text: String) =
    if (text.isEmpty()) 0f else Rect().also { getTextBounds(text, 0, text.length, it) }.left.coerceAtLeast(0).toFloat()

@Composable
private fun rememberFlexTypeface(): Typeface? {
    val context = LocalContext.current
    return remember { runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull() }
}

@Composable
private fun rememberInk(time: String, date: String, step: Float): Ink {
    val typeface = rememberFlexTypeface()
    return remember(time, date, step, typeface) {
        // Laid out at 128 with the step's weight, spacing and optical size, as the Text is.
        val clock = flexPaint(typeface, 128f, clockWeight(step), clockSpacing(step) * 128f / clockSize(step), tabular = true)
            .apply { fontVariationSettings = "'wght' ${clockWeight(step)}, 'opsz' ${clockSize(step).roundToInt()}" }
        val m = clock.fontMetrics
        Ink(
            clockBearing = clock.leftBearing(time) / 128f,
            dateBearing = flexPaint(typeface, 28f, 400).leftBearing(date),
            baselineLift = ((m.descent - m.ascent) / 128f - 1f) / 2f,
        )
    }
}

/** Where the search pill sits beside the shrunken clock: past its ink, plus 32 (design px). */
@Composable
internal fun collapsedPillShift(now: () -> Date): Float {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale) }
    val time = format.format(now())
    val typeface = rememberFlexTypeface()
    return remember(time, typeface) {
        val small = flexPaint(typeface, 44f, clockWeight(1f), clockSpacing(1f), tabular = true)
        -small.leftBearing(time) + small.measureText(time) + 32f
    }
}
