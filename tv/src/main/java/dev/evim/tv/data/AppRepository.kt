package dev.evim.tv.data

import android.app.ActivityManager
import android.content.Context
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

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val am = context.getSystemService(ActivityManager::class.java)

    /** TV (leanback) ve normal launcher'da görünen tüm uygulamalar, paket başına bir kez. */
    fun loadApps(): List<LauncherApp> {
        val found = linkedMapOf<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            pm.queryIntentActivities(intent, 0).forEach { found.putIfAbsent(it.activityInfo.packageName, it) }
        }
        found.remove(context.packageName)
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
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun ramInfo(): RamInfo {
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return RamInfo(1f - info.availMem.toFloat() / info.totalMem, info.totalMem / (1024 * 1024))
    }

    /** Arka planda bekleyen uygulamaları kapatır; ön plandakilere dokunmaz. */
    fun freeRam() {
        pm.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it != context.packageName }
            .forEach(am::killBackgroundProcesses)
    }

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
        val FALLBACK = Color(0xFF4A5060)
    }
}
