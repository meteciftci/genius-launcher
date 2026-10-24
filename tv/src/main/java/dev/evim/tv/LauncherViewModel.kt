package dev.evim.tv

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadb.AdbAuthException
import dadb.AdbConnectException
import dev.evim.tv.data.AppRepository
import dev.evim.tv.data.City
import dev.evim.tv.data.Forecast
import dev.evim.tv.data.LauncherApp
import dev.evim.tv.data.LauncherPrefs
import dev.evim.tv.data.RamInfo
import dev.evim.tv.data.SystemStatus
import dev.evim.tv.data.Weather
import dev.evim.tv.data.WeatherClient
import dev.evim.tv.data.home.HomeDevice
import dev.evim.tv.data.home.SmartHome
import dev.evim.tv.data.home.withName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dil değişince de doğru görünsün diye metin değil kaynak kimliği tutulur. */
class RamMessage(@StringRes val text: Int, vararg val args: Any)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val prefs = LauncherPrefs(app)
    private val home = SmartHome(app)
    // Tuya cihazları aynı anda tek yerel bağlantı kabul edebiliyor; sorgu ve komutlar sırayla gider
    private val homeLock = Mutex()
    private var installed: List<LauncherApp> = emptyList()

    var apps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set
    var hiddenApps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set
    var ram by mutableStateOf(RamInfo(0f, 0))
        private set
    var status by mutableStateOf(SystemStatus(wifiLevel = null, bluetoothOn = false))
        private set
    /** Boşalt düğmesinde geçici olarak gösterilen durum ("+72 MB" gibi); null ise düğme normal. */
    var ramMessage by mutableStateOf<RamMessage?>(null)
        private set
    private var freeRamJob: Job? = null
    var forecast by mutableStateOf<Forecast?>(null)
        private set
    val weather: Weather? get() = forecast?.current
    var city by mutableStateOf(prefs.city)
        private set
    var citySearchResults by mutableStateOf<List<City>>(emptyList())
        private set
    var citySearching by mutableStateOf(false)
        private set

    var homeDevices by mutableStateOf<List<HomeDevice>>(emptyList())
        private set
    var homeConfigured by mutableStateOf(false)
        private set
    private var homeJob: Job? = null

    private var weatherJob: Job? = null
    private var searchJob: Job? = null

    init {
        startWeatherUpdates()
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                runCatching { repo.keepAdbAuthorized() }
                delay(ADB_KEEPALIVE_MS)
            }
        }
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

    /** Ekran görünürken cihaz durumlarını düzenli yeniler; [stopHomeUpdates] ile durur. */
    fun startHomeUpdates() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { home.load() } }
            homeConfigured = home.isConfigured
            while (isActive && home.isConfigured) {
                homeDevices = present(homeIo { home.poll() })
                delay(HOME_REFRESH_MS)
            }
        }
    }

    fun stopHomeUpdates() {
        homeJob?.cancel()
    }

    fun toggleRelay(device: HomeDevice.Relay) = updateDevice { home.toggleRelay(device) }

    fun setPurifier(device: HomeDevice.Purifier, values: Map<String, String>) = updateDevice { home.setPurifier(device, values) }

    fun feed(device: HomeDevice.Feeder, portions: Int) {
        viewModelScope.launch { runCatching { homeIo { home.feed(device, portions) } } }
    }

    private fun updateDevice(action: () -> HomeDevice) {
        viewModelScope.launch {
            val updated = present(listOf(homeIo(action))).single()
            homeDevices = homeDevices.map { if (it.key == updated.key) updated else it }
        }
    }

    /** Cihazı widget sırasında [delta] kadar kaydırır (-1 yukarı, +1 aşağı). */
    fun moveDevice(device: HomeDevice, delta: Int) {
        val keys = homeDevices.map { it.key }.toMutableList()
        val from = keys.indexOf(device.key)
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        if (from < 0 || from == to) return
        keys.add(to, keys.removeAt(from))
        prefs.deviceOrder = keys
        homeDevices = present(homeDevices)
    }

    /** Boş ad verilirse cihazın kendi adına döner. */
    fun renameDevice(device: HomeDevice, name: String?) {
        prefs.setDeviceName(device.key, name)
        // Kendi adı bir sonraki yoklamada geri gelir; o zamana kadar listedeki ad kalsın
        homeDevices = present(homeDevices)
        if (name.isNullOrBlank()) startHomeUpdates()
    }

    fun hasCustomName(device: HomeDevice) = prefs.deviceName(device.key) != null

    /** Kullanıcının sırasını ve verdiği adları uygular. */
    private fun present(devices: List<HomeDevice>): List<HomeDevice> {
        val order = prefs.deviceOrder
        // Sıralanmamış yeni cihazlar sona, geldikleri sırayla eklenir
        return devices
            .sortedBy { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { device -> prefs.deviceName(device.key)?.let(device::withName) ?: device }
    }

    private suspend fun <T> homeIo(block: () -> T): T = withContext(Dispatchers.IO) { homeLock.withLock { block() } }

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

    fun openAccessorySettings() {
        viewModelScope.launch(Dispatchers.IO) { repo.openAccessorySettings() }
    }

    fun freeRam() {
        if (freeRamJob?.isActive == true) return
        freeRamJob = viewModelScope.launch {
            ramMessage = RamMessage(R.string.ram_freeing)
            // İlk seferde TV izin soruyor; birkaç saniyede bitmediyse kullanıcıyı uyar
            val hint = launch {
                delay(2500)
                ramMessage = RamMessage(R.string.ram_approve_on_tv)
            }
            val result = runCatching { withContext(Dispatchers.IO) { repo.freeRam() } }
            hint.cancel()
            refreshRam()
            ramMessage = result.fold(
                onSuccess = { freed -> if (freed > 0) RamMessage(R.string.ram_freed, freed) else RamMessage(R.string.ram_already_free) },
                onFailure = { error ->
                    when (error) {
                        is AdbAuthException -> RamMessage(R.string.ram_denied)
                        is AdbConnectException -> RamMessage(R.string.ram_adb_off)
                        else -> RamMessage(R.string.ram_failed)
                    }
                },
            )
            delay(4000)
            ramMessage = null
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
        const val HOME_REFRESH_MS = 15_000L
        // ADB izni 7 gün kullanılmazsa düşüyor; günde bir yenilemek yeterli
        const val ADB_KEEPALIVE_MS = 24 * 60 * 60 * 1000L
    }
}
