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
import dev.evim.tv.data.HomeSwitch
import dev.evim.tv.data.AdbState
import dev.evim.tv.data.CleanupApp
import dev.evim.tv.data.Setup
import dev.evim.tv.data.SwitchCheck
import dev.evim.tv.data.Forecast
import dev.evim.tv.data.LauncherApp
import dev.evim.tv.data.LauncherPrefs
import dev.evim.tv.data.RamInfo
import dev.evim.tv.data.SystemStatus
import dev.evim.tv.data.Weather
import dev.evim.tv.data.WeatherClient
import dev.evim.tv.data.home.HomeDevice
import dev.evim.tv.data.home.SmartHome
import dev.evim.tv.data.home.DysonAccountDevice
import dev.evim.tv.data.home.DysonChallenge
import dev.evim.tv.data.home.DysonCloud
import dev.evim.tv.data.home.DysonDiscovery
import dev.evim.tv.data.home.EufyCamera
import dev.evim.tv.data.home.EufyDiscovery
import dev.evim.tv.data.home.TuyaAccountDevice
import dev.evim.tv.data.home.TuyaCloud
import dev.evim.tv.data.home.TuyaDiscovery
import dev.evim.tv.data.home.TuyaLanDevice
import dev.evim.tv.data.home.TuyaSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val homeSwitch = HomeSwitch(app)
    private val setup = Setup(app)
    private val tuyaCloud = TuyaCloud(BuildConfig.TUYA_CLIENT_ID, BuildConfig.TUYA_SCHEMA)
    private val tuyaDiscovery = TuyaDiscovery(app)
    private val dysonDiscovery = DysonDiscovery(app)
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

    /** Home tuşu Genius Launcher'ı mı açıyor; ekrana dönülünce yenilenir. */
    var geniusIsHome by mutableStateOf(false)
        private set
    /** İlk açılışta kurulum sihirbazı kendiliğinden gösterilir. */
    var setupPending by mutableStateOf(!prefs.setupShown)
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

    fun removeDevice(device: HomeDevice) {
        viewModelScope.launch {
            homeIo { home.remove(device.key) }
            prefs.forgetDevice(device.key)
            homeDevices = homeDevices.filter { it.key != device.key }
            homeConfigured = home.isConfigured
        }
    }

    /** İstemci kimliği local.properties'te yoksa Tuya ile ekleme kapalıdır. */
    val tuyaAvailable: Boolean get() = BuildConfig.TUYA_CLIENT_ID.isNotEmpty()

    val tuyaUserCode: String get() = prefs.tuyaUserCode

    /** QR girişini başlatır; okutulacak QR içeriği ve beklenecek jeton döner. */
    suspend fun tuyaStartLogin(userCode: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val token = tuyaCloud.startLogin(userCode)
        prefs.tuyaUserCode = userCode
        tuyaCloud.qrContent(token) to token
    }

    /** Kullanıcı uygulamada onaylayana kadar bekler; süre dolarsa null. */
    suspend fun tuyaAwaitLogin(token: String, userCode: String): TuyaSession? = withContext(Dispatchers.IO) {
        repeat(TUYA_LOGIN_POLLS) {
            runCatching { tuyaCloud.loginResult(token, userCode) }.getOrNull()?.let { return@withContext it }
            delay(2_000)
        }
        null
    }

    /** Hesaptaki cihazlar ve ev ağında duyulanlar; ikisi aynı anda yapılır. */
    suspend fun tuyaCandidates(session: TuyaSession): Pair<List<TuyaAccountDevice>, Map<String, TuyaLanDevice>> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val lan = async { runCatching { tuyaDiscovery.scan() }.getOrDefault(emptyMap()) }
                val devices = tuyaCloud.devices(session)
                devices to lan.await()
            }
        }

    val tuyaAddedIds: Set<String> get() = home.tuyaIds

    suspend fun addTuya(devices: List<TuyaAccountDevice>, lan: Map<String, TuyaLanDevice>) {
        homeIo { home.addTuya(devices, lan) }
        homeConfigured = home.isConfigured
        startHomeUpdates()
    }

    suspend fun dysonRequestCode(email: String, country: String): DysonChallenge =
        withContext(Dispatchers.IO) { DysonCloud.requestCode(email, country) }

    /** Giriş ve ev ağı taraması; ağda bulunan cihazların adresleriyle döner. */
    suspend fun dysonVerify(challenge: DysonChallenge, code: String, password: String): Pair<List<DysonAccountDevice>, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val devices = DysonCloud.verify(challenge, code, password)
            devices to dysonScan(devices)
        }

    suspend fun dysonFromSticker(ssid: String, password: String): Pair<List<DysonAccountDevice>, Map<String, String>>? =
        withContext(Dispatchers.IO) {
            val device = DysonCloud.fromSticker(ssid, password) ?: return@withContext null
            listOf(device) to dysonScan(listOf(device))
        }

    private fun dysonScan(devices: List<DysonAccountDevice>) =
        runCatching { dysonDiscovery.scan(devices.map { it.serial }.toSet()) }.getOrDefault(emptyMap())

    val dysonAddedSerials: Set<String> get() = home.dysonSerials

    /**
     * Kamerayı ekler. [ip] verilmezse ev ağında Eufy kameraları aranır ve kullanıcı adı ile parolayı kabul eden
     * ilki alınır; hiç kamera bulunamazsa UNREACHABLE döner ve arayüz adresi sorar.
     */
    suspend fun addCamera(name: String, ip: String?, user: String, password: String): EufyCamera.Check = withContext(Dispatchers.IO) {
        val found = runCatching { EufyDiscovery.scan() }.getOrDefault(emptyMap())
        val candidates = if (ip != null) listOf(found.entries.firstOrNull { it.value == ip }?.key to ip) else found.map { it.key to it.value }
        var result = EufyCamera.Check.UNREACHABLE
        for ((did, address) in candidates) {
            val camera = EufyCamera(address, user, password)
            val check = camera.check()
            if (check == EufyCamera.Check.OK) {
                homeIo { home.addCamera(name, camera, user, password, did) }
                withContext(Dispatchers.Main) {
                    homeConfigured = home.isConfigured
                    startHomeUpdates()
                }
                return@withContext check
            }
            // Birden çok kamera varsa en anlamlı hata gösterilir: parola reddi, yayın yok, cevap yok
            if (check == EufyCamera.Check.WRONG_LOGIN || result == EufyCamera.Check.UNREACHABLE) result = check
        }
        result
    }

    fun cameraStream(device: HomeDevice.Camera): String? = home.streamUrl(device.key)

    suspend fun addDyson(devices: List<DysonAccountDevice>, ips: Map<String, String>) {
        homeIo { home.addDyson(devices, ips) }
        homeConfigured = home.isConfigured
        startHomeUpdates()
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
        geniusIsHome = homeSwitch.isGeniusHome
    }

    /** Genius Launcher'a geçmeden önce Wi-Fi denetimi; hata olursa ikinci parametrede gösterilecek metin. */
    fun checkHomeSwitch(onResult: (SwitchCheck?, Int?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { homeSwitch.check() } }
            onResult(result.getOrNull(), result.exceptionOrNull()?.let(::errorText))
        }
    }

    /** [fromWizard] ise sihirbazın geri alabilmesi için kaydedilir. */
    fun setGeniusHome(enabled: Boolean, fromWizard: Boolean = false, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (enabled) homeSwitch.toGenius() else homeSwitch.toGoogle()
                    if (enabled && fromWizard) setup.recordHome()
                }
            }
            geniusIsHome = homeSwitch.isGeniusHome
            onDone(result.exceptionOrNull()?.let(::errorText))
        }
    }

    /**
     * Başarılı olursa uygulama kaldırılır ve bu süreç sonlanır; yalnızca hata geri bildirilir.
     * [restoreSetup] ise önce sihirbazın kaldırdığı uygulamalar ve değiştirdiği ayarlar geri getirilir.
     */
    fun uninstall(restoreSetup: Boolean, onError: (Int) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (restoreSetup) setup.undo(includeHome = false)
                    homeSwitch.uninstall()
                }
            }.onFailure { onError(errorText(it)) }
        }
    }

    fun finishSetup() {
        prefs.setupShown = true
        setupPending = false
    }

    val isXiaomi: Boolean get() = setup.isXiaomi

    suspend fun adbState(): AdbState = withContext(Dispatchers.IO) { setup.adbState() }

    suspend fun cleanupCandidates(): List<CleanupApp> = withContext(Dispatchers.IO) { setup.cleanupCandidates() }

    suspend fun removeApps(packages: List<String>): Int =
        withContext(Dispatchers.IO) { setup.removeApps(packages) }.also { refresh() }

    suspend fun keepAwake(): Int? = runCatching { withContext(Dispatchers.IO) { setup.keepAwake() } }.exceptionOrNull()?.let(::errorText)

    /** Sihirbazın yaptıkları: Home, kaldırılan uygulama sayısı, uyanık kal. Ayarlar her açılışta yeniden okur. */
    suspend fun setupChanges(): Triple<Boolean, Int, Boolean> = withContext(Dispatchers.IO) {
        Triple(setup.changedHome, setup.removedApps.size, setup.keptAwake)
    }

    suspend fun undoSetup(): Int? {
        val error = runCatching { withContext(Dispatchers.IO) { setup.undo(includeHome = true) } }.exceptionOrNull()
        geniusIsHome = homeSwitch.isGeniusHome
        refresh()
        return error?.let(::errorText)
    }

    @StringRes
    private fun errorText(error: Throwable): Int = when (error) {
        is AdbAuthException -> R.string.adb_needs_permission
        is AdbConnectException -> R.string.adb_off
        else -> R.string.action_failed
    }

    fun refreshRam() {
        ram = repo.ramInfo()
        status = repo.systemStatus()
    }

    fun openSettings(action: String) = repo.openSettings(action)

    fun startScreensaver() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.startScreensaver() } }
    }

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
        // 2 saniyede bir, 3 dakika
        const val TUYA_LOGIN_POLLS = 90
        // ADB izni 7 gün kullanılmazsa düşüyor; günde bir yenilemek yeterli
        const val ADB_KEEPALIVE_MS = 24 * 60 * 60 * 1000L
    }
}
