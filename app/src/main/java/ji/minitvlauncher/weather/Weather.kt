package ji.minitvlauncher.weather

import androidx.annotation.StringRes
import ji.minitvlauncher.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** What the weather line shows; temperatures are already in the display unit. */
data class Weather(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
    val place: String?,
    val fetchedAt: Long,
) {
    val condition: Condition get() = Condition.of(code)

    fun toJson(): String = JSONObject()
        .put("t", temperature).put("hi", high).put("lo", low).put("code", code).put("day", isDay)
        .put("place", place ?: JSONObject.NULL).put("at", fetchedAt)
        .toString()

    companion object {
        fun fromJson(json: String): Weather? = runCatching {
            val o = JSONObject(json)
            Weather(
                o.getInt("t"), o.getInt("hi"), o.getInt("lo"), o.getInt("code"), o.getBoolean("day"),
                o.optString("place").takeIf { !o.isNull("place") && it.isNotEmpty() }, o.getLong("at"),
            )
        }.getOrNull()
    }
}

/** WMO weather codes grouped the way the line names them. */
enum class Condition(@StringRes val label: Int) {
    Clear(R.string.weather_clear),
    PartlyCloudy(R.string.weather_partly_cloudy),
    Cloudy(R.string.weather_cloudy),
    Fog(R.string.weather_fog),
    Drizzle(R.string.weather_drizzle),
    Rain(R.string.weather_rain),
    Snow(R.string.weather_snow),
    Storm(R.string.weather_storm);

    companion object {
        fun of(code: Int) = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67, in 80..82 -> Rain
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Storm
            else -> Cloudy
        }
    }
}

/** Countries that read temperatures in Fahrenheit. */
fun usesFahrenheit(locale: Locale) = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

/**
 * Open-Meteo: free for non-commercial use, no key. Coordinates are rounded to 0.1° (about 10 km),
 * enough for weather and kinder to privacy. Blocking; call off the main thread.
 */
fun fetchWeather(lat: Double, lon: Double, fahrenheit: Boolean, place: String?, now: Long): Weather {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=%.1f&longitude=%.1f".format(Locale.US, lat, lon) +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto" +
            if (fahrenheit) "&temperature_unit=fahrenheit" else "",
    )
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return parseOpenMeteo(body, place, now)
    } finally {
        connection.disconnect()
    }
}

fun parseOpenMeteo(body: String, place: String?, now: Long): Weather {
    val o = JSONObject(body)
    val current = o.getJSONObject("current")
    val daily = o.getJSONObject("daily")
    return Weather(
        temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
        high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
        low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
        code = current.getInt("weather_code"),
        isDay = current.optInt("is_day", 1) == 1,
        place = place,
        fetchedAt = now,
    )
}
