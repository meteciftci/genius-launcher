package dev.evim.tv.data

import android.content.Context
import dev.evim.tv.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** GitHub'daki yeni sürüm. [sha256] GitHub'ın dosya için verdiği özet. */
data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val size: Long, val sha256: String)

class UpdateException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NETWORK, CORRUPT }
}

/**
 * Uygulama Play Store'da olmadığı için güncellemeleri GitHub Releases'tan alır: son sürüm kendi sürümünden
 * yeniyse APK indirilir, GitHub'ın verdiği SHA-256 özetiyle karşılaştırılır ve TV'nin ADB'siyle kurulur.
 * Android, imzası kurulu sürümle aynı olmayan APK'yı zaten kabul etmez. Ağ çağrıları arka plan iş parçacığından.
 */
class Updater(private val context: Context) {
    private val adb = LocalAdb(context)

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Kendi sürümünden yeni bir sürüm varsa onu, yoksa null döner. */
    fun latest(): AppUpdate? {
        val text = get(LATEST_URL)
        val release = JSONObject(text)
        val version = release.optString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return null
        val assets = release.optJSONArray("assets") ?: return null
        // Her sürüm kendi dosyasını alır: lite "genius-launcher-lite-x.y.z.apk", tam "genius-launcher-x.y.z.apk"
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
            val name = it.optString("name")
            name.endsWith(".apk") && name.contains("-lite-") == !BuildConfig.SMART_HOME
        } ?: return null
        // GitHub her dosya için "sha256:..." özetini verir; özeti olmayan dosya kurulmaz
        val digest = apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 } ?: return null
        return AppUpdate(
            version = version,
            notes = plainNotes(release.optString("body")),
            apkUrl = apk.getString("browser_download_url"),
            size = apk.optLong("size"),
            sha256 = digest.lowercase(),
        )
    }

    /** APK'yı indirir ve özetini doğrular; [onProgress] 0..1. */
    fun download(update: AppUpdate, onProgress: (Float) -> Unit): File {
        val target = File(context.getExternalFilesDir(null), "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != 200) throw UpdateException(UpdateException.Reason.NETWORK)
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.size
                var read = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            target.delete()
            throw UpdateException(UpdateException.Reason.NETWORK)
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != update.sha256) {
            target.delete()
            throw UpdateException(UpdateException.Reason.CORRUPT)
        }
        return target
    }

    /**
     * APK'yı ADB ile kurar ve launcher'ı yeniden açar. Kurulum bu süreci kapatacağı için komutlar ADB tarafında,
     * bağlantı kopsa da sürecek şekilde arka planda çalışır. Kurulum başarısız olursa sonuç dosyasına bakılarak
     * hata fırlatılır; başarılı olursa bu fonksiyon dönmeden süreç kapanır.
     */
    fun install(apk: File) {
        val result = File(apk.parentFile, "update.result").apply { delete() }
        val script = "cat ${quote(apk.path)} | pm install -r -S ${apk.length()}; echo \$? > ${quote(result.path)};" +
            " am start -n ${context.packageName}/.MainActivity"
        adb.shell("setsid nohup sh -c ${quote(script)} > /dev/null 2>&1 < /dev/null &")
        // Kurulum başarılıysa buraya dönülmeden süreç kapanır
        repeat(INSTALL_WAIT_S) {
            Thread.sleep(1000)
            val code = runCatching { result.readText().trim() }.getOrNull()
            if (code != null && code.isNotEmpty()) {
                result.delete()
                if (code != "0") throw UpdateException(UpdateException.Reason.CORRUPT)
                return
            }
        }
        throw UpdateException(UpdateException.Reason.NETWORK)
    }

    private fun get(url: String): String {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (connection.responseCode != 200) throw UpdateException(UpdateException.Reason.NETWORK)
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateException.Reason.NETWORK)
        }
    }

    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/meteciftci/genius-launcher/releases/latest"
        private const val USER_AGENT = "genius-launcher"
        private const val TIMEOUT_MS = 15_000
        private const val INSTALL_WAIT_S = 90

        /** "0.10.0" > "0.9.1"; sayı olmayan parçalar 0 sayılır. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Sürüm notlarının Markdown işaretlerini atar; TV'de düz metin gösterilir. */
        fun plainNotes(markdown: String): String =
            markdown.lines()
                .map { it.trim().trimStart('#', '>').trim().replace("**", "").replace("`", "") }
                .map { if (it.startsWith("- ") || it.startsWith("* ")) "•  " + it.drop(2) else it }
                .joinToString("\n")
                .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
    }
}
