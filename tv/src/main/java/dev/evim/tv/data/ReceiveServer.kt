package dev.evim.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/** Telefondan gelen bir dosya. [received] / [total] ilerleme; [file] bitince dolar. */
data class Incoming(val id: Int, val name: String, val total: Long, val received: Long, val state: State, val file: File? = null) {
    enum class State { RECEIVING, DONE, FAILED, NO_SPACE }
}

/**
 * "Dosya al" ekranı açıkken ev ağında çalışan küçük bir web sunucusu. Telefon QR koddaki adresi açınca bir
 * yükleme sayfası gelir; seçilen dosyalar [folder] klasörüne yazılır. Adres her açılışta değişen rastgele bir
 * anahtar içerir, anahtarsız istekler reddedilir. Ekran kapanınca sunucu da kapanır.
 */
class ReceiveServer(context: Context, private val folder: File, private val onChange: (List<Incoming>) -> Unit) : Closeable {
    private val token = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private val server = ServerSocket(0)
    private val items = linkedMapOf<Int, Incoming>()
    private var nextId = 0
    @Volatile private var open = true

    /** Telefonun açacağı adres; TV ev ağına bağlı değilse null. */
    val url: String? = localAddress(context)?.let { "http://$it:${server.localPort}/$token/" }

    init {
        thread(isDaemon = true, name = "receive-accept") {
            while (open) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(isDaemon = true, name = "receive-client") { runCatching { socket.use(::serve) } }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = TIMEOUT_MS
        val input = socket.getInputStream()
        val out = socket.getOutputStream()
        val head = readHead(input) ?: return
        val requestLine = head.lineSequence().first().split(' ')
        if (requestLine.size < 2) return
        val method = requestLine[0]
        val target = requestLine[1]
        val headers = head.lineSequence().drop(1).mapNotNull { line ->
            line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() }
        }.toMap()
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")
        when {
            method == "GET" && (path == "/$token/" || path == "/$token") -> {
                val turkish = headers["accept-language"].orEmpty().lowercase().startsWith("tr")
                respond(out, 200, "text/html; charset=utf-8", UploadPage.html(turkish).toByteArray())
            }
            method == "POST" && path == "/$token/upload" -> {
                val name = query.split('&').firstOrNull { it.startsWith("name=") }
                    ?.let { URLDecoder.decode(it.removePrefix("name="), "UTF-8") }
                val length = headers["content-length"]?.toLongOrNull()
                if (name == null || length == null) return respond(out, 400, "text/plain", "bad request".toByteArray())
                val status = receive(input, cleanName(name), length)
                respond(out, status, "application/json", """{"status":$status}""".toByteArray())
            }
            else -> respond(out, 404, "text/plain", "not found".toByteArray())
        }
    }

    /** Dosyayı önce gizli bir geçici adla yazar, bitince asıl adına çevirir; yarım dosya klasörde görünmez. */
    private fun receive(input: InputStream, name: String, length: Long): Int {
        val id = synchronized(this) { nextId++ }
        if (runCatching { StatFs(folder.path).availableBytes }.getOrDefault(0) < length + FREE_MARGIN) {
            update(Incoming(id, name, length, 0, Incoming.State.NO_SPACE))
            return 507
        }
        folder.mkdirs()
        val part = File(folder, ".$name.$id.part")
        var received = 0L
        update(Incoming(id, name, length, 0, Incoming.State.RECEIVING))
        try {
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var lastReport = 0L
                while (received < length) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), length - received).toInt())
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    received += n
                    if (received - lastReport >= REPORT_BYTES) {
                        lastReport = received
                        update(Incoming(id, name, length, received, Incoming.State.RECEIVING))
                    }
                }
            }
            if (received < length) throw SocketException("short")
            val target = synchronized(this) { uniqueFile(name).also { part.renameTo(it) } }
            update(Incoming(id, target.name, length, length, Incoming.State.DONE, target))
            return 200
        } catch (e: Exception) {
            part.delete()
            update(Incoming(id, name, length, received, Incoming.State.FAILED))
            return 500
        }
    }

    private fun update(item: Incoming) {
        val snapshot = synchronized(items) {
            items[item.id] = item
            items.values.toList()
        }
        if (open) onChange(snapshot)
    }

    /** "foto.jpg" varsa "foto (1).jpg", "foto (2).jpg"… */
    private fun uniqueFile(name: String): File {
        var file = File(folder, name)
        var n = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        while (file.exists()) file = File(folder, "$base (${n++})$ext")
        return file
    }

    private fun readHead(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var match = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (bytes.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            bytes.write(b)
            match = if (b.toByte() == end[match]) match + 1 else if (b == 13) 1 else 0
            if (match == 4) return bytes.toString(Charsets.UTF_8.name())
        }
        return null
    }

    private fun respond(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 507 -> "Insufficient Storage"; else -> "Error" }
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    override fun close() {
        open = false
        runCatching { server.close() }
    }

    companion object {
        private const val TIMEOUT_MS = 30_000
        private const val MAX_HEAD = 16 * 1024
        private const val REPORT_BYTES = 512 * 1024L
        // Dosya yazılırken TV'nin kendisine de yer kalsın
        private const val FREE_MARGIN = 200L * 1024 * 1024

        /** Klasör yolu ve görünmez karakterler atılır; "../" ile dışarı çıkılamaz. */
        fun cleanName(name: String): String {
            val clean = name.substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it != '\u007f' }
                .trim().trimStart('.')
                .take(180)
            return clean.ifEmpty { "dosya" }
        }

        private fun localAddress(context: Context): String? {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val links = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
            return links.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}
