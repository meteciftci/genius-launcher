package dev.evim.tv.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

enum class PinCheck { OK, WRONG, BLOCKED }

/**
 * Kart kilidinin PIN'i. PIN'in kendisi değil, rastgele tuzla SHA-256 özeti "tuz:özet" olarak saklanır.
 * 4 hane kaba kuvvete dayanmaz; asıl koruma uygulamanın kendi deposunun başka uygulamalarca okunamaması.
 */
object PinLock {
    const val LENGTH = 4

    fun hash(pin: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return "${encode(salt)}:${encode(digest(salt, pin))}"
    }

    fun matches(stored: String, pin: String): Boolean {
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return false
        return MessageDigest.isEqual(digest(decode(parts[0]), pin), decode(parts[1]))
    }

    private fun digest(salt: ByteArray, pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray())
        }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
