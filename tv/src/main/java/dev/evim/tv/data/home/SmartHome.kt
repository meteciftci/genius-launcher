package dev.evim.tv.data.home

import android.content.Context
import androidx.compose.runtime.Immutable
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
}

/** Aynı cihazı başka bir adla döner. */
fun HomeDevice.withName(name: String): HomeDevice = when (this) {
    is HomeDevice.Relay -> copy(name = name)
    is HomeDevice.Feeder -> copy(name = name)
    is HomeDevice.Purifier -> copy(name = name)
}

data class Meal(val hour: Int, val minute: Int, val portions: Int)

/**
 * Cihaz anahtarlarını uygulamanın özel klasöründeki home.json'dan okur ve cihazlarla yerel ağda konuşur.
 * Anahtarlar kaynak kodda değil: tools/smarthome/push_to_tv.py dosyayı ADB ile uygulamanın harici
 * klasörüne koyar, uygulama ilk fırsatta özel klasörüne taşıyıp kopyayı siler.
 */
class SmartHome(private val context: Context) {
    private val stored = File(context.filesDir, "home.json")
    private var tuya: Map<String, TuyaEntry> = emptyMap()
    private var dyson: Map<String, DysonEntry> = emptyMap()

    val isConfigured: Boolean get() = tuya.isNotEmpty() || dyson.isNotEmpty()

    /** Yeni gönderilmiş bir ayar dosyası varsa içeri alır, sonra ayarları yükler. */
    fun load() {
        context.getExternalFilesDir(null)?.let { File(it, "home.json") }?.takeIf { it.exists() }?.let { incoming ->
            incoming.copyTo(stored, overwrite = true)
            incoming.delete()
        }
        tuya = emptyMap()
        dyson = emptyMap()
        if (!stored.exists()) return
        val json = JSONObject(stored.readText())
        tuya = json.optJSONArray("tuya").objects().associate { t ->
            val entry = TuyaEntry(
                name = t.getString("name"),
                category = t.getString("category"),
                device = TuyaDevice(t.getString("id"), t.getString("ip"), t.getString("local_key"), t.getString("version")),
            )
            t.getString("id") to entry
        }
        dyson = json.optJSONArray("dyson").objects().associate { d ->
            d.getString("serial") to DysonEntry(
                name = d.getString("name"),
                device = DysonDevice(d.getString("serial"), d.getString("credential"), d.getString("product_type"), d.getString("ip")),
            )
        }
    }

    /** Cihazı ayar dosyasından siler; anahtarı da onunla gider. */
    fun remove(key: String) {
        if (!stored.exists()) return
        val json = JSONObject(stored.readText())
        for ((list, id) in listOf("tuya" to "id", "dyson" to "serial")) {
            val kept = json.optJSONArray(list).objects().filter { it.optString(id) != key }
            json.put(list, org.json.JSONArray(kept))
        }
        stored.writeText(json.toString())
        load()
    }

    /** Tüm cihazları sorar; cevap vermeyenler [HomeDevice.reachable] = false olarak döner. */
    fun poll(): List<HomeDevice> {
        val purifiers = dyson.map { (key, entry) -> purifier(key, entry) { entry.device.state() } }
        val feeders = tuya.filter { it.value.isFeeder }.map { (key, entry) -> feeder(key, entry) { entry.device.status() } }
        val relays = tuya.filter { it.value.isRelay }.map { (key, entry) -> relay(key, entry) { entry.device.status() } }
        return purifiers + feeders + relays
    }

    fun toggleRelay(device: HomeDevice.Relay): HomeDevice {
        val entry = tuya.getValue(device.key)
        return relay(device.key, entry) {
            val changed = entry.device.set(mapOf(DP_RELAY to !device.on))
            mapOf(DP_RELAY to (changed[DP_RELAY] ?: !device.on))
        }
    }

    fun feed(device: HomeDevice.Feeder, portions: Int) {
        tuya.getValue(device.key).device.set(mapOf(DP_MANUAL_FEED to portions))
    }

    fun setPurifier(device: HomeDevice.Purifier, values: Map<String, String>): HomeDevice {
        val entry = dyson.getValue(device.key)
        return purifier(device.key, entry) { entry.device.set(values) }
    }

    private fun relay(key: String, entry: TuyaEntry, read: () -> Map<String, Any>): HomeDevice.Relay {
        val dps = runCatching(read).getOrNull()
        return HomeDevice.Relay(key, entry.name, reachable = dps != null, on = dps?.get(DP_RELAY) == true)
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

    private class TuyaEntry(val name: String, val category: String, val device: TuyaDevice) {
        val isRelay get() = category in RELAY_CATEGORIES
        val isFeeder get() = category in FEEDER_CATEGORIES
    }

    private class DysonEntry(val name: String, val device: DysonDevice)

    companion object {
        private const val DP_RELAY = "1"
        // Uygulamadan elle besleme yapılırken dinlenerek bulundu: porsiyon sayısı yazılır
        private const val DP_MANUAL_FEED = "243"
        private const val DP_MEAL_PLAN = "237"
        private val RELAY_CATEGORIES = setOf("tdq", "kg", "cz", "pc")
        private val FEEDER_CATEGORIES = setOf("sp", "cwwsq")

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

        private fun org.json.JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
