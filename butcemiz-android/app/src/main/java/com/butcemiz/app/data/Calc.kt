package com.butcemiz.app.data

import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.core.Period
import com.butcemiz.app.core.PeriodKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.max
import kotlin.math.roundToLong

data class AccountBalance(val account: Account, val balance: Long)

data class SavingView(
    val saving: Saving,
    val quantity: Double,
    /** birim fiyat (kuruş), bilinmiyorsa null */
    val unitPrice: Long?,
    val value: Long,
    val priceUpdatedAt: Long?,
    val autoPriced: Boolean,
    /** bakiyeden aktarılan net tutar */
    val netInvested: Long,
)

data class DebtView(
    val debt: Debt,
    val paid: Long,
    val remaining: Long,
    val progress: Float,
    val nextDue: LocalDate?,
)

data class CategoryTotal(val categoryId: String?, val total: Long, val count: Int)

data class PeriodStats(
    val period: Period,
    val income: Long,
    val expense: Long,
    val savingsNet: Long,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val expenseByMember: Map<String?, Long>,
    val incomeByMember: Map<String?, Long>,
    /** WEEK/MONTH: gün gün; YEAR: ay ay */
    val expenseBuckets: LongArray,
    val incomeBuckets: LongArray,
    val count: Int,
    val largestExpenses: List<Txn>,
) {
    val net: Long get() = income - expense
    val dailyAverage: Long
        get() {
            val today = Dates.today()
            val elapsed = when {
                today.isBefore(period.start) -> 1
                today.isBefore(period.endExclusive) -> ChronoUnit.DAYS.between(period.start, today).toInt() + 1
                else -> period.days
            }
            return if (elapsed <= 0) expense else expense / elapsed
        }
}

data class Occurrence(
    val recurring: Recurring,
    val due: LocalDate,
    val periodKey: String,
    val paidTxn: Txn?,
) {
    val isPaid: Boolean get() = paidTxn != null
}

data class PaydayStatus(
    val source: IncomeSource,
    val due: LocalDate,
    val periodKey: String,
    val received: Txn?,
)

data class BudgetView(val budget: Budget, val spent: Long) {
    val progress: Float get() = Money.percent(spent, budget.amount)
    val remaining: Long get() = budget.amount - spent
}

object Calc {

    // ---------------- Bakiyeler ----------------

    fun balances(accounts: List<Account>, flows: List<AccountFlowRow>): List<AccountBalance> {
        val delta = HashMap<String, Long>()
        fun add(id: String?, v: Long) {
            if (id != null) delta[id] = (delta[id] ?: 0L) + v
        }
        for (r in flows) {
            when (r.type) {
                TxType.EXPENSE, TxType.SAVING_DEPOSIT, TxType.LOAN_GIVEN -> add(r.accountId, -r.total)
                TxType.INCOME, TxType.SAVING_WITHDRAW, TxType.LOAN_RECEIVED, TxType.ADJUSTMENT -> add(r.accountId, r.total)
                TxType.TRANSFER -> {
                    add(r.accountId, -r.total)
                    add(r.toAccountId, r.total)
                }
            }
        }
        return accounts.map { AccountBalance(it, it.openingBalance + (delta[it.id] ?: 0L)) }
    }

    fun includedInTotal(a: Account): Boolean = a.includeInTotal && !a.archived

    fun total(balances: List<AccountBalance>): Long =
        balances.filter { includedInTotal(it.account) }.sumOf { it.balance }

    /** İşlemin toplam bakiyeye etkisi */
    fun effectOnTotal(t: Txn, included: Set<String>): Long {
        fun inc(id: String?) = id != null && id in included
        return when (t.type) {
            TxType.EXPENSE, TxType.SAVING_DEPOSIT, TxType.LOAN_GIVEN -> if (inc(t.accountId)) -t.amount else 0L
            TxType.INCOME, TxType.SAVING_WITHDRAW, TxType.LOAN_RECEIVED, TxType.ADJUSTMENT -> if (inc(t.accountId)) t.amount else 0L
            TxType.TRANSFER -> (if (inc(t.accountId)) -t.amount else 0L) + (if (inc(t.toAccountId)) t.amount else 0L)
        }
    }

    /** Akış (en yeni önce) için her kaydın ardından oluşan toplam bakiye. */
    fun runningBalances(recentNewestFirst: List<Txn>, currentTotal: Long, included: Set<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        var running = currentTotal
        for (t in recentNewestFirst) {
            result[t.id] = running
            running -= effectOnTotal(t, included)
        }
        return result
    }

    // ---------------- Birikimler ----------------

    fun savingQuantity(s: Saving, rows: List<SavingQtyRow>): Double =
        s.initialQty + rows.filter { it.savingId == s.id }.sumOf {
            when (it.type) {
                TxType.SAVING_DEPOSIT -> it.qty
                TxType.SAVING_WITHDRAW -> -it.qty
                else -> 0.0
            }
        }

    fun savingNetInvested(s: Saving, rows: List<SavingQtyRow>): Long =
        rows.filter { it.savingId == s.id }.sumOf {
            when (it.type) {
                TxType.SAVING_DEPOSIT -> it.total
                TxType.SAVING_WITHDRAW -> -it.total
                else -> 0L
            }
        }

    fun unitPrice(s: Saving, prices: Map<String, PriceEntry>): Pair<Long?, PriceEntry?> {
        if (s.type.unitIsLira) return 100L to null
        if (s.manualUnitPrice != null) return s.manualUnitPrice to null
        val key = s.type.priceKey ?: return null to null
        val p = prices[key] ?: return null to null
        val v = if (p.buying > 0) p.buying else p.selling
        return Money.fromLira(v) to p
    }

    fun savingViews(savings: List<Saving>, rows: List<SavingQtyRow>, prices: Map<String, PriceEntry>): List<SavingView> =
        savings.map { s ->
            val qty = savingQuantity(s, rows)
            val (unit, priceEntry) = unitPrice(s, prices)
            val value = if (unit == null) 0L else (qty * unit).roundToLong()
            SavingView(s, qty, unit, value, priceEntry?.updatedAt, priceEntry != null, savingNetInvested(s, rows))
        }

    // ---------------- Borçlar ----------------

    fun debtPaid(d: Debt, rows: List<LinkedTotalRow>): Long =
        d.alreadyPaid + rows.filter { it.linkId == d.id }.sumOf { r ->
            when {
                d.direction == DebtDirection.I_OWE && r.type == TxType.EXPENSE -> r.total
                d.direction == DebtDirection.OWED_TO_ME && r.type == TxType.INCOME -> r.total
                else -> 0L
            }
        }

    fun debtViews(debts: List<Debt>, rows: List<LinkedTotalRow>, today: LocalDate = Dates.today()): List<DebtView> =
        debts.map { d ->
            val paid = debtPaid(d, rows)
            val remaining = max(0L, d.principal - paid)
            val next = if (d.closed || remaining == 0L) null else d.dueDay?.let { Dates.nextMonthlyDate(it, today) }
            DebtView(d, paid, remaining, Money.percent(paid, d.principal).coerceIn(0f, 1f), next)
        }

    // ---------------- Dönem istatistikleri ----------------

    fun periodStats(txns: List<Txn>, p: Period): PeriodStats {
        var income = 0L
        var expense = 0L
        var savingsNet = 0L
        val expCat = LinkedHashMap<String?, LongArray>()
        val incCat = LinkedHashMap<String?, LongArray>()
        val expMember = HashMap<String?, Long>()
        val incMember = HashMap<String?, Long>()
        val bucketCount = if (p.kind == PeriodKind.YEAR) 12 else p.days
        val expBuckets = LongArray(bucketCount)
        val incBuckets = LongArray(bucketCount)
        var count = 0
        for (t in txns) {
            if (!p.contains(t.occurredAt)) continue
            count++
            val idx = bucketIndex(p, t.occurredAt).coerceIn(0, bucketCount - 1)
            when (t.type) {
                TxType.EXPENSE -> {
                    expense += t.amount
                    expCat.getOrPut(t.categoryId) { LongArray(2) }.let { it[0] += t.amount; it[1]++ }
                    expMember[t.memberId] = (expMember[t.memberId] ?: 0L) + t.amount
                    expBuckets[idx] += t.amount
                }
                TxType.INCOME -> {
                    income += t.amount
                    incCat.getOrPut(t.categoryId) { LongArray(2) }.let { it[0] += t.amount; it[1]++ }
                    incMember[t.memberId] = (incMember[t.memberId] ?: 0L) + t.amount
                    incBuckets[idx] += t.amount
                }
                TxType.SAVING_DEPOSIT -> savingsNet += t.amount
                TxType.SAVING_WITHDRAW -> savingsNet -= t.amount
                else -> Unit
            }
        }
        return PeriodStats(
            period = p,
            income = income,
            expense = expense,
            savingsNet = savingsNet,
            expenseByCategory = expCat.map { CategoryTotal(it.key, it.value[0], it.value[1].toInt()) }.sortedByDescending { it.total },
            incomeByCategory = incCat.map { CategoryTotal(it.key, it.value[0], it.value[1].toInt()) }.sortedByDescending { it.total },
            expenseByMember = expMember,
            incomeByMember = incMember,
            expenseBuckets = expBuckets,
            incomeBuckets = incBuckets,
            count = count,
            largestExpenses = txns.filter { it.type == TxType.EXPENSE && p.contains(it.occurredAt) }.sortedByDescending { it.amount }.take(5),
        )
    }

    private fun bucketIndex(p: Period, millis: Long): Int {
        val d = Dates.toDate(millis)
        return if (p.kind == PeriodKind.YEAR) d.monthValue - 1 else ChronoUnit.DAYS.between(p.start, d).toInt()
    }

    /** Ay içindeki haftalara göre gider toplamları (Pazartesi başlangıçlı). */
    fun weeklyTotals(txns: List<Txn>, p: Period): List<Pair<Period, Long>> {
        val result = mutableListOf<Pair<Period, Long>>()
        var ws = p.start.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (ws.isBefore(p.endExclusive)) {
            val s = if (ws.isBefore(p.start)) p.start else ws
            val e0 = ws.plusDays(7)
            val e = if (e0.isAfter(p.endExclusive)) p.endExclusive else e0
            val wp = Period(PeriodKind.WEEK, s, e)
            val total = txns.filter { it.type == TxType.EXPENSE && wp.contains(it.occurredAt) }.sumOf { it.amount }
            result += wp to total
            ws = ws.plusDays(7)
        }
        return result
    }

    // ---------------- Düzenli ödemeler ----------------

    fun periodKey(r: Recurring, due: LocalDate): String = when (r.frequency) {
        Frequency.WEEKLY -> "%d-W%02d".format(due.get(IsoFields.WEEK_BASED_YEAR), due.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        Frequency.YEARLY -> due.year.toString()
        else -> "%d-%02d".format(due.year, due.monthValue)
    }

    fun monthKey(d: LocalDate): String = "%d-%02d".format(d.year, d.monthValue)

    /** [from, to] aralığındaki vade tarihleri */
    fun occurrenceDates(r: Recurring, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = if (r.startDate > 0) Dates.toDate(r.startDate) else LocalDate.MIN
        val end = r.endDate?.let { Dates.toDate(it) } ?: LocalDate.MAX
        val out = mutableListOf<LocalDate>()
        when (r.frequency) {
            Frequency.WEEKLY -> {
                val dow = DayOfWeek.of((r.dayOfWeek ?: 1).coerceIn(1, 7))
                var d = from.with(java.time.temporal.TemporalAdjusters.nextOrSame(dow))
                while (!d.isAfter(to)) {
                    out += d
                    d = d.plusWeeks(1)
                }
            }
            Frequency.YEARLY -> {
                val month = (r.monthOfYear ?: (if (r.startDate > 0) Dates.toDate(r.startDate).monthValue else 1)).coerceIn(1, 12)
                for (y in from.year..to.year) {
                    val d = Dates.clampDay(YearMonth.of(y, month), r.dayOfMonth)
                    if (!d.isBefore(from) && !d.isAfter(to)) out += d
                }
            }
            else -> {
                val step = when (r.frequency) {
                    Frequency.BIMONTHLY -> 2
                    Frequency.QUARTERLY -> 3
                    else -> 1
                }
                val anchor = if (r.startDate > 0) YearMonth.from(Dates.toDate(r.startDate)) else YearMonth.from(from)
                var ym = YearMonth.from(from)
                val last = YearMonth.from(to)
                while (!ym.isAfter(last)) {
                    val diff = ChronoUnit.MONTHS.between(anchor, ym)
                    if (Math.floorMod(diff, step.toLong()) == 0L) {
                        val d = Dates.clampDay(ym, r.dayOfMonth)
                        if (!d.isBefore(from) && !d.isAfter(to)) out += d
                    }
                    ym = ym.plusMonths(1)
                }
            }
        }
        return out.filter { !it.isBefore(start) && !it.isAfter(end) }
    }

    /**
     * Ödenmemiş (gecikmiş + yaklaşan) vadeler. lookBackDays kadar geriye, aheadDays kadar ileriye bakar.
     */
    fun pendingOccurrences(
        recurring: List<Recurring>,
        payments: List<Txn>,
        today: LocalDate = Dates.today(),
        lookBackDays: Long = 45,
        aheadDays: Long = 35,
    ): List<Occurrence> {
        val paid = payments.filter { it.recurringId != null }.associateBy { it.recurringId + "|" + it.period }
        val out = mutableListOf<Occurrence>()
        for (r in recurring) {
            if (!r.active || r.deleted) continue
            val createdDate = if (r.createdAt > 0) Dates.toDate(r.createdAt) else today
            val from = maxOf(today.minusDays(lookBackDays), createdDate.minusDays(3))
            for (d in occurrenceDates(r, from, today.plusDays(aheadDays))) {
                val key = periodKey(r, d)
                val p = paid[r.id + "|" + key]
                if (p == null) out += Occurrence(r, d, key, null)
            }
        }
        return out.sortedBy { it.due }
    }

    /** Bir düzenli ödemenin sıradaki (ödenmemiş) vadesi ve son ödemesi */
    fun nextOccurrence(r: Recurring, payments: List<Txn>, today: LocalDate = Dates.today()): Occurrence? {
        val paid = payments.filter { it.recurringId == r.id }.associateBy { it.period }
        val createdDate = if (r.createdAt > 0) Dates.toDate(r.createdAt) else today
        val from = maxOf(today.minusDays(60), createdDate.minusDays(3))
        return occurrenceDates(r, from, today.plusDays(400))
            .map { Occurrence(r, it, periodKey(r, it), paid[periodKey(r, it)]) }
            .firstOrNull { !it.isPaid }
    }

    fun lastPayment(r: Recurring, payments: List<Txn>): Txn? =
        payments.filter { it.recurringId == r.id }.maxByOrNull { it.occurredAt }

    // ---------------- Maaş / gelir günleri ----------------

    /**
     * Bu ayki maaş yattı mı? Önce "Yattı" ile bağlanmış kayda, yoksa aynı kişiye ait,
     * aynı kategoride, maaş gününe yakın ve beklenen tutarın en az yarısı kadar bir
     * gelire bakar (maaşı "Gelir ekle" ile elle girenler için).
     */
    fun paydayStatus(src: IncomeSource, txns: List<Txn>, today: LocalDate = Dates.today()): PaydayStatus {
        val ym = YearMonth.from(today)
        val due = Dates.clampDay(ym, src.payDay)
        val key = monthKey(due)
        val linked = txns.firstOrNull { it.incomeSourceId == src.id && it.period == key && !it.deleted }
        val cat = src.categoryId ?: Seeds.incomeCategoryFor(src.kind)
        val from = Dates.startOfDay(due.minusDays(10))
        val to = Dates.startOfDay(due.plusDays(25))
        val manual = linked ?: txns.firstOrNull {
            it.type == TxType.INCOME && !it.deleted && it.incomeSourceId == null &&
                it.categoryId == cat && (src.memberId == null || it.memberId == src.memberId) &&
                it.occurredAt in from until to && it.amount * 2 >= src.amount
        }
        return PaydayStatus(src, due, key, manual)
    }

    /** Bir sonraki maaş günü (bu ayki alındıysa gelecek ay) */
    fun nextPayday(src: IncomeSource, payments: List<Txn>, today: LocalDate = Dates.today()): LocalDate {
        val st = paydayStatus(src, payments, today)
        return if (st.received != null || st.due.isBefore(today.minusDays(10))) {
            Dates.clampDay(YearMonth.from(today).plusMonths(1), src.payDay)
        } else st.due
    }

    // ---------------- Bütçeler ----------------

    fun budgetViews(budgets: List<Budget>, monthTxns: List<Txn>, p: Period): List<BudgetView> {
        val exp = monthTxns.filter { it.type == TxType.EXPENSE && p.contains(it.occurredAt) }
        return budgets.filter { !it.deleted && it.amount > 0 }.map { b ->
            val spent = if (b.categoryId == null) exp.sumOf { it.amount } else exp.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
            BudgetView(b, spent)
        }.sortedWith(compareBy<BudgetView> { it.budget.categoryId != null }.thenByDescending { it.progress })
    }
}
