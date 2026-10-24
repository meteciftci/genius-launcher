package dev.evim.tv.data.home

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Ev ağında bulunan Tuya cihazı. */
data class TuyaLanDevice(val id: String, val ip: String, val version: String)

/**
 * Tuya cihazları birkaç saniyede bir ev ağına kim olduklarını yayınlar: 6666'da açık (3.1), 6667'de herkesçe
 * bilinen bir anahtarla şifreli (3.3 ve 3.4). Bu yayınlar dinlenerek cihazların IP'si ve protokol sürümü bulunur.
 * Ağ çağrısı: arka plan iş parçacığından.
 */
class TuyaDiscovery(private val context: Context) {

    /** [durationMs] boyunca dinler; aynı cihaz birden çok kez duyulursa son duyulan geçerli. */
    fun scan(durationMs: Long = 6_000): Map<String, TuyaLanDevice> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Bazı cihazlar kilit olmadan yayın paketlerini uygulamaya iletmez
        val lock = wifi.createMulticastLock("genius-tuya").apply { setReferenceCounted(false); acquire() }
        val found = mutableMapOf<String, TuyaLanDevice>()
        try {
            val sockets = PORTS.map { port ->
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 300
                    bind(InetSocketAddress(port))
                }
            }
            try {
                val end = System.currentTimeMillis() + durationMs
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() < end) {
                    for (socket in sockets) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        parse(packet.data.copyOf(packet.length))?.let { found[it.id] = it }
                    }
                }
            } finally {
                sockets.forEach { it.close() }
            }
        } finally {
            lock.release()
        }
        return found
    }

    private fun parse(frame: ByteArray): TuyaLanDevice? {
        // 55AA çerçevesi: önek, sıra, komut, uzunluk, dönüş kodu (20 bayt) … CRC (4) ya da HMAC (32) ve sonek (4)
        val text = listOf(8, 36).filter { frame.size >= 20 + it + 16 }.firstNotNullOfOrNull { footer ->
            val payload = frame.copyOfRange(20, frame.size - footer)
            runCatching { String(decrypt(payload)) }.getOrNull()?.takeIf { it.trimStart().startsWith("{") }
                ?: String(payload).takeIf { it.trimStart().startsWith("{") }
        } ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = json.optString("gwId").ifEmpty { return null }
        val ip = json.optString("ip").ifEmpty { return null }
        return TuyaLanDevice(id, ip, json.optString("version", "3.3"))
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(UDP_KEY, "AES")) }
        return cipher.doFinal(data)
    }

    private companion object {
        val PORTS = listOf(6666, 6667)
        val UDP_KEY: ByteArray = MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".toByteArray())
    }
}
