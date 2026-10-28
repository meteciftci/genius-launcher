package dev.evim.tv.data.home

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Hesaptaki bir Tuya cihazı; yerel kontrol için gereken her şey ve desteğin ne kadar olduğu. */
data class TuyaAccountDevice(
    val id: String,
    val name: String,
    val localKey: String,
    val category: String,
    val productId: String,
    val kind: TuyaKind,
    /** Bilinmeyen cihazlarda aç/kapa DP numarası. */
    val switchDp: String?,
)

/**
 * Destek seviyeleri: denenmiş tiplerde tam kontrol, bilinmeyen ama anahtarı olan cihazda yalnızca aç/kapa,
 * geri kalanlarda yalnızca çevrimiçi durumu.
 */
enum class TuyaKind { RELAY, SWITCH, STATUS }

/** QR girişi tamamlanınca dönen oturum; yalnızca bellekte tutulur, hiçbir yere yazılmaz. */
class TuyaSession internal constructor(
    internal val endpoint: String,
    internal val accessToken: String,
    internal val refreshToken: String,
)

/**
 * Tuya / Smart Life hesabına Home Assistant'ın kullandığı QR girişiyle bağlanır ve cihazların yerel
 * anahtarlarını alır (tuya-device-sharing-sdk'nın Kotlin'e taşınmış hali). Geliştirici hesabı gerekmez.
 * İstekler AES-GCM ile şifrelenip HMAC ile imzalanır. Ağ çağrıları arka plan iş parçacığından yapılmalı.
 */
class TuyaCloud(private val clientId: String, private val schema: String) {

    /** Uygulamada okutulacak QR kodun içeriğini ve girişin bekleneceği jetonu döner. */
    fun startLogin(userCode: String): String {
        val url = "$LOGIN_HOST/v1.0/m/life/home-assistant/qrcode/tokens?clientid=${enc(clientId)}&usercode=${enc(userCode)}&schema=${enc(schema)}"
        val response = http("POST", url, null, emptyMap())
        if (!response.optBoolean("success")) throw TuyaCloudException(response.optString("msg"))
        return response.getJSONObject("result").getString("qrcode")
    }

    fun qrContent(token: String) = "tuyaSmart--qrLogin?token=$token"

    /** Kullanıcı uygulamada onaylayana kadar null döner. */
    fun loginResult(token: String, userCode: String): TuyaSession? {
        val url = "$LOGIN_HOST/v1.0/m/life/home-assistant/qrcode/tokens/${enc(token)}?clientid=${enc(clientId)}&usercode=${enc(userCode)}"
        val response = http("GET", url, null, emptyMap())
        if (!response.optBoolean("success")) return null
        val result = response.getJSONObject("result")
        return TuyaSession(result.getString("endpoint"), result.getString("access_token"), result.getString("refresh_token"))
    }

    /** Hesaptaki tüm evlerin cihazları. */
    fun devices(session: TuyaSession): List<TuyaAccountDevice> {
        val homes = request(session, "/v1.0/m/life/users/homes", null).asArray()
        return homes.objects().flatMap { home ->
            val list = request(session, "/v1.0/m/life/ha/home/devices", JSONObject().put("homeId", home.get("ownerId").toString())).asArray()
            // Mama kapları ev ağından mama verme komutunu uygulamıyor; listelenmez
            list.objects().filterNot { it.optString("product_id") in FEEDER_PRODUCTS || it.optString("category") == FEEDER_CATEGORY }
                .map { device(session, it) }
        }.distinctBy { it.id }
    }

    private fun device(session: TuyaSession, item: JSONObject): TuyaAccountDevice {
        val id = item.getString("id")
        val category = item.optString("category")
        val productId = item.optString("product_id")
        val localKey = item.optString("local_key")
        var switchDp: String? = null
        val kind = when {
            localKey.isEmpty() -> TuyaKind.STATUS
            category in VERIFIED_RELAYS && item.statusCodes().contains("switch_1") -> TuyaKind.RELAY
            else -> {
                // DP kodu → numara eşlemesi yalnızca bilinmeyen cihazlar için sorulur
                switchDp = runCatching { switchDp(session, id) }.getOrNull()
                if (switchDp != null) TuyaKind.SWITCH else TuyaKind.STATUS
            }
        }
        return TuyaAccountDevice(id, item.optString("name", id), localKey, category, productId, kind, switchDp)
    }

    private fun switchDp(session: TuyaSession, id: String): String? {
        val relations = (request(session, "/v1.0/m/life/devices/$id/status", null) as JSONObject).optJSONArray("dpStatusRelationDTOS")
        val byCode = relations.objects().associate { it.optString("statusCode") to it.opt("dpId")?.toString() }
        return SWITCH_CODES.firstNotNullOfOrNull { byCode[it] }
    }

    private fun JSONObject.statusCodes(): Set<String> =
        optJSONArray("status").objects().map { it.optString("code") }.toSet()

    /** İmzalı ve şifreli istek; sonuç çözülüp JSON olarak döner. */
    private fun request(session: TuyaSession, path: String, params: JSONObject?): Any {
        val rid = UUID.randomUUID().toString()
        val hashKey = hex(MessageDigest.getInstance("MD5").digest((rid + session.refreshToken).toByteArray()))
        val secret = hex(hmac(rid.toByteArray(), hashKey.toByteArray())).take(16).toByteArray()

        val query = params?.takeIf { it.length() > 0 }?.let { encrypt(it.toString(), secret) }.orEmpty()
        val headers = linkedMapOf(
            "X-appKey" to clientId,
            "X-requestId" to rid,
            "X-time" to System.currentTimeMillis().toString(),
            "X-token" to session.accessToken,
        )
        val signed = headers.entries.joinToString("||") { "${it.key}=${it.value}" } + query
        headers["X-sid"] = ""
        headers["X-sign"] = hex(hmac(hashKey.toByteArray(), signed.toByteArray()))

        val url = session.endpoint + path + if (query.isEmpty()) "" else "?encdata=${enc(query)}"
        val response = http("GET", url, null, headers)
        if (!response.optBoolean("success")) throw TuyaCloudException(response.optString("msg"))
        val result = response.opt("result") as? String ?: return JSONObject()
        val plain = decrypt(result, secret)
        return if (plain.trimStart().startsWith("[")) JSONArray(plain) else JSONObject(plain)
    }

    private fun http(method: String, url: String, body: String?, headers: Map<String, String>): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            return JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
        } finally {
            connection.disconnect()
        }
    }

    private fun encrypt(plain: String, secret: ByteArray): String {
        val nonce = (1..12).map { NONCE_CHARS[random.nextInt(NONCE_CHARS.length)] }.joinToString("").toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
        }
        return b64(nonce) + b64(cipher.doFinal(plain.toByteArray()))
    }

    private fun decrypt(data: String, secret: ByteArray): String {
        val raw = Base64.decode(data, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, raw, 0, 12))
        }
        return String(cipher.doFinal(raw, 12, raw.size - 12))
    }

    private fun Any.asArray(): JSONArray = this as? JSONArray ?: JSONArray()

    companion object {
        /** Xenon X8605: ev ağından mama verme komutunu uygulamadığı için desteklenmiyor. */
        val FEEDER_PRODUCTS = setOf("cujblevbekyblbhz")
        const val FEEDER_CATEGORY = "cwwsq"

        private const val LOGIN_HOST = "https://apigw.iotbing.com"
        private const val TIMEOUT_MS = 10_000
        private const val NONCE_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        private val random = SecureRandom()

        // Bu launcher'da denenmiş ürünler: röle/priz kategorileri
        private val VERIFIED_RELAYS = setOf("tdq", "kg", "cz", "pc")
        private val SWITCH_CODES = listOf("switch_1", "switch", "switch_led")

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
        private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

        fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

class TuyaCloudException(message: String) : Exception(message)
