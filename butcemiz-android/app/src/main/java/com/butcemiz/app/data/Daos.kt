package com.butcemiz.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

interface BaseDao<T> {
    @Upsert
    suspend fun upsert(e: T)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(list: List<T>)
}

@Dao
interface MemberDao : BaseDao<Member> {
    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun get(id: String): Member?

    @Query("SELECT * FROM members WHERE deleted = 0 ORDER BY sortOrder, createdAt")
    fun observe(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE deleted = 0 ORDER BY sortOrder, createdAt")
    suspend fun list(): List<Member>

    @Query("SELECT * FROM members")
    suspend fun all(): List<Member>
}

@Dao
interface AccountDao : BaseDao<Account> {
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): Account?

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sortOrder, createdAt")
    fun observe(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sortOrder, createdAt")
    suspend fun list(): List<Account>

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<Account>
}

@Dao
interface CategoryDao : BaseDao<Category> {
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): Category?

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY kind, sortOrder, name")
    fun observe(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY kind, sortOrder, name")
    suspend fun list(): List<Category>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>
}

data class AccountFlowRow(
    val accountId: String?,
    val toAccountId: String?,
    val type: TxType,
    val total: Long,
)

data class SavingQtyRow(
    val savingId: String?,
    val type: TxType,
    val qty: Double,
    val total: Long,
)

data class LinkedTotalRow(
    val linkId: String?,
    val type: TxType,
    val total: Long,
)

data class TitleSuggestion(
    val title: String,
    val categoryId: String?,
    val uses: Int,
    val lastUsed: Long,
)

@Dao
interface TxnDao : BaseDao<Txn> {
    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun get(id: String): Txn?

    @Query("SELECT * FROM txns WHERE id = :id")
    fun observeOne(id: String): Flow<Txn?>

    @Query("SELECT accountId, toAccountId, type, SUM(amount) AS total FROM txns WHERE deleted = 0 GROUP BY accountId, toAccountId, type")
    fun observeAccountFlows(): Flow<List<AccountFlowRow>>

    @Query("SELECT accountId, toAccountId, type, SUM(amount) AS total FROM txns WHERE deleted = 0 GROUP BY accountId, toAccountId, type")
    suspend fun accountFlows(): List<AccountFlowRow>

    @Query("SELECT savingId, type, SUM(savingQty) AS qty, SUM(amount) AS total FROM txns WHERE deleted = 0 AND savingId IS NOT NULL GROUP BY savingId, type")
    fun observeSavingQty(): Flow<List<SavingQtyRow>>

    @Query("SELECT debtId AS linkId, type, SUM(amount) AS total FROM txns WHERE deleted = 0 AND debtId IS NOT NULL GROUP BY debtId, type")
    fun observeDebtTotals(): Flow<List<LinkedTotalRow>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC, createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC, createdAt DESC")
    suspend fun between(start: Long, end: Long): List<Txn>

    @Query("SELECT * FROM txns WHERE deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND (recurringId IS NOT NULL OR incomeSourceId IS NOT NULL) AND occurredAt >= :since")
    fun observeScheduledPayments(since: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND (recurringId IS NOT NULL OR incomeSourceId IS NOT NULL) AND occurredAt >= :since")
    suspend fun scheduledPayments(since: Long): List<Txn>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND savingId = :savingId ORDER BY occurredAt DESC, createdAt DESC")
    fun observeForSaving(savingId: String): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND debtId = :debtId ORDER BY occurredAt DESC, createdAt DESC")
    fun observeForDebt(debtId: String): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND (accountId = :accountId OR toAccountId = :accountId) ORDER BY occurredAt DESC, createdAt DESC LIMIT 200")
    fun observeForAccount(accountId: String): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND incomeSourceId = :sourceId ORDER BY occurredAt DESC LIMIT 24")
    fun observeForIncomeSource(sourceId: String): Flow<List<Txn>>

    @Query(
        "SELECT title, categoryId, COUNT(*) AS uses, MAX(createdAt) AS lastUsed FROM txns " +
            "WHERE deleted = 0 AND type = :type AND title != '' GROUP BY title COLLATE NOCASE ORDER BY lastUsed DESC LIMIT 80",
    )
    fun observeTitleSuggestions(type: TxType): Flow<List<TitleSuggestion>>

    @Query("SELECT COUNT(*) FROM txns WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(occurredAt) FROM txns WHERE deleted = 0")
    suspend fun firstDate(): Long?

    @Query("SELECT * FROM txns WHERE deleted = 0 ORDER BY occurredAt DESC, createdAt DESC")
    suspend fun listAll(): List<Txn>

    @Query("SELECT * FROM txns")
    suspend fun all(): List<Txn>

    @Query("SELECT COUNT(*) FROM txns WHERE deleted = 0 AND (categoryId = :categoryId)")
    suspend fun countForCategory(categoryId: String): Int

    @Query("SELECT * FROM txns WHERE deleted = 0 AND memberId = :memberId")
    suspend fun listForMember(memberId: String): List<Txn>

    @Query("SELECT * FROM txns WHERE deleted = 0 AND categoryId = :categoryId")
    suspend fun listForCategory(categoryId: String): List<Txn>
}

@Dao
interface SavingDao : BaseDao<Saving> {
    @Query("SELECT * FROM savings WHERE id = :id")
    suspend fun get(id: String): Saving?

    @Query("SELECT * FROM savings WHERE deleted = 0 ORDER BY archived, sortOrder, createdAt")
    fun observe(): Flow<List<Saving>>

    @Query("SELECT * FROM savings")
    suspend fun all(): List<Saving>
}

@Dao
interface DebtDao : BaseDao<Debt> {
    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: String): Debt?

    @Query("SELECT * FROM debts WHERE deleted = 0 ORDER BY closed, createdAt")
    fun observe(): Flow<List<Debt>>

    @Query("SELECT * FROM debts")
    suspend fun all(): List<Debt>
}

@Dao
interface RecurringDao : BaseDao<Recurring> {
    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: String): Recurring?

    @Query("SELECT * FROM recurring WHERE deleted = 0 ORDER BY active DESC, dayOfMonth, name")
    fun observe(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE deleted = 0 AND active = 1")
    suspend fun listActive(): List<Recurring>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<Recurring>
}

@Dao
interface IncomeSourceDao : BaseDao<IncomeSource> {
    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun get(id: String): IncomeSource?

    @Query("SELECT * FROM income_sources WHERE deleted = 0 ORDER BY active DESC, createdAt")
    fun observe(): Flow<List<IncomeSource>>

    @Query("SELECT * FROM income_sources WHERE deleted = 0 AND active = 1")
    suspend fun listActive(): List<IncomeSource>

    @Query("SELECT * FROM income_sources")
    suspend fun all(): List<IncomeSource>
}

@Dao
interface BudgetDao : BaseDao<Budget> {
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: String): Budget?

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun observe(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun list(): List<Budget>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<Budget>
}

@Dao
interface DeviceDao : BaseDao<Device> {
    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun get(id: String): Device?

    @Query("SELECT * FROM devices WHERE deleted = 0 ORDER BY addedAt")
    fun observe(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE deleted = 0")
    suspend fun list(): List<Device>

    @Query("SELECT * FROM devices")
    suspend fun all(): List<Device>
}

@Dao
interface HouseholdDao : BaseDao<Household> {
    @Query("SELECT * FROM household WHERE id = :id")
    suspend fun get(id: String): Household?

    @Query("SELECT * FROM household WHERE id = 'household'")
    fun observe(): Flow<Household?>

    @Query("SELECT * FROM household")
    suspend fun all(): List<Household>
}

@Dao
interface OpDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(op: OpRecord): Long

    @Query("SELECT * FROM ops WHERE deviceId = :deviceId AND seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun after(deviceId: String, afterSeq: Long, limit: Int): List<OpRecord>

    @Query("SELECT seq FROM ops WHERE deviceId = :deviceId AND seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun seqsAfter(deviceId: String, afterSeq: Long, limit: Int): List<Long>

    @Query("SELECT DISTINCT deviceId FROM ops")
    suspend fun devices(): List<String>

    @Query("SELECT MAX(seq) FROM ops WHERE deviceId = :deviceId")
    suspend fun maxSeq(deviceId: String): Long?

    @Query("SELECT MAX(hlc) FROM ops")
    suspend fun maxHlc(): Long?

    @Query("SELECT COUNT(*) FROM ops")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM ops WHERE deviceId = :deviceId AND seq > :afterSeq")
    fun observePendingCount(deviceId: String, afterSeq: Long): Flow<Int>
}

@Dao
interface VectorDao {
    @Upsert
    suspend fun upsert(e: VectorEntry)

    @Query("SELECT * FROM vector WHERE deviceId = :deviceId")
    suspend fun get(deviceId: String): VectorEntry?

    @Query("SELECT * FROM vector")
    suspend fun all(): List<VectorEntry>
}

@Dao
interface KvDao {
    @Upsert
    suspend fun put(e: KvEntry)

    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM kv WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("DELETE FROM kv WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Dao
interface PriceDao {
    @Upsert
    suspend fun upsertAll(list: List<PriceEntry>)

    @Query("SELECT * FROM prices")
    fun observe(): Flow<List<PriceEntry>>

    @Query("SELECT * FROM prices")
    suspend fun list(): List<PriceEntry>
}
