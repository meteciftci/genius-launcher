package dev.evim.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Ana ekrana geçmeden önceki denetimin sonucu. */
sealed interface SwitchCheck {
    /** Bağlı olunan Wi-Fi ağını kapatılacak uygulama kaydetmiş; geçilirse TV internetsiz kalır. */
    data object Blocked : SwitchCheck

    /** Geçilebilir; [erasesOtherNetworks] ise kullanılmayan bazı kayıtlı ağlar silinecek. */
    data class Ready(val erasesOtherNetworks: Boolean) : SwitchCheck
}

/**
 * Home tuşunu Genius Launcher ile Google TV arasında değiştirir (tools/mibox/ana_ekran.sh'nin uygulama içi hali).
 * Google TV kendi ana ekranı açıkken başka launcher'ları yok sayar; bu yüzden kapatılır (kaldırılmaz).
 * Sıra Free My TV'den: önce kurulum sihirbazı, sonra ana ekran; tersinde sihirbazın kurtarma ekranı
 * Home'u alır ve orada yeniden başlatmak bir Xiaomi'yi fabrika ayarlarına döndürmüş.
 * ADB çağrıları arka plan iş parçacığından yapılmalı.
 */
class HomeSwitch(private val context: Context) {
    private val adb = LocalAdb(context)

    /** ADB gerektirmez: Home'a basınca açılacak etkinlik bu uygulamanınki mi? */
    val isGeniusHome: Boolean
        get() {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            return resolved?.activityInfo?.packageName == context.packageName
        }

    /**
     * Android kapatılan uygulamanın kaydettiği Wi-Fi ağlarını siler. Bağlı olunan ağı Google TV parçalarından
     * biri kaydettiyse geçiş yapılmaz; başka ağlar silinecekse kullanıcıya söylenir.
     */
    fun check(): SwitchCheck {
        // Her kayıtlı ağın bir satırı var: "cname=<kaydeden> ... mostRecentlyConnected=<true|false>"
        val lines = adb.shell("dumpsys wifi | grep 'cname='; true").lines()
        val owners = lines.mapNotNull { line ->
            val owner = Regex("cname=(\\S+)").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            owner to line.contains("mostRecentlyConnected=true")
        }
        val current = owners.filter { it.second }.map { it.first }.toSet()
        if (current.any { it in GOOGLE_PACKAGES }) return SwitchCheck.Blocked
        return SwitchCheck.Ready(erasesOtherNetworks = owners.any { it.first in GOOGLE_PACKAGES })
    }

    fun toGenius() {
        val self = "${context.packageName}/.MainActivity"
        adb.shell("cmd package set-home-activity $self")
        adb.shell("pm disable-user --user 0 $SETUP_PACKAGE")
        adb.shell("pm disable-user --user 0 $GOOGLE_PACKAGE")
        adb.shell("cmd package set-home-activity $self")
    }

    fun toGoogle() {
        REVERT.forEach(adb::shell)
    }

    /** Home'u Google TV'ye verir ve bu uygulamayı TV'den kaldırır; verileri de onunla birlikte silinir. */
    fun uninstall() {
        toGoogle()
        // Komut bu süreci de sonlandırır; cevabı beklenmez
        runCatching { adb.shell("pm uninstall ${context.packageName}") }
    }

    companion object {
        private const val GOOGLE_PACKAGE = "com.google.android.apps.tv.launcherx"
        private const val SETUP_PACKAGE = "com.google.android.tungsten.setupwraith"
        private val GOOGLE_PACKAGES = setOf(GOOGLE_PACKAGE, SETUP_PACKAGE)

        /** Google TV'ye dönüş; önce ana ekran açılır ki kurulum sihirbazının kurtarma ekranı Home'u almasın. */
        val REVERT = listOf(
            "pm enable $GOOGLE_PACKAGE",
            "cmd package set-home-activity $GOOGLE_PACKAGE/.home.HomeActivity",
            "pm enable $SETUP_PACKAGE",
        )
    }
}
