package ji.minitvlauncher.ui

import androidx.compose.runtime.getValue
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.core.content.res.ResourcesCompat
import androidx.tv.material3.Text
import ji.minitvlauncher.R
import ji.minitvlauncher.ui.theme.Palette
import ji.minitvlauncher.ui.theme.Type
import ji.minitvlauncher.ui.theme.appFont
import ji.minitvlauncher.ui.theme.px
import ji.minitvlauncher.ui.theme.tx
import ji.minitvlauncher.weather.Condition
import ji.minitvlauncher.weather.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

// Big clock: 128 px, weight 400, -7 px spacing (design changelog 5, 8).
private const val CLOCK_SIZE = 128
private const val CLOCK_WEIGHT = 400
private const val CLOCK_SPACING = -7f
private const val CLOCK_TOP = 232
private const val DATE_TOP = CLOCK_TOP + CLOCK_SIZE + 14
private const val WEATHER_TOP = DATE_TOP + 50

private val ClockStyle = TextStyle(
    fontFamily = appFont(CLOCK_WEIGHT, CLOCK_SIZE),
    fontWeight = FontWeight(CLOCK_WEIGHT),
    fontSize = CLOCK_SIZE.tx,
    lineHeight = CLOCK_SIZE.tx,
    letterSpacing = CLOCK_SPACING.tx,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * Clock, date and weather at the top of the content. They scroll with the rows ([scrollY], design
 * px) and fade out over the first 220 px as [fade] says; only layer properties change, so
 * scrolling never re-lays out the text.
 */
@Composable
internal fun Clock(scrollY: () -> Float, fade: () -> Float, now: () -> Date, weather: Weather? = null) {
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
    val dateText = dateFormat.format(date)
    val ink = rememberInk(time, dateText)

    fun Modifier.riding(x: Float, top: Float) = graphicsLayer {
        translationX = x.px.toPx()
        translationY = (top - scrollY()).px.toPx()
        alpha = fade()
    }

    Box(Modifier.fillMaxSize()) {
        Text(
            text = time,
            style = ClockStyle,
            color = Palette.Text,
            maxLines = 1,
            softWrap = false,
            // Optical alignment (changelog 2): shift left by the digits' own left side bearing so
            // their ink starts where the date's does. Compose trims the line to the font's ascent
            // where CSS line-height: 1 centres it, so the line is lifted to match the design.
            modifier = Modifier.riding(
                SIDE - ink.clockBearing + ink.dateBearing,
                CLOCK_TOP - ink.baselineLift,
            ),
        )
        Text(text = dateText, style = Type.Date, color = Palette.TextMuted, modifier = Modifier.riding(SIDE.toFloat(), DATE_TOP.toFloat()))
        if (weather != null) {
            WeatherLine(weather, now = date.time, modifier = Modifier.riding(SIDE.toFloat(), WEATHER_TOP.toFloat()))
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
 * Measured from the font itself with Paint (design px): the clock's left side bearing (per string,
 * since the first digit decides it), the date's, and how far to lift the clock's line so its
 * baseline sits where CSS line-height: 1 puts it.
 */
private class Ink(val clockBearing: Float, val dateBearing: Float, val baselineLift: Float) {
    companion object {
        val None = Ink(0f, 0f, 0f)
    }
}

private fun flexPaint(typeface: Typeface?, size: Float, weight: Int, spacingPx: Float = 0f, tabular: Boolean = false) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        fontVariationSettings = "'wght' $weight, 'opsz' ${size.toInt().coerceIn(14, 128)}"
        letterSpacing = spacingPx / size
        if (tabular) fontFeatureSettings = "tnum"
    }

private fun Paint.leftBearing(text: String) =
    if (text.isEmpty()) 0f else Rect().also { getTextBounds(text, 0, text.length, it) }.left.coerceAtLeast(0).toFloat()

@Composable
private fun rememberInk(time: String, date: String): Ink {
    val context = LocalContext.current
    val typeface = remember { runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull() }
    return produceState(Ink.None, time, date, typeface) {
        value = withContext(Dispatchers.Default) {
            // Without letter spacing: Paint offsets the first glyph by half of it, Compose doesn't.
            val clock = flexPaint(typeface, CLOCK_SIZE.toFloat(), CLOCK_WEIGHT, tabular = true)
            val m = clock.fontMetrics
            Ink(
                clockBearing = clock.leftBearing(time),
                dateBearing = flexPaint(typeface, 28f, 400).leftBearing(date),
                baselineLift = ((m.descent - m.ascent) - CLOCK_SIZE) / 2f,
            )
        }
    }.value
}
