package dev.evim.tv.data.home

import android.util.Log
import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Evim satırında gösterilen cihazlar; [reachable] false ise son denemede cevap vermedi. */
@Immutable
sealed interface HomeDevice {
    val key: String
    val name: String
    val reachable: Boolean

    data class Relay(override val key: String, override val name: String, override val reachable: Boolean, val on: Boolean) : HomeDevice

    data class Feeder(override val key: String, override val name: String, override val reachable: Boolean, val nextMeal: Meal?) : HomeDevice

    data class Purifier(override val key: String, override val name: String, override val reachable: Boolean, val state: DysonState?) : HomeDevice

    /** Eufy kamera: karta basınca canlı görüntü açılır. */
    data class Camera(override val key: String, override val name: String, override val reachable: Boolean) : HomeDevice

    /** Desteklenmeyen tip: yalnızca ağda cevap verip vermediği gösterilir. */
    data class Other(override val key: String, override val name: String, override val reachable: Boolean) : HomeDevice
}

/** Aynı cihazı başka bir adla döner. */
fun HomeDevice.withName(name: String): HomeDevice = when (this) {
    is HomeDevice.Relay -> copy(name = name)
    is HomeDevice.Feeder -> copy(name = name)
    is HomeDevice.Purifier -> copy(name = name)
    is HomeDevice.Other -> copy(name = name)
    is HomeDevice.Camera -> copy(name = name)
}

data class Meal(val hour: Int, val minute: Int, val portions: Int)

/**
 * Cihaz anahtarlarını uygulamanın özel klasöründeki home.json'dan okur ve cihazlarla yerel ağda konuşur.
 * Anahtarlar kaynak kodda değil: akıllı ev panelinden hesapla eklenirken yazılır.
 */
class SmartHome(private val context: Context) {
    private val stored = File(context.filesDir, "home.json")
    private var tuya: Map<String, TuyaEntry> = emptyMap()
    private var dyson: Map<String, DysonEntry> = emptyMap()
    private var cameras: Map<String, CameraEntry> = emptyMap()
    private val discovery = TuyaDiscovery(context)
    private val dysonDiscovery = DysonDiscovery(context)
    private var lastScan = 0L
    private var lastDysonScan = 0L
    private var lastCameraScan = 0L

    val isConfigured: Boolean get() = tuya.isNotEmpty() || dyson.isNotEmpty() || cameras.isNotEmpty()

    fun load() {
        tuya = emptyMap()
        dyson = emptyMap()
        cameras = emptyMap()
        if (!stored.exists()) return
        val json = JSONObject(stored.readText())
        tuya = json.optJSONArray("tuya").objects().associate { t ->
            val category = t.optString("category")
            val entry = TuyaEntry(
                name = t.getString("name"),
                // Eski dosyalarda tip yok; kategoriden çıkarılır
                kind = t.optString("kind").let { kind -> TuyaKind.entries.firstOrNull { it.name == kind } } ?: kindOf(category),
                switchDp = t.optString("switch_dp").ifEmpty { DP_RELAY },
                device = TuyaDevice(t.getString("id"), t.optString("ip"), t.getString("local_key"), t.optString("version").ifEmpty { "3.3" }),
            )
            t.getString("id") to entry
        }
        cameras = json.optJSONArray("eufy").objects().associate { c ->
            c.getString("id") to CameraEntry(c.getString("name"), EufyCamera(c.getString("ip"), c.getString("user"), c.getString("password")))
        }
        dyson = json.optJSONArray("dyson").objects().associate { d ->
            d.getString("serial") to DysonEntry(
                name = d.getString("name"),
                device = DysonDevice(d.getString("serial"), d.getString("credential"), d.getString("product_type"), d.optString("ip")),
            )
        }
    }

    /** Cihazı ayar dosyasından siler; anahtarı da onunla gider. */
    fun remove(key: String) {
        if (!stored.exists()) return
        val json = JSONObject(stored.readText())
        for ((list, id) in listOf("tuya" to "id", "dyson" to "serial", "eufy" to "id")) {
            val kept = json.optJSONArray(list).objects().filter { it.optString(id) != key }
            json.put(list, JSONArray(kept))
        }
        stored.writeText(json.toString())
        load()
    }

    /**
     * Hesaptan seçilen Tuya cihazlarını ekler; zaten ekliyse anahtarı, adresi ve tipi yenilenir
     * (yeniden bağlanma da budur). [lan] ev ağında bulunanların adresleri; bulunamayanlar sonra aranır.
     */
    fun addTuya(devices: List<TuyaAccountDevice>, lan: Map<String, TuyaLanDevice>) {
        val json = if (stored.exists()) JSONObject(stored.readText()) else JSONObject()
        val byId = json.optJSONArray("tuya").objects().associateBy { it.getString("id") }.toMutableMap()
        for (device in devices) {
            val entry = byId[device.id] ?: JSONObject().put("id", device.id).put("name", device.name)
            entry.put("local_key", device.localKey)
                .put("category", device.category)
                .put("product_id", device.productId)
                .put("kind", device.kind.name)
                .put("switch_dp", device.switchDp ?: "")
            lan[device.id]?.let { entry.put("ip", it.ip).put("version", it.version) }
            byId[device.id] = entry
        }
        json.put("tuya", JSONArray(byId.values))
        stored.writeText(json.toString())
        load()
    }

    /** Dyson cihazlarını ekler ya da parolasını yeniler; [ips] ev ağında bulunanların adresleri. */
    fun addDyson(devices: List<DysonAccountDevice>, ips: Map<String, String>) {
        val json = if (stored.exists()) JSONObject(stored.readText()) else JSONObject()
        val bySerial = json.optJSONArray("dyson").objects().associateBy { it.getString("serial") }.toMutableMap()
        for (device in devices) {
            val entry = bySerial[device.serial] ?: JSONObject().put("serial", device.serial).put("name", device.name)
            entry.put("credential", device.credential).put("product_type", device.productType)
            ips[device.serial]?.let { entry.put("ip", it) }
            bySerial[device.serial] = entry
        }
        json.put("dyson", JSONArray(bySerial.values))
        stored.writeText(json.toString())
        load()
    }

    val dysonSerials: Set<String> get() = dyson.keys

    /**
     * Kamerayı ekler; aynı P2P kimliği (ya da kimlik bilinmiyorsa aynı adres) zaten varsa adresi, kullanıcı adı
     * ve parolası yenilenir. [did] kameranın P2P kimliği; adresi değişince kamera bununla yeniden bulunur.
     */
    fun addCamera(name: String, camera: EufyCamera, user: String, password: String, did: String?) {
        val json = if (stored.exists()) JSONObject(stored.readText()) else JSONObject()
        val list = json.optJSONArray("eufy").objects().toMutableList()
        val entry = list.firstOrNull { (did != null && it.optString("did") == did) || it.optString("ip") == camera.ip }
            ?: JSONObject().put("id", "eufy-" + java.util.UUID.randomUUID().toString().take(8)).put("name", name).also { list += it }
        entry.put("ip", camera.ip).put("user", user).put("password", password)
        if (did != null) entry.put("did", did)
        json.put("eufy", JSONArray(list))
        stored.writeText(json.toString())
        load()
    }

    fun streamUrl(key: String): String? = cameras[key]?.camera?.streamUrl

    fun cameraIp(key: String): String? = cameras[key]?.camera?.ip

    /** Hangi Tuya cihazlarının eklendiği; yeniden bağlanırken seçili gelirler. */
    val tuyaIds: Set<String> get() = tuya.keys

    /** Tüm cihazları sorar; cevap vermeyenler [HomeDevice.reachable] = false olarak döner. */
    fun poll(): List<HomeDevice> {
        var tuyaDevices = pollTuya(tuya)
        // Cevap vermeyen varsa adresi değişmiş olabilir: ağ dinlenir, adresi değişenler yeniden sorulur
        val unreachable = tuyaDevices.filter { !it.reachable }.map { it.key }.toSet()
        if (unreachable.isNotEmpty() && rediscover(unreachable)) {
            val again = pollTuya(tuya.filterKeys { it in unreachable }).associateBy { it.key }
            tuyaDevices = tuyaDevices.map { again[it.key] ?: it }
        }
        var purifiers = dyson.map { (key, entry) -> purifier(key, entry) { entry.device.state() } }
        val lost = purifiers.filter { !it.reachable }.map { it.key }.toSet()
        if (lost.isNotEmpty() && rediscoverDyson(lost)) {
            val again = dyson.filterKeys { it in lost }.map { (key, entry) -> purifier(key, entry) { entry.device.state() } }.associateBy { it.key }
            purifiers = purifiers.map { again[it.key] ?: it }
        }
        var cams = cameras.map { (key, entry) -> HomeDevice.Camera(key, entry.name, entry.camera.reachable()) }
        if (cams.any { !it.reachable } && rediscoverCameras()) {
            cams = cameras.map { (key, entry) -> HomeDevice.Camera(key, entry.name, entry.camera.reachable()) }
        }
        return purifiers + cams + tuyaDevices
    }

    private fun pollTuya(entries: Map<String, TuyaEntry>): List<HomeDevice> = entries.map { (key, entry) ->
        when (entry.kind) {
            TuyaKind.FEEDER -> feeder(key, entry) { entry.device.status() }
            TuyaKind.RELAY, TuyaKind.SWITCH -> relay(key, entry) { entry.device.status() }
            TuyaKind.STATUS -> HomeDevice.Other(key, entry.name, reachable = runCatching { entry.device.status() }.isSuccess)
        }
    }.sortedBy { if (it is HomeDevice.Feeder) 0 else 1 }

    /** Adresi değişen cihaz bulunduysa true; ağ en fazla birkaç dakikada bir dinlenir. */
    private fun rediscover(keys: Set<String>): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastScan < RESCAN_MS) return false
        lastScan = now
        val found = runCatching { discovery.scan() }.getOrDefault(emptyMap())
        val json = JSONObject(stored.readText())
        var changed = false
        json.optJSONArray("tuya").objects().filter { it.getString("id") in keys }.forEach { entry ->
            val lan = found[entry.getString("id")] ?: return@forEach
            if (lan.ip != entry.optString("ip") || lan.version != entry.optString("version")) {
                entry.put("ip", lan.ip).put("version", lan.version)
                changed = true
            }
        }
        if (changed) {
            stored.writeText(json.toString())
            load()
        }
        return changed
    }

    /** Cevap vermeyen kameraları P2P kimlikleriyle ev ağında arar; adresi değişen bulunduysa true. */
    private fun rediscoverCameras(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCameraScan < RESCAN_MS || !stored.exists()) return false
        lastCameraScan = now
        val found = runCatching { EufyDiscovery.scan() }.getOrDefault(emptyMap())
        val json = JSONObject(stored.readText())
        var changed = false
        json.optJSONArray("eufy").objects().forEach { entry ->
            val ip = found[entry.optString("did")] ?: return@forEach
            if (ip != entry.optString("ip")) {
                entry.put("ip", ip)
                changed = true
            }
        }
        if (changed) {
            stored.writeText(json.toString())
            load()
        }
        return changed
    }

    private fun rediscoverDyson(serials: Set<String>): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastDysonScan < RESCAN_MS) return false
        lastDysonScan = now
        val found = runCatching { dysonDiscovery.scan(serials) }.getOrDefault(emptyMap())
        val json = JSONObject(stored.readText())
        var changed = false
        json.optJSONArray("dyson").objects().filter { it.getString("serial") in serials }.forEach { entry ->
            val ip = found[entry.getString("serial")] ?: return@forEach
            if (ip != entry.optString("ip")) {
                entry.put("ip", ip)
                changed = true
            }
        }
        if (changed) {
            stored.writeText(json.toString())
            load()
        }
        return changed
    }

    fun toggleRelay(device: HomeDevice.Relay): HomeDevice {
        val entry = tuya.getValue(device.key)
        val dp = entry.switchDp
        return relay(device.key, entry) {
            val changed = entry.device.set(mapOf(dp to !device.on))
            mapOf(dp to (changed[dp] ?: !device.on))
        }
    }

    /** Komutu gönderir; cihazın cevabı ya da hata loga yazılır ki mama verilmediğinde sebebi görülebilsin. */
    fun feed(device: HomeDevice.Feeder, portions: Int) {
        runCatching { tuya.getValue(device.key).device.set(mapOf(DP_MANUAL_FEED to portions)) }
            .onSuccess { Log.i(TAG, "${device.name}: $portions porsiyon gönderildi, cevap $it") }
            .onFailure { Log.w(TAG, "${device.name}: $portions porsiyon verilemedi", it) }
            .getOrThrow()
    }

    fun setPurifier(device: HomeDevice.Purifier, values: Map<String, String>): HomeDevice {
        val entry = dyson.getValue(device.key)
        return purifier(device.key, entry) { entry.device.set(values) }
    }

    private fun relay(key: String, entry: TuyaEntry, read: () -> Map<String, Any>): HomeDevice.Relay {
        val dps = runCatching(read).getOrNull()
        return HomeDevice.Relay(key, entry.name, reachable = dps != null, on = dps?.get(entry.switchDp) == true)
    }

    private fun feeder(key: String, entry: TuyaEntry, read: () -> Map<String, Any>): HomeDevice.Feeder {
        val dps = runCatching(read).getOrNull()
        val plan = (dps?.get(DP_MEAL_PLAN) as? String)?.let(::parseMealPlan).orEmpty()
        return HomeDevice.Feeder(key, entry.name, reachable = dps != null, nextMeal = nextMeal(plan))
    }

    private fun purifier(key: String, entry: DysonEntry, read: () -> DysonState): HomeDevice.Purifier {
        val state = runCatching(read).getOrNull()
        return HomeDevice.Purifier(key, entry.name, reachable = state != null, state = state)
    }

    private class TuyaEntry(val name: String, val kind: TuyaKind, val switchDp: String, val device: TuyaDevice)

    private class DysonEntry(val name: String, val device: DysonDevice)

    private class CameraEntry(val name: String, val camera: EufyCamera)

    companion object {
        private const val TAG = "GeniusHome"
        private const val DP_RELAY = "1"
        // Uygulamadan elle besleme yapılırken dinlenerek bulundu: porsiyon sayısı yazılır
        private const val DP_MANUAL_FEED = "243"
        private const val DP_MEAL_PLAN = "237"
        private const val RESCAN_MS = 3 * 60_000L
        private val RELAY_CATEGORIES = setOf("tdq", "kg", "cz", "pc")
        private val FEEDER_CATEGORIES = setOf("sp", "cwwsq")

        private fun kindOf(category: String) = when (category) {
            in RELAY_CATEGORIES -> TuyaKind.RELAY
            in FEEDER_CATEGORIES -> TuyaKind.FEEDER
            else -> TuyaKind.STATUS
        }

        /**
         * Öğün planı 15 karakterlik kayıtlardan oluşur: gün maskesi (2), saat (2), dakika (2),
         * porsiyon (2), açık mı (1) ve 6 karakter dolgu. Örn. "7f0700021000000" = her gün 07:00, 2 porsiyon.
         */
        fun parseMealPlan(plan: String): List<Meal> = plan.chunked(15).filter { it.length == 15 && it[8] == '1' }.mapNotNull {
            val hour = it.substring(2, 4).toIntOrNull() ?: return@mapNotNull null
            val minute = it.substring(4, 6).toIntOrNull() ?: return@mapNotNull null
            val portions = it.substring(6, 8).toIntOrNull(16) ?: return@mapNotNull null
            Meal(hour, minute, portions)
        }

        fun nextMeal(plan: List<Meal>, now: Calendar = Calendar.getInstance()): Meal? {
            val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val sorted = plan.sortedBy { it.hour * 60 + it.minute }
            return sorted.firstOrNull { it.hour * 60 + it.minute > minuteOfDay } ?: sorted.firstOrNull()
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
