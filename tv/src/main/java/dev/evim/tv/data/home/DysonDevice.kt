package dev.evim.tv.data.home

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DysonState(
    val power: Boolean,
    val auto: Boolean,
    /** 1-10; otomatik modda cihazın seçtiği hız ya da null. */
    val fanSpeed: Int?,
    val heating: Boolean,
    val targetCelsius: Int?,
    val temperatureCelsius: Int?,
    val humidity: Int?,
    val pm25: Int?,
    /** Dyson uygulamasındaki genel hava kalitesi: ölçümlerin en kötüsü. */
    val airQuality: AirQuality?,
)

/** Dyson uygulamasının kullandığı seviyeler, iyiden kötüye. */
enum class AirQuality { GOOD, FAIR, POOR, VERY_POOR, SEVERE, EXTREME }

/** Dyson'a hedef sıcaklık gönderirken kullanılan biçim: Kelvin × 10, dört hane. */
fun celsiusToDyson(celsius: Int): String = "%04d".format(Math.round((celsius + 273.15f) * 10))

/**
 * Dyson'un yerel MQTT sunucusu (TCP 1883). Kullanıcı adı seri no, parola MyDyson'dan alınan
 * kimlik bilgisi. Her çağrı kısa bir bağlantı açar; arka plan iş parçacığından çağrılmalı.
 */
class DysonDevice(
    private val serial: String,
    private val credential: String,
    private val productType: String,
    private val ip: String,
) {
    private val commandTopic = "$productType/$serial/command"
    private val statusTopic = "$productType/$serial/status/current"

    fun state(): DysonState = session { readState() }

    /** Örn. mapOf("fpwr" to "ON"); ardından güncel durumu döner. */
    fun set(values: Map<String, String>): DysonState = session {
        publish(commandTopic, message("STATE-SET").put("mode-reason", "LAPP").put("data", JSONObject(values)))
        readState()
    }

    private fun <T> session(block: Mqtt.() -> T): T =
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, PORT), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val mqtt = Mqtt(DataInputStream(socket.getInputStream()), socket.getOutputStream())
            mqtt.connect("genius-${System.nanoTime()}", serial, credential)
            mqtt.subscribe(statusTopic)
            try {
                mqtt.block()
            } finally {
                runCatching { mqtt.disconnect() }
            }
        }

    private fun Mqtt.readState(): DysonState {
        publish(commandTopic, message("REQUEST-CURRENT-STATE"))
        publish(commandTopic, message("REQUEST-PRODUCT-ENVIRONMENT-CURRENT-SENSOR-DATA"))
        var product: JSONObject? = null
        var sensors: JSONObject? = null
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while ((product == null || sensors == null) && System.currentTimeMillis() < deadline) {
            val text = receive() ?: continue
            val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
            when (json.optString("msg")) {
                "CURRENT-STATE" -> product = json.optJSONObject("product-state")
                "ENVIRONMENTAL-CURRENT-SENSOR-DATA" -> sensors = json.optJSONObject("data")
            }
        }
        val p = product ?: error("Dyson $ip durum vermedi")
        val s = sensors ?: JSONObject()
        return DysonState(
            power = p.optString("fpwr") == "ON",
            auto = p.optString("auto") == "ON",
            fanSpeed = p.optString("fnsp").toIntOrNull(),
            heating = p.optString("hmod") == "HEAT",
            targetCelsius = p.optString("hmax").kelvinToCelsius(),
            temperatureCelsius = s.optString("tact").kelvinToCelsius(),
            humidity = s.optString("hact").toIntOrNull(),
            pm25 = s.optString("pm25").toIntOrNull(),
            airQuality = airQuality(s),
        )
    }

    private fun message(name: String) = JSONObject().put("msg", name).put("time", isoNow())

    /** MQTT 3.1.1'in Dyson için yeten kadarı: QoS 0 yayın ve abonelik. */
    private class Mqtt(private val input: DataInputStream, private val output: OutputStream) {
        fun connect(clientId: String, user: String, password: String) {
            val body = ByteArrayOutputStream().apply {
                writeString("MQTT")
                write(4) // protokol seviyesi 3.1.1
                write(0xC2) // kullanıcı adı + parola + temiz oturum
                write(0); write(KEEP_ALIVE_S)
                writeString(clientId)
                writeString(user)
                writeString(password)
            }
            writePacket(0x10, body.toByteArray())
            val (type, ack) = readPacket()
            check(type == 0x20 && ack.size >= 2 && ack[1].toInt() == 0) { "Dyson MQTT bağlantısı reddedildi (${ack.getOrNull(1)})" }
        }

        fun subscribe(topic: String) {
            val body = ByteArrayOutputStream().apply {
                write(0); write(1) // paket kimliği
                writeString(topic)
                write(0) // QoS 0
            }
            writePacket(0x82, body.toByteArray())
        }

        fun publish(topic: String, json: JSONObject) {
            val body = ByteArrayOutputStream().apply {
                writeString(topic)
                write(json.toString().toByteArray(Charsets.UTF_8))
            }
            writePacket(0x30, body.toByteArray())
        }

        /** Sıradaki yayının içeriği; yayın dışı paketlerde (SUBACK vb.) null. */
        fun receive(): String? {
            val (type, body) = readPacket()
            if (type and 0xF0 != 0x30) return null
            val topicLength = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
            var start = 2 + topicLength
            if (type and 0x06 != 0) start += 2 // QoS > 0 ise paket kimliği
            return String(body, start, body.size - start, Charsets.UTF_8)
        }

        fun disconnect() = writePacket(0xE0, ByteArray(0))

        private fun writePacket(header: Int, body: ByteArray) {
            val packet = ByteArrayOutputStream()
            packet.write(header)
            var length = body.size
            do {
                var digit = length % 128
                length /= 128
                if (length > 0) digit = digit or 0x80
                packet.write(digit)
            } while (length > 0)
            packet.write(body)
            output.write(packet.toByteArray())
            output.flush()
        }

        private fun readPacket(): Pair<Int, ByteArray> {
            val type = input.readUnsignedByte()
            var length = 0
            var multiplier = 1
            do {
                val digit = input.readUnsignedByte()
                length += (digit and 0x7F) * multiplier
                multiplier *= 128
            } while (digit and 0x80 != 0)
            return type to ByteArray(length).also(input::readFully)
        }

        private fun ByteArrayOutputStream.writeString(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            write(bytes.size shr 8); write(bytes.size and 0xFF)
            write(bytes)
        }
    }

    private companion object {
        const val PORT = 1883
        const val TIMEOUT_MS = 5000
        const val KEEP_ALIVE_S = 30

        /** Dyson sıcaklıkları Kelvin × 10 olarak gönderir ("2942" = 21 °C). */
        fun String.kelvinToCelsius(): Int? = toIntOrNull()?.let { Math.round(it / 10f - 273.15f) }

        /**
         * Dyson uygulamasının eşikleri. PM değerleri µg/m³; VOC ve NO₂ ham değerin onda biri olan
         * endeks; formaldehit ham değerin binde biri, mg/m³. Sensör hazır değilse değer "INIT" gelir.
         */
        fun airQuality(sensors: JSONObject): AirQuality? {
            fun level(key: String, divisor: Float, limits: List<Float>): Int? =
                sensors.optString(key).toIntOrNull()?.let { raw ->
                    val value = raw / divisor
                    limits.indexOfFirst { value <= it }.let { if (it < 0) limits.size else it }
                }
            val levels = listOfNotNull(
                level("pm25", 1f, listOf(35f, 53f, 70f, 150f, 250f)),
                level("pm10", 1f, listOf(50f, 75f, 100f, 350f, 420f)),
                level("va10", 10f, listOf(3f, 6f, 8f)),
                level("noxl", 10f, listOf(3f, 6f, 8f)),
                level("hcho", 1000f, listOf(0.099f, 0.299f, 0.499f)),
            )
            return levels.maxOrNull()?.let { AirQuality.entries[it] }
        }

        fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
    }
}
