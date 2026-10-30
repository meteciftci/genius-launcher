package dev.evim.tv.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Ev ağındaki küçük web sunucularının (dosya alma, telefondan kumanda) ortak parçaları. */
internal object Http {
    private const val MAX_HEAD = 16 * 1024

    /** İstek satırı ve başlıklar; boş satıra kadar okunur. Bağlantı kesilir ya da çok büyükse null. */
    fun readHead(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
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

    fun respond(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 507 -> "Insufficient Storage"; else -> "Error" }
        out.write("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }
}
