package dev.evim.tv.data.home

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Eufy kameraları P2P yerel arama paketine (UDP 32108'e `F1 30 00 00`) kendi P2P kimlikleriyle cevap verir:
 * `F1 41 00 14` + ön ek (8 bayt, ör. "EUPRAMA") + numara (4 bayt) + kontrol (5 bayt). Cevabın geldiği adres
 * kameranın IP'sidir; kimlik değişmediği için adresi değişen kamera yeniden tanınır.
 * Ağ çağrısı: arka plan iş parçacığından.
 */
object EufyDiscovery {
    private const val PORT = 32108
    private val SEARCH = byteArrayOf(0xF1.toByte(), 0x30, 0, 0)

    /** P2P kimliği → IP. */
    fun scan(durationMs: Long = 3_000): Map<String, String> {
        val found = mutableMapOf<String, String>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val end = System.currentTimeMillis() + durationMs
            var nextSend = 0L
            val buffer = ByteArray(256)
            while (System.currentTimeMillis() < end) {
                // Bazı cevaplar kaybolabiliyor; arama birkaç kez tekrarlanır
                if (System.currentTimeMillis() >= nextSend) {
                    runCatching { socket.send(DatagramPacket(SEARCH, SEARCH.size, InetAddress.getByName("255.255.255.255"), PORT)) }
                    nextSend = System.currentTimeMillis() + 1_000
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                parse(packet.data.copyOf(packet.length))?.let { found[it] = packet.address.hostAddress.orEmpty() }
            }
        }
        return found
    }

    private fun parse(data: ByteArray): String? {
        if (data.size < 24 || data[0] != 0xF1.toByte() || data[1] != 0x41.toByte()) return null
        val prefix = String(data, 4, 8, Charsets.US_ASCII).trimEnd('\u0000')
        val number = (4 until 8).fold(0L) { acc, i -> (acc shl 8) or (data[8 + i].toLong() and 0xFF) }
        val check = String(data, 16, 8, Charsets.US_ASCII).trimEnd('\u0000')
        if (!prefix.startsWith("EU")) return null
        return "$prefix-%06d-$check".format(number)
    }
}
