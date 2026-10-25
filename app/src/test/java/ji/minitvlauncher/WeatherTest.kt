package ji.minitvlauncher

import ji.minitvlauncher.weather.Condition
import ji.minitvlauncher.weather.Weather
import ji.minitvlauncher.weather.parseOpenMeteo
import ji.minitvlauncher.weather.usesFahrenheit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class WeatherTest {
    // Trimmed real response shape from api.open-meteo.com/v1/forecast.
    private val body = """
        {"latitude":21.0,"longitude":105.8,"timezone":"Asia/Bangkok",
         "current":{"time":"2026-10-22T20:45","interval":900,"temperature_2m":23.6,"weather_code":3,"is_day":0},
         "daily":{"time":["2026-10-22"],"temperature_2m_max":[27.4],"temperature_2m_min":[20.6]}}
    """.trimIndent()

    @Test
    fun `parses current and daily values, rounding temperatures`() {
        val w = parseOpenMeteo(body, "Hà Nội", 1000L)
        assertEquals(Weather(24, 27, 21, 3, isDay = false, place = "Hà Nội", fetchedAt = 1000L), w)
        assertEquals(Condition.Cloudy, w.condition)
    }

    @Test
    fun `round-trips through the stored JSON, with and without a place`() {
        val w = parseOpenMeteo(body, "Hà Nội", 1000L)
        assertEquals(w, Weather.fromJson(w.toJson()))
        val noPlace = w.copy(place = null)
        assertEquals(noPlace, Weather.fromJson(noPlace.toJson()))
        assertEquals(null, Weather.fromJson("not json"))
    }

    @Test
    fun `groups WMO codes`() {
        assertEquals(Condition.Clear, Condition.of(0))
        assertEquals(Condition.PartlyCloudy, Condition.of(2))
        assertEquals(Condition.Fog, Condition.of(45))
        assertEquals(Condition.Drizzle, Condition.of(53))
        assertEquals(Condition.Rain, Condition.of(81))
        assertEquals(Condition.Snow, Condition.of(86))
        assertEquals(Condition.Storm, Condition.of(95))
    }

    @Test
    fun `fahrenheit only where it is the norm`() {
        assertTrue(usesFahrenheit(Locale.US))
        assertFalse(usesFahrenheit(Locale("vi", "VN")))
        assertFalse(usesFahrenheit(Locale.UK))
    }
}
