package dev.evim.tv.data.home

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Eufy kamerasının yerel RTSP yayını (TCP 554). Eufy uygulamasında Kamera Ayarları > Genel > Depolama >
 * NAS (RTSP) açılınca kamera `rtsp://kullanıcı:parola@ip:554/live0` adresinden 1080p yayın verir.
 * Görüntü buluta gitmez. Ağ çağrıları arka plan iş parçacığından yapılmalı.
 */
class EufyCamera(val ip: String, private val user: String, private val password: String) {

    /** ExoPlayer'a verilecek adres; kullanıcı adı ve parola adresin içinde, özel karakterleri kaçırılmış. */
    val streamUrl: String get() = "rtsp://${Uri.encode(user)}:${Uri.encode(password)}@$ip:$PORT/$PATH"

    /** Yalnızca RTSP bağlantı noktasına bakar; yoklamada kamerayı yormamak için. */
    fun reachable(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(ip, PORT), TIMEOUT_MS) }
    }.isSuccess

    enum class Check { OK, UNREACHABLE, WRONG_LOGIN, NO_STREAM }

    /** Kullanıcı adı ve parolayla yayının tarifini ister (RTSP DESCRIBE, gerekirse Digest kimlik doğrulama). */
    fun check(): Check {
        val uri = "rtsp://$ip:$PORT/$PATH"
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, PORT), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = socket.getOutputStream()
                fun describe(cseq: Int, auth: String?): Pair<Int, Map<String, String>> {
                    val request = buildString {
                        append("DESCRIBE $uri RTSP/1.0\r\nCSeq: $cseq\r\nAccept: application/sdp\r\n")
                        if (auth != null) append("Authorization: $auth\r\n")
                        append("\r\n")
                    }
                    out.write(request.toByteArray())
                    out.flush()
                    return readResponse(reader)
                }
                var (status, headers) = describe(1, null)
                if (status == 401) {
                    val challenge = headers["www-authenticate"].orEmpty()
                    status = describe(2, authorization(challenge, uri)).first
                }
                when (status) {
                    200 -> Check.OK
                    401, 403 -> Check.WRONG_LOGIN
                    else -> Check.NO_STREAM
                }
            }
        }.getOrDefault(Check.UNREACHABLE)
    }

    private fun authorization(challenge: String, uri: String): String {
        if (challenge.startsWith("Basic", ignoreCase = true)) {
            return "Basic " + android.util.Base64.encodeToString("$user:$password".toByteArray(), android.util.Base64.NO_WRAP)
        }
        val realm = Regex("realm=\"([^\"]*)\"").find(challenge)?.groupValues?.get(1).orEmpty()
        val nonce = Regex("nonce=\"([^\"]*)\"").find(challenge)?.groupValues?.get(1).orEmpty()
        val ha1 = md5("$user:$realm:$password")
        val ha2 = md5("DESCRIBE:$uri")
        val response = md5("$ha1:$nonce:$ha2")
        return "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\""
    }

    /** Durum kodu ve başlıklar; gövde (SDP) okunmaz. */
    private fun readResponse(reader: BufferedReader): Pair<Int, Map<String, String>> {
        val status = reader.readLine()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length > 0) reader.skip(length.toLong())
        return status to headers
    }

    private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PORT = 554
        const val PATH = "live0"
        const val TIMEOUT_MS = 3000
    }
}
