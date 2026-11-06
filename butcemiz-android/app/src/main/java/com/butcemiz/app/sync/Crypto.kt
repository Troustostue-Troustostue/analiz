package com.butcemiz.app.sync

import com.butcemiz.app.core.Ids
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Uçtan uca şifreleme: gzip + AES-256-GCM. Anahtar yalnızca iki telefonda bulunur
 * (davet kodu / QR ile paylaşılır); aracı sunucu (ntfy) yalnızca anlamsız metin görür.
 */
object Crypto {
    const val PREFIX = "BTZ1."
    private val AAD = "butcemiz-v1".toByteArray()
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()

    fun newKey(): ByteArray = Ids.randomBytes(32)
    fun keyToString(key: ByteArray): String = enc.encodeToString(key)
    fun keyFromString(s: String): ByteArray? = try {
        dec.decode(s.trim()).takeIf { it.size == 32 }
    } catch (e: IllegalArgumentException) {
        null
    }

    fun seal(key: ByteArray, plain: ByteArray): String {
        val iv = Ids.randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        val ct = c.doFinal(gzip(plain))
        return PREFIX + enc.encodeToString(iv + ct)
    }

    /** Anahtar yanlışsa veya veri bozuksa null döner. */
    fun open(key: ByteArray, text: String): ByteArray? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        val raw = try {
            dec.decode(t.substring(PREFIX.length))
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (raw.size < 12 + 16) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, 12))
            c.updateAAD(AAD)
            gunzip(c.doFinal(raw, 12, raw.size - 12))
        } catch (e: Exception) {
            null
        }
    }

    private fun gzip(b: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(b.size / 2 + 32)
        GZIPOutputStream(bos).use { it.write(b) }
        return bos.toByteArray()
    }

    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
