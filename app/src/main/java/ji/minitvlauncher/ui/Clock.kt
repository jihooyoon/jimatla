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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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

// As rows scroll the clock goes from 128 px / weight 400 / -4 px spacing to 44 px / 600 / -0.5 px,
// with the optical size following the size. (The design's large weight is 300; 400 at the user's
// request.) Weight and spacing need new text layout, so they move in CLOCK_STEPS steps, prepared
// ahead of time; size and position follow scrolling smoothly through the graphics layer.
private const val CLOCK_STEPS = 6
private val STEP_VALUES = (0..CLOCK_STEPS).map { it / CLOCK_STEPS.toFloat() }

private fun clockSize(e: Float) = lerp(128f, 44f, e)
private fun clockWeight(e: Float) = lerp(400f, 600f, e).roundToInt()
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
    val inks = rememberInks(time, dateText)
    PrepareClockSteps(time)

    Box(Modifier.fillMaxSize()) {
        ClockDigits(time, collapse, inks)
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
 * Only this recomposes when the weight step changes, not the date or weather beside it.
 * Position and scale are read in the layer, so scrolling within a step costs no layout.
 */
@Composable
private fun ClockDigits(time: String, collapse: () -> Float, inks: List<Ink>?) {
    val step by remember { derivedStateOf { (collapse() * CLOCK_STEPS).roundToInt() } }
    val e0 = STEP_VALUES[step]
    val ink = inks?.get(step) ?: Ink.None
    Text(
        text = time,
        style = clockStyle(e0),
        color = Palette.Text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            val e = collapse()
            val size = clockSize(e)
            transformOrigin = TransformOrigin(0f, 0f)
            // Optical alignment: shift left by the digits' own left side bearing so their ink
            // starts where the date's does, and at x = 120 (the rows' edge) once small.
            translationX = (SIDE - ink.clockBearing * size + ink.dateBearing * (1f - e)).px.toPx()
            // Compose trims the line to the font's ascent; CSS line-height: 1 centres it instead.
            translationY = (lerp(232f, 74f, e) - ink.baselineLift * size).px.toPx()
            scaleX = size / 128f
            scaleY = scaleX
        },
    )
}

/**
 * Loads every step's font instance and draws each step's digits once off screen, in the
 * background, so the first scroll doesn't stall on font loading and glyph rasterising.
 */
@Composable
private fun PrepareClockSteps(time: String) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    LaunchedEffect(time) {
        val layouts = withContext(Dispatchers.Default) { STEP_VALUES.map { measurer.measure(time, clockStyle(it), density = density) } }
        // Big enough to hold the glyphs: drawing outside the canvas would skip rasterising them.
        val bitmap = ImageBitmap(layouts.maxOf { it.size.width }.coerceAtLeast(1), layouts.maxOf { it.size.height }.coerceAtLeast(1))
        val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        for (layout in layouts) {
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) { drawText(layout) }
            yield()
        }
    }
}

/**
 * Measured from the font itself with Paint, as the clock is drawn at this step: the left side
 * bearing (per string, since the first digit decides it), the date's bearing, and how far to lift
 * the line so the baseline sits where CSS line-height: 1 puts it, all in units of the font size.
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

/** Every step's measurements, computed off the main thread whenever the time or date changes. */
@Composable
private fun rememberInks(time: String, date: String): List<Ink>? {
    val typeface = rememberFlexTypeface()
    return produceState<List<Ink>?>(null, time, date, typeface) {
        value = withContext(Dispatchers.Default) {
            val dateBearing = flexPaint(typeface, 28f, 400).leftBearing(date)
            STEP_VALUES.map { e ->
                // Laid out at 128 with the step's weight, spacing and optical size, as the Text is.
                val clock = flexPaint(typeface, 128f, clockWeight(e), clockSpacing(e) * 128f / clockSize(e), tabular = true)
                    .apply { fontVariationSettings = "'wght' ${clockWeight(e)}, 'opsz' ${clockSize(e).roundToInt()}" }
                val m = clock.fontMetrics
                Ink(clock.leftBearing(time) / 128f, dateBearing, ((m.descent - m.ascent) / 128f - 1f) / 2f)
            }
        }
    }.value
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
