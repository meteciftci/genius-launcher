package dev.evim.tv.data.home

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Eufy kamerasıyla ev ağında P2P oturumu; şimdilik yalnızca döndürme (Pan & Tilt) için. Hesap ya da bulut
 * gerekmez: kamera yerel aramaya (UDP 32108) P2P kimliğiyle cevap verir, kimlik geri gönderilince oturum açılır.
 * Döndürme komutu kameranın şifresiz kabul ettiği bir komuttur. Protokol bropat/eufy-security-client'tan
 * (MIT) alındı. Ağ çağrıları arka plan iş parçacığından.
 */
class EufyP2p(private val ip: String) : Closeable {

    enum class Direction(val code: Int) { LEFT(1), RIGHT(2), UP(3), DOWN(4) }

    private val socket = DatagramSocket().apply { soTimeout = 300 }
    private var camera: SocketAddress? = null
    private var seq = 0
    private val acked = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var open = true
    @Volatile var connected = false
        private set

    /** Kamerayı bulup oturumu açar; olmazsa false. */
    fun connect(timeoutMs: Long = 5_000): Boolean {
        val lookup = InetSocketAddress(ip, PORT)
        val end = System.currentTimeMillis() + timeoutMs
        var nextLookup = 0L
        val buffer = ByteArray(2048)
        while (open && System.currentTimeMillis() < end) {
            if (System.currentTimeMillis() >= nextLookup) {
                send(LOCAL_LOOKUP, byteArrayOf(0, 0), lookup)
                nextLookup = System.currentTimeMillis() + 1_000
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            }
            val data = packet.data.copyOf(packet.length)
            when (data.type()) {
                // Cevaptaki kimlik (20 bayt) aynen geri gönderilir
                LOCAL_LOOKUP_RESP -> if (data.size >= 24) send(CHECK_CAM, data.copyOfRange(4, 24) + byteArrayOf(0, 0, 0), packet.socketAddress)
                CAM_ID -> {
                    camera = packet.socketAddress
                    connected = true
                    thread(isDaemon = true, name = "eufy-p2p") { listen() }
                    command(CMD_GATEWAYINFO, null)
                    return true
                }
            }
        }
        return false
    }

    /** Kamerayı bir adım döndürür; kamera aldığını onaylamazsa birkaç kez yeniden gönderilir. */
    fun rotate(direction: Direction): Boolean {
        val json = """{"commandType":$CMD_INDOOR_ROTATE,"data":{"cmd_type":1,"rotate_type":${direction.code}}}"""
        return command(CMD_DOORBELL_SET_PAYLOAD, json.toByteArray())
    }

    private fun command(type: Int, value: ByteArray?): Boolean {
        val target = camera ?: return false
        val number = synchronized(this) { seq.also { seq = (seq + 1) and 0xFFFF } }
        val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
            .put(0xD1.toByte()).put(0).putShort(number.toShort()).put(MAGIC)
            .order(ByteOrder.LITTLE_ENDIAN).putShort(type.toShort()).array()
        val body = if (value == null) {
            byteArrayOf(0, 0, 0, 0, 1, 0, 0xFF.toByte(), 0, 0, 0)
        } else {
            ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putShort(value.size.toShort())
                .put(byteArrayOf(0, 0, 1, 0, 0, 0, 0, 0)).array() + value
        }
        repeat(RETRIES) {
            send(DATA, header + body, target)
            val until = System.currentTimeMillis() + 250
            while (System.currentTimeMillis() < until) {
                if (acked.remove(number)) return true
                Thread.sleep(20)
            }
        }
        return false
    }

    /** Kameradan gelenler: onaylar kaydedilir, gelen veriler onaylanır, ping'e cevap verilir. */
    private fun listen() {
        val buffer = ByteArray(4096)
        var nextPing = System.currentTimeMillis() + PING_MS
        while (open) {
            if (System.currentTimeMillis() >= nextPing) {
                camera?.let { send(PING, ByteArray(0), it) }
                nextPing = System.currentTimeMillis() + PING_MS
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: SocketException) {
                break
            }
            val data = packet.data.copyOf(packet.length)
            when (data.type()) {
                PING -> send(PONG, ByteArray(0), packet.socketAddress)
                ACK -> if (data.size >= 8) {
                    val count = data.u16(6)
                    for (i in 0 until count) if (data.size >= 10 + i * 2) acked += data.u16(8 + i * 2)
                }
                DATA -> if (data.size >= 8) {
                    val ack = byteArrayOf(0xD1.toByte(), data[5], 0, 1, data[6], data[7])
                    send(ACK, ack, packet.socketAddress)
                }
                END -> connected = false
            }
        }
    }

    private fun send(type: Int, payload: ByteArray, to: SocketAddress) {
        val message = byteArrayOf(0xF1.toByte(), type.toByte(), (payload.size shr 8).toByte(), payload.size.toByte()) + payload
        runCatching { socket.send(DatagramPacket(message, message.size, to)) }
    }

    override fun close() {
        if (!open) return
        open = false
        camera?.let { send(END, ByteArray(0), it) }
        connected = false
        socket.close()
    }

    private fun ByteArray.type() = if (size >= 4 && this[0] == 0xF1.toByte()) this[1].toInt() and 0xFF else -1
    private fun ByteArray.u16(at: Int) = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

    private companion object {
        const val PORT = 32108
        const val RETRIES = 3
        const val PING_MS = 5_000L
        val MAGIC = "XZYH".toByteArray()

        const val LOCAL_LOOKUP = 0x30
        const val CHECK_CAM = 0x41
        const val LOCAL_LOOKUP_RESP = 0x41
        const val CAM_ID = 0x42
        const val DATA = 0xD0
        const val ACK = 0xD1
        const val PING = 0xE0
        const val PONG = 0xE1
        const val END = 0xF0

        const val CMD_GATEWAYINFO = 1100
        const val CMD_DOORBELL_SET_PAYLOAD = 1700
        const val CMD_INDOOR_ROTATE = 6030
    }
}
