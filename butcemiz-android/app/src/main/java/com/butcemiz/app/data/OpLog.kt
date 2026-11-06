package com.butcemiz.app.data

import androidx.room.withTransaction
import com.butcemiz.app.core.HybridClock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

/** Ağ üzerinden taşınan işlem (kısa alan adları paket boyutunu küçültür). */
@Serializable
data class OpDto(
    val d: String, // cihaz
    val s: Long, // sıra
    val h: Long, // HLC damgası
    val e: String, // varlık türü
    val k: String, // varlık kimliği
    val p: JsonElement, // varlığın tam hali
)

/** Bir varlık türünün senkronizasyon tanımı. */
class EntityType<T : Synced>(
    val code: String,
    val serializer: KSerializer<T>,
    private val getFn: suspend (String) -> T?,
    private val upsertFn: suspend (T) -> Unit,
    private val restampFn: (T, Long, String) -> T,
) {
    suspend fun get(id: String): T? = getFn(id)
    suspend fun upsert(e: T) = upsertFn(e)
    fun restamp(e: T, hlc: Long, device: String): T = restampFn(e, hlc, device)
    fun encode(e: T): String = AppJson.encodeToString(serializer, e)
    fun decode(s: String): T = AppJson.decodeFromString(serializer, s)

    /** Uzaktan gelen sürümü, yereldekinden yeniyse uygular. */
    suspend fun applyRemote(payload: String, hlc: Long, device: String): AppliedChange? {
        val incoming = restamp(decode(payload), hlc, device)
        val existing = get(incoming.id)
        if (existing != null && !HybridClock.isNewer(hlc, device, existing.stamp, existing.stampDevice)) return null
        upsert(incoming)
        return AppliedChange(code, incoming.id, existing == null || existing.stampDevice == Seeds.SEED_DEVICE, existing, incoming, device)
    }
}

data class AppliedChange(
    val entity: String,
    val id: String,
    val isNew: Boolean,
    val before: Synced?,
    val after: Synced,
    val fromDevice: String,
)

/**
 * İşlem günlüğü. Yerel her değişiklik (cihaz, sıra) numarasıyla günlüğe yazılır ve aynı
 * veritabanı işlemi içinde tabloya uygulanır. Uzaktan gelen işlemler de buradan geçer.
 */
class OpLog(
    private val db: AppDatabase,
    private val deviceIdProvider: () -> String,
) {
    private val opDao = db.opDao()
    private val vectorDao = db.vectorDao()

    @Volatile
    private var clock: HybridClock? = null

    private suspend fun clock(): HybridClock {
        clock?.let { return it }
        val start = opDao.maxHlc() ?: 0L
        return HybridClock(start) { }.also { clock = it }
    }

    val members = EntityType("member", Member.serializer(), { db.memberDao().get(it) }, { db.memberDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val accounts = EntityType("account", Account.serializer(), { db.accountDao().get(it) }, { db.accountDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val categories = EntityType("category", Category.serializer(), { db.categoryDao().get(it) }, { db.categoryDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val txns = EntityType("txn", Txn.serializer(), { db.txnDao().get(it) }, { db.txnDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val savings = EntityType("saving", Saving.serializer(), { db.savingDao().get(it) }, { db.savingDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val debts = EntityType("debt", Debt.serializer(), { db.debtDao().get(it) }, { db.debtDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val recurring = EntityType("recurring", Recurring.serializer(), { db.recurringDao().get(it) }, { db.recurringDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val incomeSources = EntityType("income", IncomeSource.serializer(), { db.incomeSourceDao().get(it) }, { db.incomeSourceDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val budgets = EntityType("budget", Budget.serializer(), { db.budgetDao().get(it) }, { db.budgetDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val devices = EntityType("device", Device.serializer(), { db.deviceDao().get(it) }, { db.deviceDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }
    val households = EntityType("household", Household.serializer(), { db.householdDao().get(it) }, { db.householdDao().upsert(it) }) { e, h, d -> e.copy(stamp = h, stampDevice = d) }

    val types: Map<String, EntityType<*>> = listOf(
        members, accounts, categories, txns, savings, debts, recurring, incomeSources, budgets, devices, households,
    ).associateBy { it.code }

    /** Yerel değişikliği damgalar, günlüğe yazar ve uygular. */
    suspend fun <T : Synced> commit(type: EntityType<T>, entity: T): T = db.withTransaction {
        commitInTx(type, entity)
    }

    /** Birden fazla değişikliği tek veritabanı işleminde uygular. */
    suspend fun commitAll(block: suspend Batch.() -> Unit) {
        db.withTransaction { Batch().block() }
    }

    inner class Batch {
        suspend fun <T : Synced> put(type: EntityType<T>, entity: T): T = commitInTx(type, entity)
    }

    private suspend fun <T : Synced> commitInTx(type: EntityType<T>, entity: T): T {
        val device = deviceIdProvider()
        val hlc = clock().now()
        val stamped = type.restamp(entity, hlc, device)
        val seq = (maxSeq(device) ?: 0L) + 1L
        opDao.insertIgnore(
            OpRecord(device, seq, hlc, type.code, stamped.id, type.encode(stamped), System.currentTimeMillis()),
        )
        vectorDao.upsert(VectorEntry(device, seq, seq))
        type.upsert(stamped)
        return stamped
    }

    private suspend fun maxSeq(device: String): Long? = opDao.maxSeq(device)

    /** Uzaktan gelen işlemleri uygular. Yalnızca gerçekten yeni olanlar için değişiklik döner. */
    suspend fun applyRemote(ops: List<OpDto>): List<AppliedChange> = db.withTransaction {
        val self = deviceIdProvider()
        val changes = mutableListOf<AppliedChange>()
        val touched = mutableSetOf<String>()
        val clk = clock()
        for (op in ops.sortedWith(compareBy<OpDto>({ it.d }, { it.s }))) {
            if (op.d == self) continue
            val payload = op.p.toString()
            val row = opDao.insertIgnore(OpRecord(op.d, op.s, op.h, op.e, op.k, payload, System.currentTimeMillis()))
            if (row == -1L) continue
            touched += op.d
            clk.observe(op.h)
            val type = types[op.e] ?: continue // daha yeni sürümden gelen bilinmeyen tür: günlükte saklanır
            try {
                type.applyRemote(payload, op.h, op.d)?.let { changes += it }
            } catch (e: Exception) {
                // bozuk kayıt tüm senkronizasyonu durdurmasın
            }
        }
        for (d in touched) recomputeVector(d)
        changes
    }

    private suspend fun recomputeVector(device: String) {
        val cur = vectorDao.get(device) ?: VectorEntry(device, 0, 0)
        var c = cur.contiguous
        outer@ while (true) {
            val seqs = opDao.seqsAfter(device, c, 500)
            if (seqs.isEmpty()) break
            for (s in seqs) {
                if (s == c + 1) c = s else break@outer
            }
            if (seqs.size < 500) break
        }
        val maxSeen = maxOf(cur.maxSeen, maxSeq(device) ?: 0L, c)
        vectorDao.upsert(VectorEntry(device, c, maxSeen))
    }

    /** Bu cihazın bildiği durum vektörü: cihaz → kesintisiz son sıra */
    suspend fun stateVector(): Map<String, Long> = vectorDao.all().associate { it.deviceId to it.contiguous }

    /** Karşı tarafın vektörüne göre eksik olan işlemler. */
    suspend fun missingFor(remote: Map<String, Long>, limit: Int = 4000): List<OpDto> {
        val result = mutableListOf<OpDto>()
        for (v in vectorDao.all()) {
            val have = remote[v.deviceId] ?: 0L
            if (v.contiguous <= have) continue
            var after = have
            while (result.size < limit) {
                val chunk = opDao.after(v.deviceId, after, 500)
                if (chunk.isEmpty()) break
                for (r in chunk) {
                    if (r.seq > v.contiguous) break
                    result += r.toDto()
                    after = r.seq
                }
                if (chunk.size < 500 || after >= v.contiguous) break
            }
            if (result.size >= limit) break
        }
        return result
    }

    suspend fun ownOpsAfter(seq: Long, limit: Int = 4000): List<OpDto> =
        opDao.after(deviceIdProvider(), seq, limit).map { it.toDto() }

    suspend fun ownMaxSeq(): Long = maxSeq(deviceIdProvider()) ?: 0L

    private fun OpRecord.toDto(): OpDto = OpDto(deviceId, seq, hlc, entity, entityId, AppJson.parseToJsonElement(payload))
}
