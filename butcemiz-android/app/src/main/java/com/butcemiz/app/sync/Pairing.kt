package com.butcemiz.app.sync

import com.butcemiz.app.core.Ids
import com.butcemiz.app.prefs.AppPrefs
import com.butcemiz.app.prefs.SyncConfig
import java.util.Base64

/**
 * Davet kodu: "BTZ1.<konu>.<anahtar>[.<sunucu>]"
 * Hem QR olarak gösterilir hem de WhatsApp vb. ile metin olarak paylaşılabilir.
 */
object PairingCode {
    private const val HEAD = "BTZ1"
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()
    private val pattern = Regex("BTZ1\\.([A-Za-z0-9_-]{16,64})\\.([A-Za-z0-9_-]{43})(?:\\.([A-Za-z0-9_-]+))?")

    fun newConfig(server: String = AppPrefs.DEFAULT_SERVER): SyncConfig =
        SyncConfig(server.trimEnd('/'), "btz" + Ids.random(29), Crypto.keyToString(Crypto.newKey()))

    fun encode(cfg: SyncConfig): String {
        val sb = StringBuilder(HEAD).append('.').append(cfg.topic).append('.').append(cfg.keyB64)
        if (cfg.server.trimEnd('/') != AppPrefs.DEFAULT_SERVER) {
            sb.append('.').append(enc.encodeToString(cfg.server.trimEnd('/').toByteArray()))
        }
        return sb.toString()
    }

    /** Metnin içinden (ör. paylaşılan bir mesajdan) kodu bulup çözer. */
    fun decode(text: String): SyncConfig? {
        val m = pattern.find(text.replace("\n", "").replace(" ", "")) ?: return null
        val topic = m.groupValues[1]
        val key = m.groupValues[2]
        if (Crypto.keyFromString(key) == null) return null
        val server = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let {
            try {
                String(dec.decode(it))
            } catch (e: IllegalArgumentException) {
                null
            }
        }?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: AppPrefs.DEFAULT_SERVER
        return SyncConfig(server, topic, key)
    }

    fun shareText(cfg: SyncConfig, householdName: String): String =
        "\"$householdName\" ortak bütçesine katılmak için Bütçemiz uygulamasında " +
            "\"Eşimin bütçesine katıl\" → \"Kodu yapıştır\" adımını kullan.\n\n" +
            "Davet kodu (yalnızca eşinle paylaş):\n${encode(cfg)}"
}
