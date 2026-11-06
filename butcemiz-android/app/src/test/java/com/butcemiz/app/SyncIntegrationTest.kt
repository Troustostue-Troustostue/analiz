package com.butcemiz.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.butcemiz.app.data.AppDatabase
import com.butcemiz.app.data.AppliedChange
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.OpLog
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Repository
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.prefs.AppPrefs
import com.butcemiz.app.prefs.SyncConfig
import com.butcemiz.app.sync.NtfyEvent
import com.butcemiz.app.sync.PairingCode
import com.butcemiz.app.sync.PublishResult
import com.butcemiz.app.sync.RelayTransport
import com.butcemiz.app.sync.SyncEngine
import com.butcemiz.app.sync.SyncListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** ntfy'ı taklit eden bellek içi aktarıcı (önbellek + canlı akış). */
class FakeRelay : RelayTransport {
    data class Msg(val id: String, val time: Long, val body: String)

    private val lock = Any()
    private val messages = mutableListOf<Msg>()
    private val subscribers = mutableListOf<Channel<NtfyEvent>>()
    private val seq = AtomicInteger(0)
    val publishCount = AtomicInteger(0)
    val bytes = AtomicInteger(0)

    private fun Msg.event() = NtfyEvent(id, time, "message", body, null)

    override suspend fun publish(cfg: SyncConfig, body: String): PublishResult {
        val m = Msg("m" + seq.incrementAndGet(), System.currentTimeMillis() / 1000, body)
        synchronized(lock) {
            messages += m
            subscribers.forEach { it.trySend(m.event()) }
        }
        publishCount.incrementAndGet()
        bytes.addAndGet(body.length)
        return PublishResult.Ok
    }

    private fun cached(since: String): List<Msg> = synchronized(lock) {
        if (since == "all") messages.toList() else messages.filter { it.time >= since.toLong() }
    }

    override suspend fun poll(cfg: SyncConfig, since: String): List<NtfyEvent> = cached(since).map { it.event() }

    override fun stream(cfg: SyncConfig, since: String): Flow<NtfyEvent> = channelFlow {
        val ch = Channel<NtfyEvent>(Channel.UNLIMITED)
        val backlog: List<Msg>
        synchronized(lock) {
            backlog = if (since == "all") messages.toList() else messages.filter { it.time >= since.toLong() }
            subscribers += ch
        }
        try {
            send(NtfyEvent("open" + seq.incrementAndGet(), System.currentTimeMillis() / 1000, "open"))
            backlog.forEach { send(it.event()) }
            for (e in ch) send(e)
        } finally {
            synchronized(lock) { subscribers -= ch }
        }
    }

    override suspend fun download(cfg: SyncConfig, url: String): String? = null

    /** 12 saatlik önbelleğin dolduğunu taklit eder */
    fun dropCache() = synchronized(lock) { messages.clear() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncIntegrationTest {

    private val relay = FakeRelay()
    private val phones = mutableListOf<Phone>()

    inner class Phone(val name: String) {
        private val ctx: Context = ApplicationProvider.getApplicationContext()
        val prefs = AppPrefs(ctx, "prefs_$name").also { it.wipe() }
        val db: AppDatabase = AppDatabase.inMemory(ctx)
        val log = OpLog(db) { prefs.deviceId }
        val changes = CopyOnWriteArrayList<AppliedChange>()
        val joined = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = SyncEngine(
            prefs, log, relay,
            object : SyncListener {
                override suspend fun onRemoteChanges(changes: List<AppliedChange>) {
                    this@Phone.changes += changes
                }

                override suspend fun onPeerJoined(deviceName: String?) {
                    joined.incrementAndGet()
                }
            },
            scope, { name }, releaseDelayMs = 100, requestCooldownMs = 2_000,
        )
        val repo = Repository(db, log, prefs, onLocalChange = { engine.onLocalChange() }, deviceInfo = { name to "test" })

        suspend fun total(): Long = Calc.total(Calc.balances(db.accountDao().list(), db.txnDao().accountFlows()))
        suspend fun txnCount(): Int = db.txnDao().listAll().size

        init {
            phones += this
        }
    }

    @After
    fun tearDown() {
        phones.forEach {
            it.scope.cancel()
            it.db.close()
        }
    }

    private suspend fun waitFor(what: String, timeoutMs: Long = 25_000, cond: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond()) {
            if (System.currentTimeMillis() - start > timeoutMs) fail("Zaman aşımı: $what")
            delay(100)
        }
    }

    private fun expense(amount: Long, title: String, member: String?) =
        Txn(id = "", type = TxType.EXPENSE, amount = amount, title = title, categoryId = Seeds.CAT_MARKET, accountId = Seeds.MAIN_ACCOUNT_ID, memberId = member, occurredAt = System.currentTimeMillis())

    @Test
    fun twoPhonesStayInSync() = runBlocking {
        val cfg = PairingCode.newConfig()

        // --- Telefon A: bütçeyi oluşturur ---
        val a = Phone("S23FE")
        a.prefs.syncConfig = cfg
        a.repo.createHousehold("Evimiz", "Alperen", "Ayşe", 120_000_00, 45_000_00, 15, 1L to 2L)
        a.engine.acquire("t")
        assertEquals(120_000_00L, a.total())
        delay(1_500)
        assertEquals("Eşleşmeden önce mesaj gönderilmemeli", 0, relay.publishCount.get())

        // --- Telefon B: davet koduyla katılır ---
        val b = Phone("S24FE")
        b.repo.prepareJoin()
        b.prefs.syncConfig = PairingCode.decode(PairingCode.encode(cfg))
        b.prefs.awaitingInitialSync = true
        b.engine.acquire("t")
        b.engine.requestNow(force = true)

        waitFor("B, A'nın verilerini almalı") { b.db.memberDao().list().size == 2 && b.total() == 120_000_00L }
        assertTrue(a.prefs.paired && b.prefs.paired)
        assertEquals(1, a.joined.get())
        val ayse = b.db.memberDao().list().first { it.name == "Ayşe" }
        b.repo.chooseMember(ayse.id)
        waitFor("A, B'nin cihazını görmeli") { a.db.deviceDao().list().size == 2 }
        assertEquals("Maaş kaynağı da gelmeli", 1, b.db.incomeSourceDao().listActive().size)

        // --- A harcama yapar → B anında görür ---
        val alperen = a.prefs.myMemberId
        a.repo.saveTxn(expense(2_000_00, "Migros", alperen))
        waitFor("B yeni bakiyeyi görmeli") { b.total() == 118_000_00L }
        val notice = b.changes.firstOrNull { it.entity == "txn" && it.isNew && (it.after as Txn).title == "Migros" }
        assertTrue("B'de bildirim için yeni hareket olayı oluşmalı", notice != null)

        // --- B gelir ekler → A görür ---
        b.repo.saveTxn(Txn(id = "", type = TxType.INCOME, amount = 500_00, title = "Ek gelir", categoryId = Seeds.CAT_EXTRA_INCOME, accountId = Seeds.MAIN_ACCOUNT_ID, memberId = ayse.id, occurredAt = System.currentTimeMillis()))
        waitFor("A yeni bakiyeyi görmeli") { a.total() == 118_500_00L }

        // --- Aynı anda aynı kategoriyi düzenleme → iki telefon aynı sonuca varır ---
        coroutineScope {
            launch { a.repo.saveCategory(a.db.categoryDao().get(Seeds.CAT_MARKET)!!.copy(name = "Market (A)")) }
            launch { b.repo.saveCategory(b.db.categoryDao().get(Seeds.CAT_MARKET)!!.copy(name = "Market (B)")) }
        }
        waitFor("kategori adı iki tarafta eşitlenmeli") {
            val x = a.db.categoryDao().get(Seeds.CAT_MARKET)!!.name
            val y = b.db.categoryDao().get(Seeds.CAT_MARKET)!!.name
            x == y && x.startsWith("Market (")
        }

        // --- Aynı faturayı iki telefondan aynı anda "Öde" → tek kayıt ---
        val r = a.repo.saveRecurring(Recurring(id = "", name = "Elektrik", amount = 850_00, categoryId = Seeds.CAT_BILLS, dayOfMonth = 3))
        waitFor("fatura B'ye gelmeli") { b.db.recurringDao().get(r.id) != null }
        coroutineScope {
            launch { a.repo.payRecurring(r, "2026-11", 850_00, Seeds.MAIN_ACCOUNT_ID, alperen, System.currentTimeMillis()) }
            launch { b.repo.payRecurring(b.db.recurringDao().get(r.id)!!, "2026-11", 850_00, Seeds.MAIN_ACCOUNT_ID, ayse.id, System.currentTimeMillis()) }
        }
        waitFor("fatura bir kez düşülmeli") { a.total() == 117_650_00L && b.total() == 117_650_00L }

        // --- B kısa süre çevrimdışı: A'nın girdileri önbellekten gelir ---
        b.engine.release("t")
        delay(800)
        assertTrue(!b.engine.isConnected)
        repeat(3) { i -> a.repo.saveTxn(expense(100_00, "Fırın $i", alperen)) }
        delay(1_500)
        b.engine.acquire("t")
        waitFor("B önbellekten yetişmeli") { b.total() == 117_350_00L }

        // --- B 12 saatten uzun kopuk: önbellek boş, "req" ile tamamlanır ---
        b.engine.release("t")
        delay(800)
        a.repo.saveTxn(expense(1_000_00, "Eczane", alperen))
        a.repo.saveTxn(expense(250_00, "Otopark", alperen))
        delay(1_500)
        relay.dropCache()
        b.prefs.lastAlive = System.currentTimeMillis() - 13 * 3_600_000L
        b.engine.acquire("t")
        waitFor("B istek göndererek eksikleri almalı", 30_000) { b.total() == 116_100_00L }

        // --- Silme de eşitlenir ---
        val eczane = a.db.txnDao().listAll().first { it.title == "Eczane" }
        a.repo.deleteTxn(eczane)
        waitFor("silme B'ye ulaşmalı") { b.total() == 117_100_00L }

        // --- Son durum: iki telefon birebir aynı ---
        delay(1_500)
        assertEquals(a.total(), b.total())
        assertEquals(a.txnCount(), b.txnCount())
        assertEquals(
            a.db.txnDao().listAll().map { it.id to it.amount }.toSet(),
            b.db.txnDao().listAll().map { it.id to it.amount }.toSet(),
        )
        println("Toplam yayınlanan mesaj: ${relay.publishCount.get()}, toplam boyut: ${relay.bytes.get()} bayt")
        assertTrue("Mesaj sayısı kota dostu olmalı (${relay.publishCount.get()})", relay.publishCount.get() <= 30)
    }

    @Test
    fun opsAppliedInAnyOrderConverge() = runBlocking {
        val a = Phone("A")
        val b = Phone("B")
        val c = Phone("C")
        a.repo.prepareJoin()
        b.repo.prepareJoin()
        c.repo.prepareJoin()
        val t = a.repo.saveTxn(expense(1_000_00, "İlk", null))
        a.repo.saveTxn(t.copy(amount = 2_000_00, title = "Düzeltilmiş"))
        a.repo.saveCategory(a.db.categoryDao().get(Seeds.CAT_FOOD)!!.copy(name = "Restoran"))
        a.repo.deleteTxn(a.db.txnDao().get(t.id)!!)
        val ops = a.log.missingFor(emptyMap())
        assertEquals(4, ops.size)
        b.log.applyRemote(ops)
        ops.reversed().forEach { c.log.applyRemote(listOf(it)) } // ters sırada, tek tek
        for (p in listOf(b, c)) {
            assertEquals(0L, p.total())
            assertEquals("Restoran", p.db.categoryDao().get(Seeds.CAT_FOOD)!!.name)
            assertTrue(p.db.txnDao().get(t.id)!!.deleted)
            assertEquals(4L, p.log.stateVector()[a.prefs.deviceId])
        }
        // tekrar uygulamak etkisizdir
        assertEquals(0, b.log.applyRemote(ops).size)
    }
}
