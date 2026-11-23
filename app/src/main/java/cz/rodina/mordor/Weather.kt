package cz.rodina.mordor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DayWeather(val code: Int, val precip: Double, val sunshineH: Double, val tmax: Double, val daylightH: Double = 10.0)
data class Place(val name: String, val lat: Double, val lon: Double)

/** Počasí z Open-Meteo (zdarma, bez klíče). */
object Weather {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun geocode(name: String): Place? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=cs&format=json"))
        val arr = j.optJSONArray("results") ?: return@withContext null
        if (arr.length() == 0) return@withContext null
        val o = arr.getJSONObject(0)
        Place(o.optString("name", name), o.getDouble("latitude"), o.getDouble("longitude"))
    }

    /** Denní počasí za posledních [pastDays] dní (max 92). Klíč = yyyy-MM-dd. */
    suspend fun pastDays(lat: Double, lon: Double, pastDays: Int): Map<String, DayWeather> = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=weather_code,precipitation_sum,sunshine_duration,temperature_2m_max,daylight_duration" +
            "&past_days=${pastDays.coerceIn(1, 92)}&forecast_days=1&timezone=auto"
        val daily = JSONObject(get(url)).getJSONObject("daily")
        val time = daily.getJSONArray("time")
        val code = daily.getJSONArray("weather_code")
        val pr = daily.getJSONArray("precipitation_sum")
        val sun = daily.getJSONArray("sunshine_duration")
        val tmax = daily.getJSONArray("temperature_2m_max")
        val light = daily.optJSONArray("daylight_duration")
        val out = HashMap<String, DayWeather>()
        for (i in 0 until time.length()) {
            if (code.isNull(i)) continue
            out[time.getString(i)] = DayWeather(
                code = code.getInt(i),
                precip = if (pr.isNull(i)) 0.0 else pr.getDouble(i),
                sunshineH = if (sun.isNull(i)) 0.0 else sun.getDouble(i) / 3600.0,
                tmax = if (tmax.isNull(i)) 10.0 else tmax.getDouble(i),
                daylightH = if (light == null || light.isNull(i)) 10.0 else light.getDouble(i) / 3600.0,
            )
        }
        out
    }
}
