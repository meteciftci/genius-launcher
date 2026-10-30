package dev.evim.tv.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import dadb.Dadb
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Telefondaki tarayıcıdan TV'yi yönetmek için ev ağında çalışan küçük web sunucusu. Adres QR kodla verilir ve
 * gizli bir anahtar içerir; anahtarsız istekler reddedilir. Tuşlar TV'nin kendi ADB'siyle gönderilir, yani
 * hangi uygulama açık olursa olsun çalışır. Yazı, panoya konup yapıştırma tuşuna basılarak yazılır; böylece
 * ç, ğ, ı, ö, ş, ü da çıkar.
 */
class PhoneRemote(private val context: Context, private val prefs: LauncherPrefs) : Closeable {
    private val token = prefs.remoteToken
    private val server = ServerSocket(prefs.remotePort).also { prefs.remotePort = it.localPort }
    private val main = Handler(Looper.getMainLooper())
    private val adb = LocalAdb(context)
    private var connection: Dadb? = null
    @Volatile private var open = true

    /** Telefonun açacağı adres; TV ev ağına bağlı değilse null. */
    val url: String? get() = ReceiveServer.localAddress(context)?.let { "http://$it:${server.localPort}/$token/" }

    init {
        thread(isDaemon = true, name = "remote-accept") {
            while (open) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(isDaemon = true, name = "remote-client") { runCatching { socket.use(::serve) } }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = TIMEOUT_MS
        val input = socket.getInputStream()
        val out = socket.getOutputStream()
        val head = Http.readHead(input) ?: return
        val requestLine = head.lineSequence().first().split(' ')
        if (requestLine.size < 2) return
        val headers = head.lineSequence().drop(1).mapNotNull { line ->
            line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() }
        }.toMap()
        val path = requestLine[1].substringBefore('?')
        val query = requestLine[1].substringAfter('?', "")
        when {
            requestLine[0] == "GET" && (path == "/$token/" || path == "/$token") -> {
                val turkish = headers["accept-language"].orEmpty().lowercase().startsWith("tr")
                Http.respond(out, 200, "text/html; charset=utf-8", RemotePage.html(turkish).toByteArray())
            }
            requestLine[0] == "POST" && path == "/$token/key" -> {
                val code = query.removePrefix("k=").let(KEYS::get)
                    ?: return Http.respond(out, 400, "text/plain", "bad key".toByteArray())
                Http.respond(out, if (send("input keyevent $code")) 200 else 500, "text/plain", ByteArray(0))
            }
            requestLine[0] == "POST" && path == "/$token/text" -> {
                val length = headers["content-length"]?.toIntOrNull()?.takeIf { it in 1..MAX_TEXT }
                    ?: return Http.respond(out, 400, "text/plain", "bad request".toByteArray())
                val text = String(input.readNBytes(length), Charsets.UTF_8)
                Http.respond(out, if (type(text)) 200 else 500, "text/plain", ByteArray(0))
            }
            else -> Http.respond(out, 404, "text/plain", "not found".toByteArray())
        }
    }

    /** Yazıyı panoya koyar ve odaktaki alana yapıştırır. */
    private fun type(text: String): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        main.post {
            runCatching {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("genius", text))
            }
            latch.countDown()
        }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return send("input keyevent ${KeyEvent.KEYCODE_PASTE}")
    }

    /** Bağlantı açık tutulur; koptuysa bir kez yeniden kurulur. */
    @Synchronized
    private fun send(command: String): Boolean {
        repeat(2) {
            val ok = runCatching {
                val dadb = connection ?: adb.connect().also { connection = it }
                dadb.shell(command).exitCode == 0
            }.getOrElse {
                runCatching { connection?.close() }
                connection = null
                false
            }
            if (ok) return true
        }
        return false
    }

    @Synchronized
    override fun close() {
        open = false
        runCatching { server.close() }
        runCatching { connection?.close() }
        connection = null
    }

    companion object {
        const val DEFAULT_PORT = 8765
        private const val TIMEOUT_MS = 10_000
        private const val MAX_TEXT = 4000

        /** Sayfanın gönderebildiği tuşlar; başka kod kabul edilmez. */
        private val KEYS = mapOf(
            "up" to KeyEvent.KEYCODE_DPAD_UP,
            "down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
            "back" to KeyEvent.KEYCODE_BACK,
            "home" to KeyEvent.KEYCODE_HOME,
            "volup" to KeyEvent.KEYCODE_VOLUME_UP,
            "voldown" to KeyEvent.KEYCODE_VOLUME_DOWN,
            "mute" to KeyEvent.KEYCODE_VOLUME_MUTE,
            "play" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "delete" to KeyEvent.KEYCODE_DEL,
            "enter" to KeyEvent.KEYCODE_ENTER,
        )

        fun newToken(): String = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    }
}
