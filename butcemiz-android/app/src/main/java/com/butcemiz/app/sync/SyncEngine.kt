package com.butcemiz.app.sync

import com.butcemiz.app.data.AppJson
import com.butcemiz.app.data.AppliedChange
import com.butcemiz.app.data.OpDto
import com.butcemiz.app.data.OpLog
import com.butcemiz.app.prefs.AppPrefs
import com.butcemiz.app.prefs.SyncConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Şifreli zarf: ntfy üzerinden giden her mesajın içeriği. */
@Serializable
data class Envelope(
    val v: Int = 1,
    /** "ops" = değişiklikler, "req" = eksiklerimi gönder, "hi" = merhaba */
    val k: String,
    /** gönderen cihaz */
    val f: String,
    /** gönderenin durum vektörü */
    val sv: Map<String, Long> = emptyMap(),
    val ops: List<OpDto> = emptyList(),
    val t: Long = 0,
    /** gönderen cihaz adı */
    val n: String? = null,
)

interface SyncListener {
    suspend fun onRemoteChanges(changes: List<AppliedChange>)
    suspend fun onPeerJoined(deviceName: String?)
}

/**
 * İki telefon arasında gerçek zamanlı, uçtan uca şifreli senkronizasyon.
 *
 * - Her yerel değişiklik işlem günlüğüne (op-log) yazılır ve kısa bir gecikmeyle toplu yayınlanır.
 * - Telefonlar ntfy konusuna abone olur; mesajlar anında gelir, çevrimdışı kalınan süre
 *   için sunucu 12 saatlik önbellek tutar.
 * - Daha uzun kopukluklarda "req" (durum vektörü) gönderilir; karşı taraf yalnızca eksikleri yollar.
 * - Çakışmalar kayıt bazında "son yazan kazanır" ile çözülür; bakiyeler hareketlerin
 *   toplamından hesaplandığı için para kaybı/çift sayım olmaz.
 */
class SyncEngine(
    private val prefs: AppPrefs,
    private val log: OpLog,
    private val transport: NtfyTransport,
    private val listener: SyncListener,
    private val scope: CoroutineScope,
    private val deviceName: () -> String,
) {
    enum class State { NOT_CONFIGURED, WAITING_PARTNER, IDLE, CONNECTING, CONNECTED, OFFLINE, QUOTA }

    data class Status(
        val state: State = State.IDLE,
        val detail: String? = null,
        val lastEventAt: Long = 0,
        val pendingOps: Long = 0,
    )

    private val _status = MutableStateFlow(Status(if (prefs.syncConfig == null) State.NOT_CONFIGURED else State.IDLE))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mutex = Mutex()
    private val holders = mutableSetOf<String>()
    private var streamJob: Job? = null
    private var stopJob: Job? = null
    private val wake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)
    private val requestSignal = Channel<Boolean>(Channel.CONFLATED)
    private val lastResponseAt = HashMap<String, Long>()

    @Volatile
    var isConnected: Boolean = false
        private set

    init {
        scope.launch {
            for (s in flushSignal) {
                delay(600)
                runCatching { flush() }
            }
        }
        scope.launch {
            for (force in requestSignal) {
                delay(2_500)
                runCatching { mutex.withLock { sendRequest(force) } }
            }
        }
        scope.launch { refreshPending() }
    }

    // ===================== yaşam döngüsü =====================

    /** Bağlantıyı açık tutmak isteyen bileşen (ekran, servis) çağırır. */
    fun acquire(tag: String) = synchronized(this) {
        holders += tag
        stopJob?.cancel()
        stopJob = null
        if (streamJob?.isActive != true) streamJob = scope.launch { runStream() }
    }

    fun release(tag: String) = synchronized(this) {
        holders -= tag
        if (holders.isEmpty()) {
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(20_000)
                synchronized(this@SyncEngine) {
                    if (holders.isEmpty()) {
                        streamJob?.cancel()
                        streamJob = null
                        isConnected = false
                        setState(if (prefs.syncConfig == null) State.NOT_CONFIGURED else State.IDLE)
                    }
                }
            }
        }
    }

    /** Ağ geri geldiğinde ya da kullanıcı "şimdi eşitle" dediğinde beklemeyi keser. */
    fun poke() {
        wake.tryEmit(Unit)
    }

    /** Senkron ayarı değiştiğinde (katılma, sıfırlama) bağlantıyı yeniden kurar. */
    fun restart() = synchronized(this) {
        streamJob?.cancel()
        streamJob = null
        isConnected = false
        setState(if (prefs.syncConfig == null) State.NOT_CONFIGURED else State.IDLE)
        if (holders.isNotEmpty()) streamJob = scope.launch { runStream() }
    }

    fun onLocalChange() {
        flushSignal.trySend(Unit)
        scope.launch { refreshPending() }
    }

    /** Kullanıcı isteğiyle tam eşitleme isteği gönder. */
    fun requestNow(force: Boolean = true) {
        requestSignal.trySend(force)
        poke()
    }

    // ===================== canlı akış =====================

    private suspend fun runStream() {
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            val cfg = prefs.syncConfig
            val key = cfg?.let { Crypto.keyFromString(it.keyB64) }
            if (cfg == null || key == null) {
                setState(State.NOT_CONFIGURED)
                wake.first()
                continue
            }
            setState(State.CONNECTING)
            val aliveBefore = prefs.lastAlive
            try {
                transport.stream(cfg, sinceParam()).collect { ev ->
                    markAlive()
                    when (ev.event) {
                        "open" -> {
                            isConnected = true
                            backoff = 2_000L
                            setState(connectedState())
                            scope.launch { onConnected(aliveBefore) }
                        }
                        "message" -> mutex.withLock { handleEvent(cfg, key, ev) }
                    }
                }
                isConnected = false
            } catch (e: CancellationException) {
                isConnected = false
                throw e
            } catch (e: Exception) {
                isConnected = false
                setState(State.OFFLINE, "Bağlantı yok")
            }
            withTimeoutOrNull(backoff + Random.nextLong(0, 1_500)) { wake.first() }
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun connectedState(): State = when {
        System.currentTimeMillis() < prefs.quotaBlockedUntil -> State.QUOTA
        prefs.paired -> State.CONNECTED
        else -> State.WAITING_PARTNER
    }

    private suspend fun onConnected(aliveBefore: Long) {
        delay(3_000) // önbellekteki mesajlar önce işlensin
        flush()
        maybeRequestAfterGap(aliveBefore)
    }

    private fun maybeRequestAfterGap(aliveBefore: Long) {
        val now = System.currentTimeMillis()
        val gap = now - aliveBefore
        val missedCache = aliveBefore == 0L || gap > 11 * HOUR
        val daily = now - prefs.lastSyncRequestAt > 24 * HOUR
        when {
            !prefs.paired && prefs.awaitingInitialSync -> requestSignal.trySend(false)
            prefs.paired && (missedCache || daily) -> requestSignal.trySend(false)
        }
    }

    private fun sinceParam(): String {
        val t = prefs.lastMessageTime
        return if (t <= 0L) "all" else (t - 5).toString()
    }

    private var lastAliveWrite = 0L
    private fun markAlive() {
        val now = System.currentTimeMillis()
        _status.update { it.copy(lastEventAt = now) }
        if (now - lastAliveWrite > 60_000) {
            lastAliveWrite = now
            prefs.lastAlive = now
        }
    }

    // ===================== mesaj işleme =====================

    private suspend fun handleEvent(cfg: SyncConfig, key: ByteArray, ev: NtfyEvent) {
        if (ev.id.isNotEmpty() && prefs.isMessageSeen(ev.id)) return
        val url = ev.attachment?.url
        val raw = if (!url.isNullOrBlank()) transport.download(cfg, url) else ev.message
        if (ev.time > prefs.lastMessageTime) prefs.lastMessageTime = ev.time
        if (ev.id.isNotEmpty()) prefs.markMessageSeen(ev.id)
        if (raw == null) {
            // ekin süresi dolmuş: eksikleri yeniden iste
            requestSignal.trySend(false)
            return
        }
        val plain = Crypto.open(key, raw) ?: return
        val env = try {
            AppJson.decodeFromString(Envelope.serializer(), plain.decodeToString())
        } catch (e: Exception) {
            return
        }
        processEnvelope(env)
    }

    /** Test edilebilirlik için ayrı tutuldu. Çağıran mutex'i tutar. */
    internal suspend fun processEnvelope(env: Envelope) {
        val self = prefs.deviceId
        if (env.f == self) return
        prefs.lastPeerSeen = System.currentTimeMillis()
        if (!prefs.paired) {
            prefs.paired = true
            setState(State.CONNECTED)
            runCatching { listener.onPeerJoined(env.n) }
            flushSignal.trySend(Unit)
        }
        if (prefs.awaitingInitialSync && env.k == "ops") prefs.awaitingInitialSync = false
        when (env.k) {
            "ops" -> if (env.ops.isNotEmpty()) {
                val changes = log.applyRemote(env.ops)
                if (changes.isNotEmpty()) runCatching { listener.onRemoteChanges(changes) }
            }
            "req" -> respond(env)
        }
        val mine = log.stateVector()
        val lacking = env.sv.any { (d, s) -> d != self && s > (mine[d] ?: 0L) }
        if (lacking) requestSignal.trySend(false)
        if (env.k == "req" && env.sv.isEmpty() && mine.isEmpty()) {
            // iki taraf da boş: yapacak bir şey yok
        }
    }

    private suspend fun respond(env: Envelope) {
        val now = System.currentTimeMillis()
        val last = lastResponseAt[env.f] ?: 0L
        if (now - last < 30_000) return
        val missing = log.missingFor(env.sv)
        if (missing.isEmpty()) return
        lastResponseAt[env.f] = now
        if (publishOps(missing)) {
            val self = prefs.deviceId
            val ownMax = missing.filter { it.d == self }.maxOfOrNull { it.s }
            if (ownMax != null && ownMax > prefs.lastPublishedSeq) prefs.lastPublishedSeq = ownMax
            refreshPending()
        }
    }

    // ===================== yayınlama =====================

    suspend fun flush() = mutex.withLock {
        if (!prefs.paired || prefs.syncConfig == null) return@withLock
        while (true) {
            val ops = log.ownOpsAfter(prefs.lastPublishedSeq, MAX_OPS)
            if (ops.isEmpty()) break
            val ok = publishEnvelope(Envelope(k = "ops", f = prefs.deviceId, sv = log.stateVector(), ops = ops, t = System.currentTimeMillis()))
            if (!ok) break
            prefs.lastPublishedSeq = ops.last().s
            if (ops.size < MAX_OPS) break
        }
        refreshPending()
    }

    private suspend fun publishOps(ops: List<OpDto>): Boolean {
        val sv = log.stateVector()
        for (chunk in ops.chunked(MAX_OPS)) {
            val ok = publishEnvelope(Envelope(k = "ops", f = prefs.deviceId, sv = sv, ops = chunk, t = System.currentTimeMillis()))
            if (!ok) return false
        }
        return true
    }

    private suspend fun sendRequest(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastSyncRequestAt < 45_000) return
        if (prefs.syncConfig == null) return
        val ok = publishEnvelope(Envelope(k = "req", f = prefs.deviceId, sv = log.stateVector(), t = now, n = deviceName()))
        if (ok) prefs.lastSyncRequestAt = now
    }

    private suspend fun publishEnvelope(env: Envelope): Boolean {
        val cfg = prefs.syncConfig ?: return false
        val key = Crypto.keyFromString(cfg.keyB64) ?: return false
        val now = System.currentTimeMillis()
        if (now < prefs.quotaBlockedUntil) {
            setState(State.QUOTA, "Günlük mesaj kotası doldu")
            return false
        }
        val text = Crypto.seal(key, AppJson.encodeToString(Envelope.serializer(), env).encodeToByteArray())
        return when (val r = transport.publish(cfg, text)) {
            PublishResult.Ok -> {
                if (_status.value.state == State.QUOTA || _status.value.state == State.OFFLINE) {
                    setState(if (isConnected) connectedState() else State.IDLE)
                }
                true
            }
            is PublishResult.RateLimited -> {
                prefs.quotaBlockedUntil = now + if (r.dailyQuota) HOUR else 60_000L
                setState(State.QUOTA, if (r.dailyQuota) "Günlük mesaj kotası doldu" else "Çok sık gönderim, kısa süre bekleniyor")
                scope.launch {
                    delay(prefs.quotaBlockedUntil - now + 1_000)
                    flushSignal.trySend(Unit)
                }
                false
            }
            is PublishResult.Failed -> {
                if (r.code == -1) setState(State.OFFLINE, "Gönderilemedi, tekrar denenecek")
                scope.launch {
                    delay(30_000)
                    flushSignal.trySend(Unit)
                }
                false
            }
        }
    }

    // ===================== arka plan (WorkManager) =====================

    /** Akış açık değilken çağrılır: önbelleği çeker, bekleyenleri yollar. */
    suspend fun syncOnce(): Boolean {
        val cfg = prefs.syncConfig ?: return true
        val key = Crypto.keyFromString(cfg.keyB64) ?: return true
        if (isConnected) {
            flush()
            return true
        }
        val aliveBefore = prefs.lastAlive
        val events = try {
            transport.poll(cfg, sinceParam())
        } catch (e: Exception) {
            return false
        }
        mutex.withLock {
            for (ev in events) if (ev.event == "message") handleEvent(cfg, key, ev)
        }
        flush()
        maybeRequestAfterGap(aliveBefore)
        prefs.lastAlive = System.currentTimeMillis()
        // istek kanalı 2,5 sn bekler; işçi kapanmadan gönderilsin
        delay(3_500)
        return true
    }

    // ===================== yardımcılar =====================

    private suspend fun refreshPending() {
        val pending = if (prefs.syncConfig == null) 0L else (log.ownMaxSeq() - prefs.lastPublishedSeq).coerceAtLeast(0L)
        _status.update { it.copy(pendingOps = pending) }
    }

    private fun setState(s: State, detail: String? = null) {
        _status.update { it.copy(state = s, detail = detail) }
    }

    companion object {
        const val HOUR = 3_600_000L
        const val MAX_OPS = 1500
    }
}
