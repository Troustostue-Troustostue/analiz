package com.butcemiz.app.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.butcemiz.app.core.Ids
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Açık"), DARK("Koyu") }

/** Senkronizasyon ayarları: sunucu, gizli konu adı ve uçtan uca şifreleme anahtarı. */
data class SyncConfig(
    val server: String,
    val topic: String,
    val keyB64: String,
    val token: String? = null,
)

/** Cihaza özel (senkronize edilmeyen) ayarlar. */
class AppPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("butcemiz_prefs", Context.MODE_PRIVATE)

    private fun <T> state(v: T) = MutableStateFlow(v)

    // ---------- kimlik ----------
    val deviceId: String
        get() = sp.getString(K_DEVICE, null) ?: Ids.random(12).also { id -> sp.edit(commit = true) { putString(K_DEVICE, id) } }

    private val _myMemberId = state(sp.getString(K_MEMBER, null))
    val myMemberIdFlow: StateFlow<String?> = _myMemberId.asStateFlow()
    var myMemberId: String?
        get() = _myMemberId.value
        set(v) {
            sp.edit(commit = true) { putString(K_MEMBER, v) }
            _myMemberId.value = v
        }

    private val _onboarded = state(sp.getBoolean(K_ONBOARDED, false))
    val onboardedFlow: StateFlow<Boolean> = _onboarded.asStateFlow()
    var onboarded: Boolean
        get() = _onboarded.value
        set(v) {
            sp.edit(commit = true) { putBoolean(K_ONBOARDED, v) }
            _onboarded.value = v
        }

    // ---------- senkronizasyon ----------
    private val _sync = state(readSync())
    val syncConfigFlow: StateFlow<SyncConfig?> = _sync.asStateFlow()
    var syncConfig: SyncConfig?
        get() = _sync.value
        set(v) {
            sp.edit(commit = true) {
                if (v == null) {
                    remove(K_SYNC_SERVER); remove(K_SYNC_TOPIC); remove(K_SYNC_KEY); remove(K_SYNC_TOKEN)
                } else {
                    putString(K_SYNC_SERVER, v.server)
                    putString(K_SYNC_TOPIC, v.topic)
                    putString(K_SYNC_KEY, v.keyB64)
                    putString(K_SYNC_TOKEN, v.token)
                }
            }
            _sync.value = v
        }

    private fun readSync(): SyncConfig? {
        val topic = sp.getString(K_SYNC_TOPIC, null) ?: return null
        val key = sp.getString(K_SYNC_KEY, null) ?: return null
        return SyncConfig(sp.getString(K_SYNC_SERVER, null) ?: DEFAULT_SERVER, topic, key, sp.getString(K_SYNC_TOKEN, null))
    }

    /** ntfy'dan alınan son mesajın zamanı (saniye) */
    var lastMessageTime: Long
        get() = sp.getLong(K_LAST_MSG_TIME, 0L)
        set(v) = sp.edit { putLong(K_LAST_MSG_TIME, v) }

    var lastPublishedSeq: Long
        get() = sp.getLong(K_LAST_PUBLISHED, 0L)
        set(v) = sp.edit(commit = true) { putLong(K_LAST_PUBLISHED, v) }

    private val _paired = state(sp.getBoolean(K_PAIRED, false))
    val pairedFlow: StateFlow<Boolean> = _paired.asStateFlow()
    var paired: Boolean
        get() = _paired.value
        set(v) {
            sp.edit(commit = true) { putBoolean(K_PAIRED, v) }
            _paired.value = v
        }

    /** Akışın en son canlı olduğu an (ms). Uzun kopukluk tespiti için. */
    var lastAlive: Long
        get() = sp.getLong(K_LAST_ALIVE, 0L)
        set(v) = sp.edit { putLong(K_LAST_ALIVE, v) }

    private val _peerSeen = state(sp.getLong(K_PEER_SEEN, 0L))
    val lastPeerSeenFlow: StateFlow<Long> = _peerSeen.asStateFlow()
    var lastPeerSeen: Long
        get() = _peerSeen.value
        set(v) {
            sp.edit { putLong(K_PEER_SEEN, v) }
            _peerSeen.value = v
        }

    /** Davetle katılan cihaz, eşinden ilk verileri bekliyor */
    private val _awaiting = state(sp.getBoolean(K_AWAITING, false))
    val awaitingInitialSyncFlow: StateFlow<Boolean> = _awaiting.asStateFlow()
    var awaitingInitialSync: Boolean
        get() = _awaiting.value
        set(v) {
            sp.edit(commit = true) { putBoolean(K_AWAITING, v) }
            _awaiting.value = v
        }

    private val seenIds: ArrayDeque<String> by lazy {
        ArrayDeque(sp.getString(K_SEEN_IDS, "")!!.split(',').filter { it.isNotBlank() })
    }

    @Synchronized
    fun isMessageSeen(id: String): Boolean = seenIds.contains(id)

    @Synchronized
    fun markMessageSeen(id: String) {
        if (seenIds.contains(id)) return
        seenIds.addLast(id)
        while (seenIds.size > 300) seenIds.removeFirst()
        sp.edit { putString(K_SEEN_IDS, seenIds.joinToString(",")) }
    }

    var lastSyncRequestAt: Long
        get() = sp.getLong(K_LAST_REQ, 0L)
        set(v) = sp.edit { putLong(K_LAST_REQ, v) }

    var quotaBlockedUntil: Long
        get() = sp.getLong(K_QUOTA, 0L)
        set(v) = sp.edit { putLong(K_QUOTA, v) }

    // ---------- görünüm ----------
    private val _theme = state(ThemeMode.entries.getOrElse(sp.getInt(K_THEME, 0)) { ThemeMode.SYSTEM })
    val themeFlow: StateFlow<ThemeMode> = _theme.asStateFlow()
    var theme: ThemeMode
        get() = _theme.value
        set(v) {
            sp.edit { putInt(K_THEME, v.ordinal) }
            _theme.value = v
        }

    private val _dynamic = state(sp.getBoolean(K_DYNAMIC, false))
    val dynamicColorFlow: StateFlow<Boolean> = _dynamic.asStateFlow()
    var dynamicColor: Boolean
        get() = _dynamic.value
        set(v) {
            sp.edit { putBoolean(K_DYNAMIC, v) }
            _dynamic.value = v
        }

    private val _hide = state(sp.getBoolean(K_HIDE, false))
    val hideAmountsFlow: StateFlow<Boolean> = _hide.asStateFlow()
    var hideAmounts: Boolean
        get() = _hide.value
        set(v) {
            sp.edit { putBoolean(K_HIDE, v) }
            _hide.value = v
        }

    // ---------- güvenlik ----------
    private val _lock = state(sp.getBoolean(K_LOCK, false))
    val appLockFlow: StateFlow<Boolean> = _lock.asStateFlow()
    var appLock: Boolean
        get() = _lock.value
        set(v) {
            sp.edit { putBoolean(K_LOCK, v) }
            _lock.value = v
        }

    private val _secure = state(sp.getBoolean(K_SECURE, false))
    val secureScreenFlow: StateFlow<Boolean> = _secure.asStateFlow()
    var secureScreen: Boolean
        get() = _secure.value
        set(v) {
            sp.edit { putBoolean(K_SECURE, v) }
            _secure.value = v
        }

    // ---------- bildirimler / arka plan ----------
    private val _bg = state(sp.getBoolean(K_BG, true))
    val backgroundSyncFlow: StateFlow<Boolean> = _bg.asStateFlow()
    var backgroundSync: Boolean
        get() = _bg.value
        set(v) {
            sp.edit { putBoolean(K_BG, v) }
            _bg.value = v
        }

    private val _nPartner = state(sp.getBoolean(K_N_PARTNER, true))
    val notifyPartnerFlow: StateFlow<Boolean> = _nPartner.asStateFlow()
    var notifyPartner: Boolean
        get() = _nPartner.value
        set(v) {
            sp.edit { putBoolean(K_N_PARTNER, v) }
            _nPartner.value = v
        }

    private val _nRem = state(sp.getBoolean(K_N_REMIND, true))
    val notifyRemindersFlow: StateFlow<Boolean> = _nRem.asStateFlow()
    var notifyReminders: Boolean
        get() = _nRem.value
        set(v) {
            sp.edit { putBoolean(K_N_REMIND, v) }
            _nRem.value = v
        }

    private val _nBudget = state(sp.getBoolean(K_N_BUDGET, true))
    val notifyBudgetFlow: StateFlow<Boolean> = _nBudget.asStateFlow()
    var notifyBudget: Boolean
        get() = _nBudget.value
        set(v) {
            sp.edit { putBoolean(K_N_BUDGET, v) }
            _nBudget.value = v
        }

    /** Hatırlatma/uyarı bildirimlerinin tekrar gönderilmemesi için anahtar listesi */
    fun wasNotified(key: String): Boolean = sp.getStringSet(K_NOTIFIED, emptySet())!!.contains(key)

    fun markNotified(key: String) {
        val cur = sp.getStringSet(K_NOTIFIED, emptySet())!!.toMutableSet()
        cur += key
        // sınırsız büyümesin
        val trimmed = if (cur.size > 400) cur.toList().takeLast(300).toSet() else cur
        sp.edit { putStringSet(K_NOTIFIED, trimmed) }
    }

    var batteryPromptShown: Boolean
        get() = sp.getBoolean(K_BATTERY_PROMPT, false)
        set(v) = sp.edit { putBoolean(K_BATTERY_PROMPT, v) }

    var lastRatesFetch: Long
        get() = sp.getLong(K_RATES, 0L)
        set(v) = sp.edit { putLong(K_RATES, v) }

    fun lastAccount(type: com.butcemiz.app.data.TxType): String? = sp.getString("last_acc_${type.name}", null)
    fun setLastAccount(type: com.butcemiz.app.data.TxType, id: String?) = sp.edit { putString("last_acc_${type.name}", id) }

    /** Genel amaçlı tek seferlik bayraklar (ör. izin istendi mi) */
    fun flag(key: String): Boolean = sp.getBoolean("flag_$key", false)
    fun setFlag(key: String, v: Boolean = true) = sp.edit { putBoolean("flag_$key", v) }

    /** Bu cihazdaki tüm ayarları siler (veritabanı ayrıca temizlenir). */
    fun wipe() {
        sp.edit(commit = true) { clear() }
    }

    fun resetSyncState() {
        sp.edit(commit = true) {
            remove(K_LAST_MSG_TIME); remove(K_LAST_PUBLISHED); remove(K_PAIRED); remove(K_LAST_REQ); remove(K_QUOTA)
            remove(K_LAST_ALIVE); remove(K_SEEN_IDS); remove(K_AWAITING)
        }
        _paired.value = false
        _awaiting.value = false
        synchronized(this) { seenIds.clear() }
    }

    companion object {
        const val DEFAULT_SERVER = "https://ntfy.sh"
        private const val K_DEVICE = "device_id"
        private const val K_MEMBER = "my_member_id"
        private const val K_ONBOARDED = "onboarded"
        private const val K_SYNC_SERVER = "sync_server"
        private const val K_SYNC_TOPIC = "sync_topic"
        private const val K_SYNC_KEY = "sync_key"
        private const val K_SYNC_TOKEN = "sync_token"
        private const val K_LAST_MSG_TIME = "last_msg_time"
        private const val K_LAST_PUBLISHED = "last_published_seq"
        private const val K_PAIRED = "paired"
        private const val K_LAST_REQ = "last_sync_req"
        private const val K_QUOTA = "quota_blocked_until"
        private const val K_THEME = "theme"
        private const val K_DYNAMIC = "dynamic_color"
        private const val K_HIDE = "hide_amounts"
        private const val K_LOCK = "app_lock"
        private const val K_SECURE = "secure_screen"
        private const val K_BG = "background_sync"
        private const val K_N_PARTNER = "notify_partner"
        private const val K_N_REMIND = "notify_reminders"
        private const val K_N_BUDGET = "notify_budget"
        private const val K_NOTIFIED = "notified_keys"
        private const val K_BATTERY_PROMPT = "battery_prompt"
        private const val K_RATES = "last_rates_fetch"
        private const val K_LAST_ALIVE = "last_alive"
        private const val K_PEER_SEEN = "peer_seen"
        private const val K_AWAITING = "awaiting_initial"
        private const val K_SEEN_IDS = "seen_msg_ids"
    }
}
