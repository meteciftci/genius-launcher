package dev.evim.tv.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/**
 * README ekran görüntüleri için örnek veri: TV'deki kişisel uygulamalar yerine örnek kartlar, şehir yerine
 * İstanbul. Launcher'a `--ez demo true` ile istek gelince etkinleşir, süreç kapanana kadar sürer; hiçbir ayar yazılmaz.
 *
 * ```
 * adb shell am start -n dev.evim.tv/.MainActivity --ez demo true
 * ```
 */
object DemoMode {
    @Volatile var enabled = false

    val CITY = City("İstanbul", 41.01, 28.98, "İstanbul, Türkiye")

    /** Örnek kartlar arasına TV'de yüklü olan bilinen uygulamalar da girer (gerçek simgeleriyle). */
    private val ORDER = listOf(
        "demo.netflix", "com.google.android.youtube.tv", "demo.disney", "demo.max", "demo.prime",
        "com.spotify.tv.android", FILES_APP, "com.android.vending",
    )

    fun apps(installed: List<LauncherApp>): List<LauncherApp> {
        val fake = listOf(
            card("demo.netflix", "Netflix", "N", 0xFFE50914),
            card("demo.disney", "Disney+", "D+", 0xFF113CCF),
            card("demo.max", "HBO Max", "max", 0xFF5B2BD6),
            card("demo.prime", "Prime Video", "P", 0xFF1A98FF),
        )
        val byPackage = (fake + installed).associateBy { it.packageName }
        return ORDER.mapNotNull { byPackage[it] }
    }

    /** Gerçek logo yerine renkli zeminde harf: markaların simgeleri repoya girmez. */
    private fun card(packageName: String, label: String, mark: String, argb: Long): LauncherApp {
        val color = Color(argb)
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = if (mark.length > 2) 64f else 96f
        }
        canvas.drawText(mark, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
        return LauncherApp(packageName, label, bitmap.asImageBitmap(), autoColor = color)
    }
}
