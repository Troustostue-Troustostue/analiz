package com.butcemiz.app.data

import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Ids
import com.butcemiz.app.core.Period
import com.butcemiz.app.prefs.AppPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Kullanıcı arayüzünün tek veri kapısı: okumalar akış (Flow), yazmalar işlem günlüğü üzerinden. */
class Repository(
    val db: AppDatabase,
    val log: OpLog,
    val prefs: AppPrefs,
    private val onLocalChange: () -> Unit,
    private val deviceInfo: () -> Pair<String, String> = { "Telefon" to "" },
    private val appVersion: String = "",
) {
    private val txnDao = db.txnDao()

    // ======================= Akışlar =======================

    val members: Flow<List<Member>> = db.memberDao().observe()
    val accounts: Flow<List<Account>> = db.accountDao().observe()
    val categories: Flow<List<Category>> = db.categoryDao().observe()
    val household: Flow<Household> = db.householdDao().observe().map { it ?: Household() }.distinctUntilChanged()
    val monthStartDay: Flow<Int> = household.map { it.monthStartDay }.distinctUntilChanged()
    val devices: Flow<List<Device>> = db.deviceDao().observe()
    val recurring: Flow<List<Recurring>> = db.recurringDao().observe()
    val incomeSources: Flow<List<IncomeSource>> = db.incomeSourceDao().observe()
    val budgets: Flow<List<Budget>> = db.budgetDao().observe()
    val prices: Flow<Map<String, PriceEntry>> = db.priceDao().observe().map { l -> l.associateBy { it.key } }
    val txnCount: Flow<Int> = txnDao.observeCount()

    val accountBalances: Flow<List<AccountBalance>> =
        combine(accounts, txnDao.observeAccountFlows()) { accs, flows -> Calc.balances(accs, flows) }

    val totalBalance: Flow<Long> = accountBalances.map { Calc.total(it) }.distinctUntilChanged()

    val savings: Flow<List<SavingView>> =
        combine(db.savingDao().observe(), txnDao.observeSavingQty(), prices) { s, rows, p -> Calc.savingViews(s, rows, p) }

    val debts: Flow<List<DebtView>> =
        combine(db.debtDao().observe(), txnDao.observeDebtTotals()) { d, rows -> Calc.debtViews(d, rows) }

    fun txnsBetween(p: Period): Flow<List<Txn>> = txnDao.observeBetween(p.startMillis, p.endMillis)
    fun txnsBetween(start: Long, end: Long): Flow<List<Txn>> = txnDao.observeBetween(start, end)
    fun recentTxns(limit: Int): Flow<List<Txn>> = txnDao.observeRecent(limit)
    fun scheduledPayments(): Flow<List<Txn>> =
        txnDao.observeScheduledPayments(Dates.startOfDay(Dates.today().minusDays(800)))
    fun txn(id: String): Flow<Txn?> = txnDao.observeOne(id)
    fun txnsForSaving(id: String): Flow<List<Txn>> = txnDao.observeForSaving(id)
    fun txnsForDebt(id: String): Flow<List<Txn>> = txnDao.observeForDebt(id)
    fun txnsForAccount(id: String): Flow<List<Txn>> = txnDao.observeForAccount(id)
    fun txnsForIncomeSource(id: String): Flow<List<Txn>> = txnDao.observeForIncomeSource(id)
    fun titleSuggestions(type: TxType): Flow<List<TitleSuggestion>> = txnDao.observeTitleSuggestions(type)

    suspend fun getTxn(id: String): Txn? = txnDao.get(id)

    // ======================= Yazma işlemleri =======================

    private fun now() = System.currentTimeMillis()

    private suspend fun <T : Synced> commit(type: EntityType<T>, e: T): T =
        log.commit(type, e).also { onLocalChange() }

    // ---- hareketler ----
    suspend fun saveTxn(t: Txn): Txn {
        val base = if (t.id.isBlank()) t.copy(id = Ids.newId()) else t
        val withMeta = if (base.createdAt == 0L) base.copy(createdAt = now(), createdBy = prefs.deviceId) else base
        return commit(log.txns, withMeta)
    }

    suspend fun deleteTxn(t: Txn) {
        commit(log.txns, t.copy(deleted = true))
    }

    suspend fun restoreTxn(t: Txn) {
        commit(log.txns, t.copy(deleted = false))
    }

    suspend fun adjustBalance(accountId: String, currentBalance: Long, targetBalance: Long, note: String = "") {
        val diff = targetBalance - currentBalance
        if (diff == 0L) return
        saveTxn(
            Txn(
                id = "", type = TxType.ADJUSTMENT, amount = diff, title = "Bakiye düzeltme", accountId = accountId,
                memberId = prefs.myMemberId, occurredAt = now(), note = note,
            ),
        )
    }

    // ---- üyeler / cihazlar / ev ----
    suspend fun saveMember(m: Member): Member =
        commit(log.members, if (m.id.isBlank()) m.copy(id = Ids.newId(), createdAt = now()) else m)

    suspend fun deleteMember(m: Member, reassignTo: String?) {
        log.commitAll {
            for (t in txnDao.listForMember(m.id)) put(log.txns, t.copy(memberId = reassignTo))
            put(log.members, m.copy(deleted = true))
        }
        if (prefs.myMemberId == m.id) prefs.myMemberId = reassignTo
        onLocalChange()
    }

    suspend fun saveHousehold(h: Household) {
        commit(log.households, h)
    }

    suspend fun registerThisDevice(memberId: String?) {
        val (name, model) = deviceInfo()
        commit(log.devices, Device(prefs.deviceId, name, model, memberId, now(), appVersion))
    }

    // ---- hesaplar ----
    suspend fun saveAccount(a: Account): Account =
        commit(log.accounts, if (a.id.isBlank()) a.copy(id = Ids.newId(), createdAt = now()) else a)

    /** Hareketi varsa arşivler, yoksa siler. */
    suspend fun removeAccount(a: Account, hasTxns: Boolean) {
        commit(log.accounts, if (hasTxns) a.copy(archived = true) else a.copy(deleted = true))
    }

    // ---- kategoriler ----
    suspend fun saveCategory(c: Category): Category =
        commit(log.categories, if (c.id.isBlank()) c.copy(id = Ids.newId()) else c)

    suspend fun removeCategory(c: Category) {
        val used = txnDao.countForCategory(c.id) > 0
        commit(log.categories, if (used) c.copy(archived = true) else c.copy(deleted = true))
    }

    // ---- birikimler ----
    suspend fun saveSaving(s: Saving): Saving =
        commit(log.savings, if (s.id.isBlank()) s.copy(id = Ids.newId(), createdAt = now()) else s)

    suspend fun deleteSaving(s: Saving) {
        commit(log.savings, s.copy(deleted = true))
    }

    suspend fun savingMovement(
        saving: Saving,
        deposit: Boolean,
        qty: Double,
        amount: Long,
        accountId: String?,
        memberId: String?,
        date: Long,
        note: String,
    ): Txn = saveTxn(
        Txn(
            id = "",
            type = if (deposit) TxType.SAVING_DEPOSIT else TxType.SAVING_WITHDRAW,
            amount = amount,
            title = saving.name,
            accountId = accountId,
            memberId = memberId,
            occurredAt = date,
            note = note,
            savingId = saving.id,
            savingQty = qty,
        ),
    )

    // ---- borçlar ----
    suspend fun saveDebt(d: Debt, moneyAccountId: String? = null): Debt {
        val isNew = d.id.isBlank()
        val saved = commit(log.debts, if (isNew) d.copy(id = Ids.newId(), createdAt = now()) else d)
        if (isNew && moneyAccountId != null && saved.principal > 0) {
            saveTxn(
                Txn(
                    id = "",
                    type = if (saved.direction == DebtDirection.I_OWE) TxType.LOAN_RECEIVED else TxType.LOAN_GIVEN,
                    amount = saved.principal - saved.alreadyPaid,
                    title = saved.name,
                    accountId = moneyAccountId,
                    memberId = prefs.myMemberId,
                    occurredAt = saved.startDate ?: now(),
                    debtId = saved.id,
                ),
            )
        }
        return saved
    }

    suspend fun deleteDebt(d: Debt) {
        commit(log.debts, d.copy(deleted = true))
    }

    suspend fun payDebt(d: Debt, amount: Long, accountId: String?, memberId: String?, date: Long, note: String): Txn =
        saveTxn(
            Txn(
                id = "",
                type = if (d.direction == DebtDirection.I_OWE) TxType.EXPENSE else TxType.INCOME,
                amount = amount,
                title = d.name,
                categoryId = if (d.direction == DebtDirection.I_OWE) Seeds.CAT_DEBT else Seeds.CAT_RECEIVABLE,
                accountId = accountId,
                memberId = memberId,
                occurredAt = date,
                note = note,
                debtId = d.id,
            ),
        )

    // ---- düzenli ödemeler ----
    suspend fun saveRecurring(r: Recurring): Recurring =
        commit(log.recurring, if (r.id.isBlank()) r.copy(id = Ids.newId(), createdAt = now()) else r)

    suspend fun deleteRecurring(r: Recurring) {
        commit(log.recurring, r.copy(deleted = true))
    }

    /** Aynı dönem için iki telefondan "Öde"ye basılsa bile tek kayıt oluşur (sabit kimlik). */
    suspend fun payRecurring(r: Recurring, periodKey: String, amount: Long, accountId: String?, memberId: String?, date: Long): Txn {
        val id = "rp_${r.id}_$periodKey"
        val existing = txnDao.get(id)
        return saveTxn(
            Txn(
                id = id,
                type = TxType.EXPENSE,
                amount = amount,
                title = r.name,
                categoryId = r.categoryId ?: Seeds.CAT_BILLS,
                accountId = accountId,
                memberId = memberId,
                occurredAt = date,
                debtId = r.debtId,
                recurringId = r.id,
                period = periodKey,
                createdAt = existing?.createdAt ?: 0L,
                createdBy = existing?.createdBy ?: "",
            ),
        )
    }

    // ---- gelir kaynakları (maaş) ----
    suspend fun saveIncomeSource(s: IncomeSource): IncomeSource {
        val isNew = s.id.isBlank()
        val old = if (isNew) null else db.incomeSourceDao().get(s.id)
        val amountChanged = old == null || old.amount != s.amount
        val base = if (isNew) s.copy(id = Ids.newId(), createdAt = now()) else s
        return commit(log.incomeSources, if (amountChanged) base.copy(amountUpdatedAt = now()) else base)
    }

    suspend fun deleteIncomeSource(s: IncomeSource) {
        commit(log.incomeSources, s.copy(deleted = true))
    }

    suspend fun recordIncome(src: IncomeSource, periodKey: String, amount: Long, accountId: String?, date: Long): Txn {
        val id = "inc_${src.id}_$periodKey"
        val existing = txnDao.get(id)
        return saveTxn(
            Txn(
                id = id,
                type = TxType.INCOME,
                amount = amount,
                title = src.name,
                categoryId = src.categoryId ?: Seeds.incomeCategoryFor(src.kind),
                accountId = accountId ?: src.accountId ?: Seeds.MAIN_ACCOUNT_ID,
                memberId = src.memberId,
                occurredAt = date,
                incomeSourceId = src.id,
                period = periodKey,
                createdAt = existing?.createdAt ?: 0L,
                createdBy = existing?.createdBy ?: "",
            ),
        )
    }

    // ---- bütçeler ----
    suspend fun setBudget(categoryId: String?, amount: Long) {
        val id = "budget_" + (categoryId ?: "total")
        val existing = db.budgetDao().get(id)
        commit(log.budgets, Budget(id, categoryId, amount, existing?.createdAt ?: now(), deleted = amount <= 0))
    }

    // ======================= Kurulum =======================

    /** "Yeni bütçe oluştur" */
    suspend fun createHousehold(
        householdName: String,
        myName: String,
        partnerName: String?,
        startBalance: Long?,
        salary: Long?,
        payDay: Int,
        memberColors: Pair<Long, Long>,
    ) {
        Seeds.apply(db)
        var myId = ""
        val (devName, devModel) = deviceInfo()
        log.commitAll {
            val me = put(log.members, Member(Ids.newId(), myName.trim(), color = memberColors.first, sortOrder = 0, createdAt = now()))
            myId = me.id
            if (!partnerName.isNullOrBlank()) {
                put(log.members, Member(Ids.newId(), partnerName.trim(), color = memberColors.second, sortOrder = 1, createdAt = now()))
            }
            put(log.households, Household(name = householdName.ifBlank { "Bütçemiz" }, createdAt = now()))
            put(log.devices, Device(prefs.deviceId, devName, devModel, me.id, now(), appVersion))
            if (startBalance != null && startBalance != 0L) {
                put(log.accounts, Seeds.mainAccount.copy(openingBalance = startBalance, createdAt = now()))
            }
            if (salary != null && salary > 0) {
                put(
                    log.incomeSources,
                    IncomeSource(
                        Ids.newId(), "Maaş", IncomeKind.SALARY, me.id, salary, payDay.coerceIn(1, 31),
                        Seeds.MAIN_ACCOUNT_ID, Seeds.CAT_SALARY, createdAt = now(), amountUpdatedAt = now(),
                    ),
                )
            }
        }
        prefs.myMemberId = myId
        prefs.onboarded = true
        onLocalChange()
    }

    /** "Eşimin bütçesine katıl" — varsayılanlar hazırlanır, veriler eşten gelir. */
    suspend fun prepareJoin() {
        Seeds.apply(db)
    }

    suspend fun chooseMember(memberId: String) {
        prefs.myMemberId = memberId
        registerThisDevice(memberId)
    }

    suspend fun createMemberAndChoose(name: String, color: Long) {
        val m = saveMember(Member("", name.trim(), color = color, sortOrder = 5))
        chooseMember(m.id)
    }
}
