package dev.evim.tv.data

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * TV'nin kendi ADB'sine cihazın içinden (127.0.0.1:5555) bağlanır.
 *
 * Android 14'te normal bir uygulama başka uygulamaların süreçlerini kapatamıyor, ana ekranı
 * da değiştiremiyor; bunlar için shell yetkisi gerekiyor. İlk bağlantıda TV
 * "USB hata ayıklamaya izin verilsin mi?" diye sorar; "Her zaman izin ver" onaylanınca
 * anahtar hatırlanır. TV'de USB hata ayıklamanın açık kalması gerekir.
 */
class LocalAdb(context: Context) {
    private val keyDir = File(context.filesDir, "adb")
    private val privateKey = File(keyDir, "adbkey")
    private val publicKey = File(keyDir, "adbkey.pub")

    /** Komutu shell yetkisiyle çalıştırır. İzin onaylanana kadar bekler; bağlanamazsa hata fırlatır. */
    fun shell(command: String): String {
        Dadb.create(HOST, PORT, keyPair(), connectTimeout = CONNECT_TIMEOUT_MS).use { adb ->
            val response = adb.shell(command)
            check(response.exitCode == 0) { "'$command' başarısız (${response.exitCode}): ${response.allOutput}" }
            return response.output
        }
    }

    /** Kullanıcı en az bir kez izin verdiyse anahtar dosyası vardır. */
    val hasKey: Boolean get() = privateKey.exists()

    /**
     * Android, "her zaman izin ver" denmiş anahtarı 7 gün kullanılmazsa unutup yeniden sorar.
     * Zararsız bir komutla bağlanıp bu süreyi yeniler.
     */
    fun keepAlive() {
        if (hasKey) shell("true")
    }

    private fun keyPair(): AdbKeyPair {
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
            // TV'deki izin penceresinde "unknown@unknown" yerine uygulamanın adı görünsün
            publicKey.writeText(publicKey.readText().substringBefore(' ') + " genius-launcher@tv")
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val CONNECT_TIMEOUT_MS = 3000
    }
}
