package dev.evim.tv.data.home

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Yerel MQTT için gereken bilgiler; [name] hesaptaki ad ya da etiketten eklenince "Dyson". */
data class DysonAccountDevice(val serial: String, val name: String, val credential: String, val productType: String)

/** E-postaya kod gönderildi; kod ve şifreyle [DysonCloud.verify] çağrılır. */
class DysonChallenge internal constructor(internal val email: String, internal val id: String)

class DysonCloudException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NO_ACCOUNT, TOO_FREQUENT, WRONG_LOGIN, NETWORK }
}

/**
 * MyDyson hesabından cihazların yerel MQTT parolalarını alır (libdyson'ın Kotlin'e taşınmış hali):
 * e-postaya gelen kod ve hesap şifresiyle giriş, ardından cihaz listesi. Jeton yalnızca bu çağrılar
 * boyunca kullanılır, saklanmaz. Ağ çağrıları arka plan iş parçacığından yapılmalı.
 */
object DysonCloud {
    private const val HOST = "https://appapi.cp.dyson.com"
    private const val TIMEOUT_MS = 15_000
    // Robot süpürgeler başka bir protokol konuşur; bu launcher yalnızca hava temizleyicileri yönetir
    private val VACUUMS = setOf("N223", "276", "277")
    private val PRODUCT_TYPE = Regex("^[0-9]{3}[A-Z]?$")

    fun requestCode(email: String, country: String): DysonChallenge {
        provision()
        val status = call("POST", "/v3/userregistration/email/userstatus?country=${enc(country)}", JSONObject().put("email", email))
        if (status.second.optString("accountStatus") != "ACTIVE") throw DysonCloudException(DysonCloudException.Reason.NO_ACCOUNT)
        val auth = call("POST", "/v3/userregistration/email/auth?country=${enc(country)}&culture=en-US", JSONObject().put("email", email))
        if (auth.first == 429) throw DysonCloudException(DysonCloudException.Reason.TOO_FREQUENT)
        val id = auth.second.optString("challengeId").ifEmpty { throw DysonCloudException(DysonCloudException.Reason.NETWORK) }
        return DysonChallenge(email, id)
    }

    /** Kod ve şifre doğruysa hesaptaki hava temizleyicileri döner. */
    fun verify(challenge: DysonChallenge, code: String, password: String): List<DysonAccountDevice> {
        val body = JSONObject().put("email", challenge.email).put("password", password)
            .put("challengeId", challenge.id).put("otpCode", code)
        val (status, login) = call("POST", "/v3/userregistration/email/verify", body)
        val token = login.optString("token")
        // Arka arkaya denemelerde sunucunun önündeki Cloudflare 429 döner; bu yanlış şifre değildir
        if (status == 429) throw DysonCloudException(DysonCloudException.Reason.TOO_FREQUENT)
        if (status == 400 || status == 401 || status == 403) throw DysonCloudException(DysonCloudException.Reason.WRONG_LOGIN)
        if (token.isEmpty()) throw DysonCloudException(DysonCloudException.Reason.NETWORK)
        provision()
        val manifest = JSONArray(raw("GET", "/v2/provisioningservice/manifest", null, token).second)
        return (0 until manifest.length()).mapNotNull { manifest.optJSONObject(it) }.mapNotNull { device ->
            val encrypted = device.optString("LocalCredentials").ifEmpty { return@mapNotNull null }
            // "type" bazen varyantı da içerir (527K gibi); MQTT konusu bununla kurulur
            val type = device.optString("type").takeIf { it.matches(PRODUCT_TYPE) } ?: device.optString("ProductType")
            if (type in VACUUMS) return@mapNotNull null
            DysonAccountDevice(device.getString("Serial"), device.optString("Name", "Dyson"), decryptCredential(encrypted), type)
        }
    }

    /**
     * Cihazın üstündeki Wi-Fi etiketinden, buluta gitmeden: ağ adında seri no ve ürün tipi var,
     * MQTT parolası Wi-Fi parolasının SHA-512 özeti. Ağ adı tanınmazsa null.
     */
    fun fromSticker(ssid: String, password: String): DysonAccountDevice? {
        val match = Regex("^DYSON-([0-9A-Z]{3}-[A-Z]{2}-[0-9A-Z]{8,})-([0-9]{3}[A-Z]?)$").find(ssid.trim().uppercase()) ?: return null
        val serial = match.groupValues[1]
        // Bazı modellerde etiketteki tip MQTT'dekinden farklı
        val type = match.groupValues[2].let { if (it == "455A") "455" else it }
        val credential = Base64.encodeToString(MessageDigest.getInstance("SHA-512").digest(password.trim().toByteArray()), Base64.NO_WRAP)
        return DysonAccountDevice(serial, "Dyson", credential, type)
    }

    /** Dyson sunucusu bu çağrıdan sonra aynı adresten gelen istekleri kabul ediyor. */
    private fun provision() {
        if (raw("GET", "/v1/provisioningservice/application/Android/version", null, null).first != 200) {
            throw DysonCloudException(DysonCloudException.Reason.NETWORK)
        }
    }

    private fun call(method: String, path: String, body: JSONObject?): Pair<Int, JSONObject> {
        val (status, text) = raw(method, path, body, null)
        return status to (runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
    }

    private fun raw(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> {
        val connection = runCatching { URL(HOST + path).openConnection() as HttpURLConnection }
            .getOrElse { throw DysonCloudException(DysonCloudException.Reason.NETWORK) }
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "android client")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val status = connection.responseCode
            if (status >= 500) throw DysonCloudException(DysonCloudException.Reason.NETWORK)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: java.io.IOException) {
            throw DysonCloudException(DysonCloudException.Reason.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    /** Hesaptaki yerel parola, uygulamaya gömülü sabit bir anahtarla şifreli gelir. */
    private fun decryptCredential(encrypted: String): String {
        val key = ByteArray(32) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        }
        val plain = cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))
        val unpadded = plain.copyOf(plain.size - (plain.last().toInt() and 0xFF).coerceIn(0, plain.size))
        return JSONObject(String(unpadded)).getString("apPasswordHash")
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
