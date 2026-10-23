package dev.evim.tv.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class City(val name: String, val latitude: Double, val longitude: Double) {
    companion object {
        val DEFAULT = City("Muğla", 37.22, 28.36)
    }
}

data class Weather(val temperature: Int, val symbol: String)

/** Open-Meteo: ücretsiz, API anahtarı gerektirmez. */
object WeatherClient {
    fun fetch(city: City): Weather {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,weather_code",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            val current = JSONObject(connection.inputStream.bufferedReader().readText()).getJSONObject("current")
            return Weather(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                symbol = symbolFor(current.getInt("weather_code")),
            )
        } finally {
            connection.disconnect()
        }
    }

    // WMO hava durumu kodları
    private fun symbolFor(code: Int) = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3, 45, 48 -> "☁"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "☁"
    }
}
