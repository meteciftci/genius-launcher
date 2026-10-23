package dev.evim.tv.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import dev.evim.tv.R

@Immutable
data class LauncherApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** Simgeden otomatik çıkarılan renk. */
    val autoColor: Color,
    /** Ekranda kullanılan renk: kullanıcı seçtiyse o, yoksa [autoColor]. */
    val color: Color = autoColor,
)

data class RamInfo(val usedFraction: Float, val totalMb: Long)

/** Wi-Fi sinyali 0-4 arası; null ise Wi-Fi'ye bağlı değil. */
data class SystemStatus(val wifiLevel: Int?, val bluetoothOn: Boolean)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val am = context.getSystemService(ActivityManager::class.java)
    private val adb = LocalAdb(context)

    /** TV (leanback) ve normal launcher'da görünen tüm uygulamalar, paket başına bir kez. */
    fun loadApps(): List<LauncherApp> {
        val found = linkedMapOf<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            pm.queryIntentActivities(intent, 0).forEach { found.putIfAbsent(it.activityInfo.packageName, it) }
        }
        found.remove(context.packageName)
        // Ayarlar üst çubuktaki simgeden açılıyor; kartlar arasında yer kaplamasın
        found.remove(TV_SETTINGS_PACKAGE)
        return found.values.map { info ->
            val bitmap = info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            LauncherApp(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = bitmap.asImageBitmap(),
                autoColor = accentFrom(bitmap),
            )
        }
    }

    fun launch(packageName: String) {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return
        // Uygulama kaldırılmış ya da devre dışı olabilir; launcher çökmemeli
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun systemStatus(): SystemStatus {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val wifiLevel = caps?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }?.let {
            val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.getSystemService(WifiManager::class.java).calculateSignalLevel(it.signalStrength)
            } else {
                @Suppress("DEPRECATION")
                WifiManager.calculateSignalLevel(it.signalStrength, 5)
            }
            level.coerceIn(0, 4)
        }
        val bluetoothOn = runCatching {
            context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
        }.getOrDefault(false)
        return SystemStatus(wifiLevel, bluetoothOn)
    }

    /**
     * TV ayarlarının bir sayfasını açar. Sayfa yoksa ya da açılamazsa ana ayarlara düşer;
     * hiçbir durumda launcher'ı çökertmez.
     */
    fun openSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = intent.resolveActivity(pm) != null && runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) openMainSettings()
    }

    /**
     * "Uzaktan Kumandalar ve Aksesuarlar" sayfası. Google TV bu sayfayı izinle korunan
     * SliceActivity içinde gösteriyor; normal uygulama açamadığı için shell yetkisiyle açılır.
     * Arka plan iş parçacığından çağrılmalı; ADB'ye ulaşılamazsa ana ayarlara düşer.
     */
    fun openAccessorySettings() {
        val title = context.getString(R.string.accessories_title).replace("'", "")
        val command = "am start -a android.settings.SLICE_SETTINGS" +
            " -n com.android.tv.settings/.slice.SliceActivity" +
            " --es slice_uri content://com.android.tv.settings.accessories.sliceprovider/general" +
            " --es screen_title '$title'"
        val opened = runCatching { adb.shell(command) }.isSuccess
        if (!opened) openMainSettings()
    }

    private fun openMainSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun ramInfo(): RamInfo {
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return RamInfo(1f - info.availMem.toFloat() / info.totalMem, info.totalMem / (1024 * 1024))
    }

    /** Arka planda bekleyen uygulamaları kapatır; ön plandakilere dokunmaz. */
    /**
     * Arka plandaki tüm süreçleri kapatır ve boşalan RAM'i MB olarak döner.
     * Android 14'te bunu yalnızca shell yapabildiği için TV'nin kendi ADB'si kullanılır.
     */
    fun freeRam(): Long {
        val before = availableMb()
        adb.shell("am kill-all")
        // Süreçlerin belleği sisteme geri vermesi biraz sürüyor
        Thread.sleep(1500)
        return availableMb() - before
    }

    fun keepAdbAuthorized() = adb.keepAlive()

    private fun availableMb(): Long = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem / (1024 * 1024)

    private fun accentFrom(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).generate()
        val candidates = listOf(
            palette.vibrantSwatch,
            palette.dominantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
        )
        // Çok açık renkler (beyaz simgeler) vitrinde soluk durur, onları atla
        return candidates.firstNotNullOfOrNull { swatch ->
            swatch?.let { Color(it.rgb) }?.takeIf { it.luminance() < 0.7f }
        } ?: FALLBACK
    }

    private companion object {
        const val ICON_PX = 192
        const val TV_SETTINGS_PACKAGE = "com.android.tv.settings"
        val FALLBACK = Color(0xFF4A5060)
    }
}
