package dev.evim.tv.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class City(val name: String, val latitude: Double, val longitude: Double, val region: String = "") {
    companion object {
        val DEFAULT = City("Muğla", 37.22, 28.36, "Muğla, Türkiye")
    }
}

data class Weather(
    val temperature: Int,
    val symbol: String,
    val description: String,
    val feelsLike: Int,
    val humidity: Int,
    val windKmh: Int,
)

data class HourlyWeather(val hour: String, val temperature: Int, val symbol: String, val rainChance: Int)

data class DailyWeather(
    val day: String,
    val min: Int,
    val max: Int,
    val symbol: String,
    val description: String,
    val rainChance: Int,
)

data class Forecast(
    val current: Weather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val sunrise: String,
    val sunset: String,
)

/** Open-Meteo: ücretsiz, API anahtarı gerektirmez. */
object WeatherClient {
    private val TR = Locale("tr", "TR")

    fun fetch(city: City): Forecast {
        val json = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${city.latitude}&longitude=${city.longitude}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                "&timezone=auto&forecast_days=7",
        )

        val current = json.getJSONObject("current")
        val currentCode = current.getInt("weather_code")
        val weather = Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            symbol = symbolFor(currentCode, current.optInt("is_day", 1) == 1),
            description = describe(currentCode),
            feelsLike = current.getDouble("apparent_temperature").roundToInt(),
            humidity = current.getInt("relative_humidity_2m"),
            windKmh = current.getDouble("wind_speed_10m").roundToInt(),
        )

        // Saatlik veri günün 00:00'ından başlar; şu anki saatten itibaren 12 saat
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val nowHour = current.getString("time").take(13)
        val start = (0 until times.length()).firstOrNull { times.getString(it).startsWith(nowHour) } ?: 0
        val hours = (start until minOf(start + 12, times.length())).map { i ->
            HourlyWeather(
                hour = if (i == start) "Şimdi" else times.getString(i).substring(11, 16),
                temperature = hourly.getJSONArray("temperature_2m").getDouble(i).roundToInt(),
                symbol = symbolFor(hourly.getJSONArray("weather_code").getInt(i), hourly.getJSONArray("is_day").getInt(i) == 1),
                rainChance = hourly.getJSONArray("precipitation_probability").optInt(i, 0),
            )
        }

        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            val code = daily.getJSONArray("weather_code").getInt(i)
            DailyWeather(
                day = dayLabel(dates.getString(i), i),
                min = daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(),
                symbol = symbolFor(code, isDay = true),
                description = describe(code),
                rainChance = daily.getJSONArray("precipitation_probability_max").optInt(i, 0),
            )
        }

        return Forecast(
            current = weather,
            hourly = hours,
            daily = days,
            sunrise = daily.getJSONArray("sunrise").getString(0).takeLast(5),
            sunset = daily.getJSONArray("sunset").getString(0).takeLast(5),
        )
    }

    /** Şehir adına göre arama; Türkiye sonuçları öne alınır. */
    fun searchCities(query: String): List<City> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=tr")
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length())
            .map { results.getJSONObject(it) }
            // Yalnızca yerleşim yerleri ve idari bölgeler; havalimanı, dağ vb. değil
            .filter { r -> r.optString("feature_code").let { it.startsWith("PPL") || it.startsWith("ADM") } }
            .sortedByDescending { it.optString("country_code") == "TR" }
            .map { r ->
                val country = if (r.optString("country_code") == "TR") "Türkiye" else r.optString("country")
                val region = listOf(r.optString("admin2"), r.optString("admin1"), country)
                    .map { it.removeSuffix(" İlçesi") }
                    .filter { it.isNotBlank() && it != r.getString("name") }
                    .distinct()
                    .joinToString(", ")
                City(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"), region)
            }
    }

    private fun get(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun dayLabel(date: String, index: Int): String = when (index) {
        0 -> "Bugün"
        1 -> "Yarın"
        else -> {
            val parsed = SimpleDateFormat("yyyy-MM-dd", TR).parse(date) ?: return date
            SimpleDateFormat("EEEE", TR).format(Calendar.getInstance().apply { time = parsed }.time)
        }
    }

    // WMO hava durumu kodları
    private fun symbolFor(code: Int, isDay: Boolean) = when (code) {
        0 -> if (isDay) "☀" else "☾"
        1, 2 -> if (isDay) "⛅" else "☁"
        3, 45, 48 -> "☁"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "☁"
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Açık"
        1 -> "Az bulutlu"
        2 -> "Parçalı bulutlu"
        3 -> "Bulutlu"
        45, 48 -> "Sisli"
        in 51..57 -> "Çiseleme"
        in 61..67 -> "Yağmurlu"
        in 71..77 -> "Karlı"
        in 80..82 -> "Sağanak"
        85, 86 -> "Kar sağanağı"
        in 95..99 -> "Gök gürültülü"
        else -> "—"
    }
}
