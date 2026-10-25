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
