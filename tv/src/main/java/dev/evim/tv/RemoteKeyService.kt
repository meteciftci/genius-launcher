package dev.evim.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.evim.tv.data.AppRepository
import dev.evim.tv.data.FILES_APP
import dev.evim.tv.data.KeyTarget
import dev.evim.tv.data.LauncherPrefs
import dev.evim.tv.data.LocalAdb
import dev.evim.tv.data.RemoteKeys
import kotlin.concurrent.thread

/**
 * Kumandanın atanmış tuşlarını, hangi uygulama açık olursa olsun uygulamalara ulaşmadan yakalar.
 * Apps tuşu (ALL_APPS) gibi bazılarını Android hiçbir pencereye vermediği için erişilebilirlik servisi gerekiyor.
 * Atanmamış tuşlar olduğu gibi geçer; bildirim ya da ekran içeriği okunmaz.
 */
class RemoteKeyService : AccessibilityService() {
    private lateinit var keys: RemoteKeys
    private var mappings: Map<Int, KeyTarget> = emptyMap()
    // Basışı yutulan tuşun bırakılışı da yutulsun; yoksa uygulama yalnızca bırakılışı görür
    private val swallowed = mutableSetOf<Int>()
    // Bu yoldan işlenen tuşlar; getevent aynı basışı ikinci kez çalıştırmasın
    private val handled = mutableSetOf<Int>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var watching = false
    @Volatile private var rawKeys: AutoCloseable? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mappings = keys.mappings }

    override fun onServiceConnected() {
        keys = RemoteKeys(this)
        mappings = keys.mappings
        getSharedPreferences(RemoteKeys.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            watching = true
            thread(isDaemon = true) { watchRawKeys() }
        }
    }

    override fun onDestroy() {
        watching = false
        rawKeys?.let { thread { runCatching { it.close() } } }
        if (::keys.isInitialized) getSharedPreferences(RemoteKeys.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    /** Sistemin bu servise vermediği tuşlar için; servis açıkken sürer, bağlantı koparsa yeniden kurulur. */
    private fun watchRawKeys() {
        val adb = LocalAdb(this)
        while (watching) {
            runCatching {
                val layout = RemoteKeys.parseKeyLayout(adb.shell(RemoteKeys.KEY_LAYOUT_COMMAND))
                adb.stream("getevent", { rawKeys = it }) { line ->
                    RemoteKeys.releasedKey(line, layout)?.let { code -> main.post { onRawKey(code) } }
                }
            }
            if (watching) Thread.sleep(RAW_RETRY_MS)
        }
    }

    private fun onRawKey(code: Int) {
        if (handled.remove(code)) return
        val learner = RemoteKeys.learner
        if (learner != null) learner(code) else mappings[code]?.let(::run)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return swallowed.remove(code)
        val learner = RemoteKeys.learner
        if (learner != null && code !in RemoteKeys.RESERVED) {
            if (event.repeatCount == 0) learner(code)
            swallowed += code
            handled += code
            return true
        }
        val target = mappings[code] ?: return false
        if (event.repeatCount == 0) run(target)
        swallowed += code
        handled += code
        return true
    }

    private fun run(target: KeyTarget) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (target) {
            KeyTarget.Home -> runCatching { startActivity(home) }
            is KeyTarget.App -> {
                val prefs = LauncherPrefs(this)
                // Kilitli uygulama PIN'siz açılmasın: launcher açılır ve PIN'i o sorar. Dosyalar da launcher'ın ekranı.
                if (target.packageName == FILES_APP || prefs.pinHash != null && target.packageName in prefs.locked) {
                    runCatching { startActivity(home.putExtra(MainActivity.EXTRA_OPEN, target.packageName)) }
                } else {
                    AppRepository(this).launch(target.packageName)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        const val RAW_RETRY_MS = 5000L
    }
}
