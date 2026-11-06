package com.butcemiz.app.core

import java.security.SecureRandom

object Ids {
    private val rnd = SecureRandom()
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun random(len: Int = 16): String = buildString(len) {
        repeat(len) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
    }

    fun newId(): String = random(16)

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }
}

/**
 * Hibrit mantıksal saat (HLC). Değer = (milisaniye << 16) | sayaç.
 * İki cihaz arasında "son yazan kazanır" sıralamasını saat kaymalarına karşı güvenli kılar.
 */
class HybridClock(initial: Long, private val persist: (Long) -> Unit) {
    @Volatile
    private var last: Long = initial

    @Synchronized
    fun now(wallMillis: Long = System.currentTimeMillis()): Long {
        val lastMillis = last ushr 16
        val lastCounter = last and 0xFFFF
        val next = if (wallMillis > lastMillis) {
            wallMillis shl 16
        } else {
            if (lastCounter >= 0xFFFF) (lastMillis + 1) shl 16 else (lastMillis shl 16) or (lastCounter + 1)
        }
        last = next
        persist(next)
        return next
    }

    @Synchronized
    fun observe(remote: Long) {
        if (remote > last) {
            last = remote
            persist(remote)
        }
    }

    fun current(): Long = last

    companion object {
        fun millisOf(hlc: Long): Long = hlc ushr 16

        /** a, b'den daha yeni mi? (eşitlikte cihaz kimliği karşılaştırılır) */
        fun isNewer(aStamp: Long, aDevice: String, bStamp: Long, bDevice: String): Boolean =
            aStamp > bStamp || (aStamp == bStamp && aDevice > bDevice)
    }
}
