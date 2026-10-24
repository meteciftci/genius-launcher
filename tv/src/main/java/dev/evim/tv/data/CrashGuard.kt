package dev.evim.tv.data

import android.content.Context
import java.io.File

/**
 * Güvenlik ağı: Genius Launcher ana ekranken Google TV'nin ana ekranı kapalı olduğu için, launcher çökmeye
 * başlarsa geriye boş bir ekran kalır. 5 dakikada 3. çökmede TV'nin kendi ADB'siyle Google TV ana ekranı
 * geri açılır (fikir ve sıra Free My TV'nin Watchdog'undan). Çökme zamanları files/home-crashes.txt'te.
 */
object CrashGuard {
    private const val WINDOW_MS = 5 * 60_000L
    private const val LIMIT = 3
    private const val REVERT_TIMEOUT_MS = 8_000L

    // Önce ana ekran açılır ki kurulum sihirbazının kurtarma ekranı Home'u almasın
    private val REVERT = listOf(
        "pm enable com.google.android.apps.tv.launcherx",
        "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity",
        "pm enable com.google.android.tungsten.setupwraith",
    )

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                if (record(app, System.currentTimeMillis()) >= LIMIT) revert(app)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun revert(context: Context) {
        // Çöken iş parçacığında ağ işi yapılamaz; ayrı bir iş parçacığında yapılıp kısa süre beklenir
        val worker = Thread {
            runCatching {
                val adb = LocalAdb(context)
                REVERT.forEach(adb::shell)
            }
            file(context).delete()
        }
        worker.start()
        worker.join(REVERT_TIMEOUT_MS)
    }

    private fun record(context: Context, now: Long): Int {
        val recent = recent(context, now) + now
        file(context).writeText(recent.joinToString("\n"))
        return recent.size
    }

    private fun recent(context: Context, now: Long): List<Long> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { it.trim().toLongOrNull() }.filter { now - it in 0 until WINDOW_MS }
    }

    private fun file(context: Context) = File(context.filesDir, "home-crashes.txt")
}
