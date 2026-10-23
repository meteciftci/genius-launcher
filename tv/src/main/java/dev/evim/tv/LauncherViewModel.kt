package dev.evim.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.evim.tv.data.AppRepository
import dev.evim.tv.data.City
import dev.evim.tv.data.Forecast
import dev.evim.tv.data.LauncherApp
import dev.evim.tv.data.LauncherPrefs
import dev.evim.tv.data.RamInfo
import dev.evim.tv.data.SystemStatus
import dev.evim.tv.data.Weather
import dev.evim.tv.data.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val prefs = LauncherPrefs(app)
    private var installed: List<LauncherApp> = emptyList()

    var apps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set
    var hiddenApps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set
    var ram by mutableStateOf(RamInfo(0f, 0))
        private set
    var status by mutableStateOf(SystemStatus(wifiLevel = null, bluetoothOn = false))
        private set
    var forecast by mutableStateOf<Forecast?>(null)
        private set
    val weather: Weather? get() = forecast?.current
    var city by mutableStateOf(prefs.city)
        private set
    var citySearchResults by mutableStateOf<List<City>>(emptyList())
        private set
    var citySearching by mutableStateOf(false)
        private set

    private var weatherJob: Job? = null
    private var searchJob: Job? = null

    init {
        startWeatherUpdates()
    }

    fun selectCity(selected: City) {
        prefs.city = selected
        city = selected
        forecast = null
        startWeatherUpdates()
    }

    /** Kumandayla yazarken her harfte istek atmamak için kısa bir bekleme ile arar. */
    fun searchCity(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            citySearchResults = emptyList()
            citySearching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            citySearching = true
            citySearchResults = runCatching { withContext(Dispatchers.IO) { WeatherClient.searchCities(query) } }
                .getOrDefault(emptyList())
            citySearching = false
        }
    }

    private fun startWeatherUpdates() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (true) {
                forecast = runCatching { withContext(Dispatchers.IO) { WeatherClient.fetch(city) } }
                    .getOrDefault(forecast)
                delay(WEATHER_REFRESH_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            installed = withContext(Dispatchers.Default) { repo.loadApps() }
            publish()
        }
        refreshRam()
    }

    fun refreshRam() {
        ram = repo.ramInfo()
        status = repo.systemStatus()
    }

    fun openSettings(action: String) = repo.openSettings(action)

    fun freeRam() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { repo.freeRam() }
            delay(500)
            refreshRam()
        }
    }

    fun launch(app: LauncherApp) = repo.launch(app.packageName)

    fun openAppInfo(app: LauncherApp) = repo.openAppInfo(app.packageName)

    /** Kartı görünen listede [delta] kadar kaydırır (-1 sola, +1 sağa). */
    fun move(app: LauncherApp, delta: Int) {
        val visible = apps.map { it.packageName }.toMutableList()
        val from = visible.indexOf(app.packageName)
        val to = (from + delta).coerceIn(0, visible.lastIndex)
        if (from < 0 || from == to) return
        visible.add(to, visible.removeAt(from))
        prefs.order = visible + hiddenApps.map { it.packageName }
        publish()
    }

    fun hide(app: LauncherApp) {
        prefs.hidden = prefs.hidden + app.packageName
        publish()
    }

    fun unhide(app: LauncherApp) {
        prefs.hidden = prefs.hidden - app.packageName
        publish()
    }

    /** null: otomatik renge dön. */
    fun setColor(app: LauncherApp, color: Color?) {
        prefs.setCustomColor(app.packageName, color?.toArgb())
        publish()
    }

    private fun publish() {
        val order = prefs.order
        val hidden = prefs.hidden
        val sorted = installed
            .map { app -> app.copy(color = prefs.customColor(app.packageName)?.let(::Color) ?: app.autoColor) }
            .sortedWith(
                compareBy<LauncherApp>({ order.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } })
                    .thenBy { it.label.lowercase() },
            )
        apps = sorted.filter { it.packageName !in hidden }
        hiddenApps = sorted.filter { it.packageName in hidden }
    }

    private companion object {
        const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
    }
}
