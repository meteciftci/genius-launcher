package dev.evim.tv.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dadb.AdbAuthException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Temizlikte önerilen ya da seçilebilen bir sistem uygulaması. */
data class CleanupApp(
    val packageName: String,
    val label: String,
    val recommended: Boolean,
    /** Bağlı olunan Wi-Fi ağını bu uygulama kaydetmiş; kaldırılırsa ağ silinir, bu yüzden seçilemez. */
    val ownsCurrentWifi: Boolean,
)

enum class AdbState { READY, NEEDS_PERMISSION, OFF }

/**
 * İlk kurulum sihirbazının yaptıkları: Home tuşu, sistem uygulaması temizliği ve "uyanık kal" ayarları.
 * Neyi değiştirdiği (ayarların eski değerleriyle) files/setup.json'a yazılır; geri alma yalnızca bunları
 * eski hâline getirir. Uygulamalar `pm uninstall -k --user 0` ile kaldırılır, `install-existing` ile geri gelir.
 * ADB çağrıları arka plan iş parçacığından yapılmalı.
 */
class Setup(private val context: Context) {
    private val adb = LocalAdb(context)
    private val file = File(context.filesDir, "setup.json")

    fun adbState(): AdbState = runCatching { adb.shell("true"); AdbState.READY }.getOrElse {
        if (it is AdbAuthException) AdbState.NEEDS_PERMISSION else AdbState.OFF
    }

    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /** Kaldırılabilir olarak bilinen ve bu TV'de hâlâ yüklü olanlar; önerilenler önce. */
    fun cleanupCandidates(): List<CleanupApp> {
        val installed = adb.shell("pm list packages --user 0").lines().map { it.removePrefix("package:").trim() }.toSet()
        val wifiOwners = adb.shell("dumpsys wifi | grep 'cname='; true").lines()
            .filter { it.contains("mostRecentlyConnected=true") }
            .mapNotNull { Regex("cname=(\\S+)").find(it)?.groupValues?.get(1) }
            .toSet()
        val known = (if (isXiaomi) XIAOMI else emptyMap()) + COMMON
        return known.filterKeys { it in installed }.map { (pkg, recommended) ->
            CleanupApp(pkg, label(pkg), recommended, ownsCurrentWifi = pkg in wifiOwners)
        }.sortedWith(compareByDescending<CleanupApp> { it.recommended }.thenBy { it.label })
    }

    /** Seçilenleri kaldırır; kaldırılabilenleri kaydeder ve sayısını döner. */
    fun removeApps(packages: List<String>): Int {
        val log = read()
        val removed = log.optJSONArray(KEY_REMOVED).strings().toMutableSet()
        var count = 0
        for (pkg in packages) {
            if (runCatching { adb.shell("pm uninstall -k --user 0 $pkg") }.getOrNull()?.contains("Success") == true) {
                removed += pkg
                count++
            }
        }
        write(log.put(KEY_REMOVED, JSONArray(removed)))
        return count
    }

    val removedApps: List<String> get() = read().optJSONArray(KEY_REMOVED).strings()

    /** TV kapanınca kutu uyumasın ve ev ağı kesilmesin: akıllı ev cihazları her an yönetilebilsin. */
    fun keepAwake() {
        val log = read()
        val saved = log.optJSONObject(KEY_SETTINGS) ?: JSONObject()
        for ((namespace, key, target) in AWAKE) {
            val id = "$namespace/$key"
            // Eski değer yalnızca ilk kez yazılır; sihirbaz yeniden çalışınca kendi değerini "eski" saymasın
            if (!saved.has(id)) saved.put(id, adb.shell("settings get $namespace $key").trim())
            adb.shell("settings put $namespace $key $target")
        }
        write(log.put(KEY_SETTINGS, saved))
    }

    val keptAwake: Boolean get() = read().has(KEY_SETTINGS)

    fun recordHome() = write(read().put(KEY_HOME, true))

    val changedHome: Boolean get() = read().optBoolean(KEY_HOME)

    val hasChanges: Boolean get() = removedApps.isNotEmpty() || keptAwake || changedHome

    /**
     * Sihirbazın yaptıklarını geri alır: uygulamaları geri getirir, ayarları eski değerlerine döndürür.
     * [includeHome] ise Home tuşu da Google TV'ye verilir (kaldırma bunu zaten kendisi yapar).
     */
    fun undo(includeHome: Boolean) {
        val log = read()
        val failed = mutableListOf<String>()
        log.optJSONArray(KEY_REMOVED).strings().forEach { pkg ->
            if (runCatching { adb.shell("cmd package install-existing $pkg") }.isFailure) failed += pkg
        }
        log.optJSONObject(KEY_SETTINGS)?.let { saved ->
            saved.keys().forEach { id ->
                val (namespace, key) = id.split('/', limit = 2)
                val old = saved.getString(id)
                adb.shell(if (old == "null" || old.isEmpty()) "settings delete $namespace $key" else "settings put $namespace $key $old")
            }
        }
        if (includeHome && log.optBoolean(KEY_HOME)) HomeSwitch.revert(adb)
        // Geri gelemeyen uygulama kalırsa kayıtta durur ki tekrar denenebilsin
        write(JSONObject().apply { if (failed.isNotEmpty()) put(KEY_REMOVED, JSONArray(failed)) })
    }

    private fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()
    }.getOrDefault(pkg)

    private fun read(): JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    private fun write(json: JSONObject) = file.writeText(json.toString())

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private companion object {
        const val KEY_REMOVED = "removed"
        const val KEY_SETTINGS = "settings"
        const val KEY_HOME = "home"

        // Paket → önerilir mi. Xiaomi listesi Mi Box S 1. ve 2. nesilde ve bir Mi TV'de (Android 9) denendi;
        // güncelleme servisi bilerek yok
        val XIAOMI = mapOf(
            "com.xiaomi.statistic" to true,
            "com.miui.tv.analytics" to true,
            "com.xm.webcontent" to true,
            "com.mitv.tvhome.oemtab" to true,
            "com.mitv.tvhome.atv" to false,
            "com.xiaomo.tv.milegal" to true,
            "com.xiaomi.mitv.smartshare" to false,
            "com.mitv.tvhome.michannel" to false,
            "com.mitv.tvhome.mitvplus" to false,
            "com.xiaomi.mimusic2" to false,
            "com.mitv.gallery" to false,
            // Mi Wallpaper: Xiaomi'nin ekran koruyucusu; seçiliyse kaldırılınca Android varsayılanına döner
            "com.mitv.dream" to false,
            "com.mitv.videoplayer" to false,
            "com.xiaomi.mitv.mediaexplorer" to false,
        )
        val COMMON = mapOf(
            "tv.alphonso.alphonso_eula" to true,
            "com.google.android.videos" to false,
            "com.netflix.ninja" to false,
            "com.google.android.youtube.tvmusic" to false,
            "com.google.android.play.games" to false,
            "com.amazon.amazonvideo.livingroom" to false,
        )

        val AWAKE = listOf(
            Triple("global", "hdmi_control_auto_device_off_enabled", "0"),
            Triple("global", "low_power_standby_enabled", "0"),
            Triple("global", "stay_on_while_plugged_in", "3"),
            Triple("global", "wifi_sleep_policy", "2"),
            Triple("secure", "sleep_timeout", "-1"),
            Triple("secure", "attentive_timeout", "-1"),
        )
    }
}
