package dev.evim.tv.data.home

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tuya yerel protokolü (3.3 ve 3.4), TCP 6668. Her çağrı kısa bir bağlantı açıp kapatır;
 * bu cihazlar aynı anda az sayıda yerel bağlantı kabul ettiği için bağlantı açık tutulmaz.
 * Arka plan iş parçacığından çağrılmalı.
 */
class TuyaDevice(
    private val id: String,
    private val ip: String,
    localKey: String,
    private val version: String,
) {
    private val realKey = localKey.toByteArray(Charsets.UTF_8)
    private val is34 = version == "3.4"

    /** DP numarası → değer (Boolean, Int ya da String). */
    fun status(): Map<String, Any> = session { query() }

    /** Verilen DP'leri yazar ve cihazın bildirdiği yeni değerleri döner. */
    fun set(dps: Map<String, Any>): Map<String, Any> = session {
        val data = JSONObject(dps)
        if (is34) {
            send(CONTROL_NEW, JSONObject().put("protocol", 5).put("t", now()).put("data", JSONObject().put("dps", data)))
        } else {
            send(CONTROL, JSONObject().put("devId", id).put("uid", id).put("t", now().toString()).put("dps", data))
        }
        // Cihaz önce boş bir onay, sonra değişen DP'leri gönderir
        receiveDps(setOf(CONTROL, CONTROL_NEW, STATUS)) ?: emptyMap()
    }

    private fun <T> session(block: Session.() -> T): T =
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, PORT), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val s = Session(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()))
            if (is34) s.negotiate()
            s.block()
        }

    private inner class Session(private val input: DataInputStream, private val output: DataOutputStream) {
        private var key = realKey
        private var seq = 1

        fun query(): Map<String, Any> {
            if (is34) {
                send(DP_QUERY_NEW, JSONObject())
            } else {
                send(DP_QUERY, JSONObject().put("gwId", id).put("devId", id).put("uid", id).put("t", now().toString()))
            }
            return receiveDps(setOf(DP_QUERY, DP_QUERY_NEW, STATUS)) ?: error("Tuya $ip durum vermedi")
        }

        /** 3.4: iki tarafın rastgele sayılarından oturum anahtarı üretilir. */
        fun negotiate() {
            val localNonce = ByteArray(16).also(SecureRandom()::nextBytes)
            writeFrame(SESS_KEY_NEG_START, aes(realKey, localNonce, Cipher.ENCRYPT_MODE, pad = true))
            val (cmd, payload) = readFrame()
            check(cmd == SESS_KEY_NEG_RESP) { "Tuya $ip anahtar anlaşması başarısız ($cmd)" }
            val plain = aes(realKey, payload, Cipher.DECRYPT_MODE, pad = true)
            val remoteNonce = plain.copyOfRange(0, 16)
            check(hmac(realKey, localNonce).contentEquals(plain.copyOfRange(16, 48))) { "Tuya $ip yerel anahtar yanlış" }
            writeFrame(SESS_KEY_NEG_FINISH, aes(realKey, hmac(realKey, remoteNonce), Cipher.ENCRYPT_MODE, pad = true))
            val xor = ByteArray(16) { (localNonce[it].toInt() xor remoteNonce[it].toInt()).toByte() }
            key = aes(realKey, xor, Cipher.ENCRYPT_MODE, pad = false)
        }

        fun send(cmd: Int, json: JSONObject) {
            // Boşluklu JSON'u bazı cihazlar reddediyor
            val body = json.toString().replace(" ", "").toByteArray(Charsets.UTF_8)
            val needsHeader = cmd !in NO_HEADER_CMDS
            val payload = if (is34) {
                aes(key, if (needsHeader) versionHeader() + body else body, Cipher.ENCRYPT_MODE, pad = true)
            } else {
                val encrypted = aes(key, body, Cipher.ENCRYPT_MODE, pad = true)
                if (needsHeader) versionHeader() + encrypted else encrypted
            }
            writeFrame(cmd, payload)
        }

        fun receiveDps(commands: Set<Int>): Map<String, Any>? {
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val (cmd, payload) = readFrame()
                if (cmd !in commands || payload.isEmpty()) continue
                val json = decode(payload) ?: continue
                val dps = json.optJSONObject("dps") ?: json.optJSONObject("data")?.optJSONObject("dps") ?: continue
                return dps.keys().asSequence().associateWith { dps.get(it) }
            }
            return null
        }

        private fun decode(payload: ByteArray): JSONObject? {
            var data = payload
            if (is34) data = aes(key, data, Cipher.DECRYPT_MODE, pad = true)
            if (data.size >= 15 && String(data, 0, 3, Charsets.US_ASCII) == version) data = data.copyOfRange(15, data.size)
            if (!is34) data = aes(key, data, Cipher.DECRYPT_MODE, pad = true)
            return runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrNull()
        }

        private fun writeFrame(cmd: Int, payload: ByteArray) {
            val endSize = if (is34) 36 else 8
            val head = ByteBuffer.allocate(16).putInt(PREFIX).putInt(seq++).putInt(cmd).putInt(payload.size + endSize).array()
            val unsigned = head + payload
            val check = if (is34) {
                hmac(key, unsigned)
            } else {
                ByteBuffer.allocate(4).putInt(CRC32().apply { update(unsigned) }.value.toInt()).array()
            }
            output.write(unsigned + check + ByteBuffer.allocate(4).putInt(SUFFIX).array())
            output.flush()
        }

        /** Dönüş kodu ve sağlama/sonek ayıklanmış (komut, yük) çifti. */
        private fun readFrame(): Pair<Int, ByteArray> {
            check(input.readInt() == PREFIX) { "Tuya $ip bozuk paket" }
            input.readInt() // sıra numarası
            val cmd = input.readInt()
            val length = input.readInt()
            val body = ByteArray(length).also(input::readFully)
            val endSize = if (is34) 36 else 8
            // Cihazdan gelen paketlerde yükten önce 4 baytlık dönüş kodu var
            val start = if (length - endSize >= 4) 4 else 0
            return cmd to body.copyOfRange(start, length - endSize)
        }

        private fun versionHeader() = version.toByteArray(Charsets.US_ASCII) + ByteArray(12)
    }

    private companion object {
        const val PORT = 6668
        const val TIMEOUT_MS = 5000
        const val PREFIX = 0x000055AA
        const val SUFFIX = 0x0000AA55

        const val SESS_KEY_NEG_START = 3
        const val SESS_KEY_NEG_RESP = 4
        const val SESS_KEY_NEG_FINISH = 5
        const val CONTROL = 7
        const val STATUS = 8
        const val HEART_BEAT = 9
        const val DP_QUERY = 10
        const val CONTROL_NEW = 13
        const val DP_QUERY_NEW = 16
        val NO_HEADER_CMDS = setOf(DP_QUERY, DP_QUERY_NEW, HEART_BEAT, SESS_KEY_NEG_START, SESS_KEY_NEG_RESP, SESS_KEY_NEG_FINISH)

        fun now() = System.currentTimeMillis() / 1000

        fun aes(key: ByteArray, data: ByteArray, mode: Int, pad: Boolean): ByteArray {
            val encrypting = mode == Cipher.ENCRYPT_MODE
            val cipher = Cipher.getInstance(if (pad && encrypting) "AES/ECB/PKCS5Padding" else "AES/ECB/NoPadding")
                .apply { init(mode, SecretKeySpec(key, "AES")) }
            val out = cipher.doFinal(data)
            if (!pad || encrypting || out.isEmpty()) return out
            // Bazı cihazlar dolguyu tam doğru yapmıyor; katı kontrol yerine son baytı okuyup kırp
            val padding = out.last().toInt()
            return if (padding in 1..16 && padding <= out.size) out.copyOf(out.size - padding) else out
        }

        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
