package dev.evim.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.core.content.edit
import dev.evim.tv.R

/** Kumandada bir tuşa atanan iş: ana ekran ya da bir uygulama. */
sealed interface KeyTarget {
    data object Home : KeyTarget
    data class App(val packageName: String) : KeyTarget
}

/**
 * Kumandanın kısayol tuşlarının (Netflix, YouTube, Apps...) yeni görevleri. Google TV ana ekranı kapatılınca bu tuşlar
 * boşa çıkar; [dev.evim.tv.RemoteKeyService] onları uygulamalara ulaşmadan yakalar. Servis tuşların hepsini
 * süzdüğü için yalnızca atanmış bir tuş varken açık tutulur. Açıp kapatmak TV'nin ADB'siyle yapılır.
 */
class RemoteKeys(private val context: Context) {
    private val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val mappings: Map<Int, KeyTarget>
        get() = sp.all.mapNotNull { (key, value) ->
            val code = key.toIntOrNull() ?: return@mapNotNull null
            code to (if (value == HOME) KeyTarget.Home else KeyTarget.App(value as? String ?: return@mapNotNull null))
        }.toMap()

    fun set(code: Int, target: KeyTarget?) = sp.edit {
        when (target) {
            null -> remove(code.toString())
            KeyTarget.Home -> putString(code.toString(), HOME)
            is KeyTarget.App -> putString(code.toString(), target.packageName)
        }
    }

    private val component get() = "${context.packageName}/${context.packageName}.RemoteKeyService"

    /** ADB gerektirmez; ayar herkesin okuyabildiği bir değer. */
    val serviceEnabled: Boolean
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals(component, ignoreCase = true) }

    /** Servisi listede başka servisler varsa onlara dokunmadan açar ya da kapatır. Arka plan iş parçacığından. */
    fun setServiceEnabled(adb: LocalAdb, enabled: Boolean) {
        val current = adb.shell("settings get secure enabled_accessibility_services").trim()
            .split(':').filter { it.isNotBlank() && it != "null" && !it.equals(component, ignoreCase = true) }
        val next = if (enabled) current + component else current
        if (next.isEmpty()) {
            adb.shell("settings delete secure enabled_accessibility_services")
        } else {
            adb.shell("settings put secure enabled_accessibility_services ${next.joinToString(":")}")
            adb.shell("settings put secure accessibility_enabled 1")
        }
    }

    companion object {
        const val PREFS = "remote_keys"
        private const val HOME = "home"

        /** Öğrenilirken başka işe atanamayan tuşlar: yön, OK, geri, Home, ses ve güç. */
        val RESERVED = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_UNKNOWN,
        )

        // Xiaomi kumandasında (Mi Box S 2. nesil, Vendor_2717_Product_32b9.kl) tuşların ürettiği kodlar
        private val XIAOMI_NAMES = mapOf(
            KeyEvent.KEYCODE_BUTTON_6 to R.string.keys_netflix,
            KeyEvent.KEYCODE_BUTTON_3 to R.string.keys_youtube,
            KeyEvent.KEYCODE_BUTTON_7 to R.string.keys_prime,
            KeyEvent.KEYCODE_BUTTON_5 to R.string.keys_tvplus,
            KeyEvent.KEYCODE_ALL_APPS to R.string.keys_apps,
        )

        // Sistem Netflix, Prime ve Apps gibi tuşları erişilebilirlik servisine vermeden işliyor; onlar yalnızca getevent ile
        // görülebiliyor. Kodlar kutudan kutuya değiştiği için cihazın kendi tuş haritası okunur.
        const val KEY_LAYOUT_COMMAND = "cat /vendor/usr/keylayout/Vendor_2717_Product_32b9.kl 2>/dev/null ||" +
            " cat /system/usr/keylayout/Vendor_2717_Product_32b9.kl 2>/dev/null; true"
        private val LAYOUT_LINE = Regex("""^\s*key\s+(\d+)\s+(\w+)""")
        private val KEY_RELEASE = Regex("""0001 ([0-9a-f]{4}) 00000000\s*$""")

        /** Tuş haritası metninden Linux kodu → Android tuş kodu; yön, ses gibi ayrılmış tuşlar dışarıda kalır. */
        fun parseKeyLayout(text: String): Map<Int, Int> = text.lines().mapNotNull { line ->
            val (scan, name) = LAYOUT_LINE.find(line)?.destructured ?: return@mapNotNull null
            val code = KeyEvent.keyCodeFromString("KEYCODE_$name")
            if (code == KeyEvent.KEYCODE_UNKNOWN || code in RESERVED) null else scan.toInt() to code
        }.toMap()

        /** getevent satırı [layout]'taki bir tuşun bırakılışıysa Android tuş kodu. */
        fun releasedKey(line: String, layout: Map<Int, Int>): Int? =
            KEY_RELEASE.find(line)?.let { layout[it.groupValues[1].toInt(16)] }

        // Mi Box 4'ün kumandası (oneday): Netflix tuşu üreticiye özel 1001 kodunu üretiyor
        private val MIBOX4_NAMES = mapOf(1001 to R.string.keys_netflix, KeyEvent.KEYCODE_BUTTON_4 to R.string.keys_prime, KeyEvent.KEYCODE_ALL_APPS to R.string.keys_apps)

        /** Bilinen bir tuşsa adı; değilse null ve Android'in kod adı gösterilir. */
        @StringRes
        fun knownName(code: Int): Int? = when {
            Build.DEVICE == "oneday" -> MIBOX4_NAMES[code]
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) -> XIAOMI_NAMES[code]
            else -> null
        }

        fun codeName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

        /** Ayarlarda "tuşa basın" beklenirken servis yakaladığı tuşu buraya verir; main thread'de çağrılır. */
        @Volatile
        var learner: ((Int) -> Unit)? = null
    }
}
