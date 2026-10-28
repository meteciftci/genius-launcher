package dev.evim.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.evim.tv.data.AppRepository
import dev.evim.tv.data.FILES_APP
import dev.evim.tv.data.KeyTarget
import dev.evim.tv.data.LauncherPrefs
import dev.evim.tv.data.RemoteKeys

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
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mappings = keys.mappings }

    override fun onServiceConnected() {
        keys = RemoteKeys(this)
        mappings = keys.mappings
        getSharedPreferences(RemoteKeys.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        if (::keys.isInitialized) getSharedPreferences(RemoteKeys.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return swallowed.remove(code)
        val learner = RemoteKeys.learner
        if (learner != null && code !in RemoteKeys.RESERVED) {
            if (event.repeatCount == 0) learner(code)
            swallowed += code
            return true
        }
        val target = mappings[code] ?: return false
        if (event.repeatCount == 0) run(target)
        swallowed += code
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
}
