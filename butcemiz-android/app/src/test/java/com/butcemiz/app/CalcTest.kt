package com.butcemiz.app

import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.PeriodKind
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AccountFlowRow
import com.butcemiz.app.data.AccountType
import com.butcemiz.app.data.Budget
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Debt
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.Frequency
import com.butcemiz.app.data.IncomeSource
import com.butcemiz.app.data.LinkedTotalRow
import com.butcemiz.app.data.PriceEntry
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.SavingQtyRow
import com.butcemiz.app.data.SavingType
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalcTest {

    private val bank = Account("bank", "Banka", AccountType.BANK, openingBalance = 120_000_00)
    private val cash = Account("cash", "Nakit", AccountType.CASH, openingBalance = 5_000_00)
    private val card = Account("card", "Kart", AccountType.CREDIT_CARD, openingBalance = 0, includeInTotal = false, creditLimit = 50_000_00)

    @Test
    fun balancesFromFlows() {
        val flows = listOf(
            AccountFlowRow("bank", null, TxType.EXPENSE, 2_000_00),
            AccountFlowRow("bank", null, TxType.INCOME, 45_000_00),
            AccountFlowRow("bank", "cash", TxType.TRANSFER, 1_000_00),
            AccountFlowRow("card", null, TxType.EXPENSE, 3_000_00),
            AccountFlowRow("bank", null, TxType.SAVING_DEPOSIT, 10_000_00),
            AccountFlowRow("cash", null, TxType.ADJUSTMENT, -500_00),
            AccountFlowRow(null, null, TxType.EXPENSE, 999_00), // hesapsız: etkisiz
        )
        val b = Calc.balances(listOf(bank, cash, card), flows).associate { it.account.id to it.balance }
        assertEquals(120_000_00L - 2_000_00 + 45_000_00 - 1_000_00 - 10_000_00, b["bank"])
        assertEquals(5_000_00 + 1_000_00 - 500_00L, b["cash"])
        assertEquals(-3_000_00L, b["card"])
        // kredi kartı toplam bakiyeye dahil değil
        assertEquals(b["bank"]!! + b["cash"]!!, Calc.total(Calc.balances(listOf(bank, cash, card), flows)))
    }

    @Test
    fun runningBalanceForFeed() {
        val inc = setOf("bank")
        val t1 = Txn("1", TxType.INCOME, 45_000_00, accountId = "bank", createdAt = 1)
        val t2 = Txn("2", TxType.EXPENSE, 2_000_00, accountId = "bank", createdAt = 2)
        val t3 = Txn("3", TxType.EXPENSE, 500_00, accountId = "card", createdAt = 3)
        val newestFirst = listOf(t3, t2, t1)
        val m = Calc.runningBalances(newestFirst, 163_000_00, inc)
        assertEquals(163_000_00L, m["3"])
        assertEquals(163_000_00L, m["2"]) // kart hareketi toplamı etkilemez
        assertEquals(165_000_00L, m["1"])
    }

    @Test
    fun savingsValuation() {
        val gold = Saving("g", "Altın", SavingType.GOLD_GRAM, initialQty = 10.0)
        val usd = Saving("u", "Dolar", SavingType.USD, initialQty = 1000.0)
        val tl = Saving("t", "Mevduat", SavingType.TRY, initialQty = 50_000.0)
        val fund = Saving("f", "Fon", SavingType.FUND, initialQty = 100.0, manualUnitPrice = 12_50)
        val rows = listOf(
            SavingQtyRow("g", TxType.SAVING_DEPOSIT, 5.0, 32_000_00),
            SavingQtyRow("g", TxType.SAVING_WITHDRAW, 2.0, 13_000_00),
        )
        val prices = mapOf(
            "gram-altin" to PriceEntry("gram-altin", 6_500.0, 6_510.0, null, 1, "t"),
            "USD" to PriceEntry("USD", 50.0, 50.1, null, 1, "t"),
        )
        val v = Calc.savingViews(listOf(gold, usd, tl, fund), rows, prices).associateBy { it.saving.id }
        assertEquals(13.0, v["g"]!!.quantity, 1e-9)
        assertEquals(13 * 6_500_00L, v["g"]!!.value)
        assertEquals(19_000_00L, v["g"]!!.netInvested)
        assertEquals(1000 * 50_00L, v["u"]!!.value)
        assertEquals(50_000_00L, v["t"]!!.value)
        assertEquals(100 * 12_50L, v["f"]!!.value)
    }

    @Test
    fun debtRemaining() {
        val loan = Debt("d", "Kredi", DebtDirection.I_OWE, principal = 100_000_00, alreadyPaid = 10_000_00, dueDay = 5)
        val lent = Debt("l", "Ahmet", DebtDirection.OWED_TO_ME, principal = 5_000_00)
        val rows = listOf(
            LinkedTotalRow("d", TxType.EXPENSE, 20_000_00),
            LinkedTotalRow("d", TxType.LOAN_RECEIVED, 100_000_00), // ödeme sayılmaz
            LinkedTotalRow("l", TxType.INCOME, 2_000_00),
        )
        val v = Calc.debtViews(listOf(loan, lent), rows, LocalDate.of(2026, 11, 6)).associateBy { it.debt.id }
        assertEquals(70_000_00L, v["d"]!!.remaining)
        assertEquals(0.3f, v["d"]!!.progress, 1e-6f)
        assertEquals(LocalDate.of(2026, 12, 5), v["d"]!!.nextDue)
        assertEquals(3_000_00L, v["l"]!!.remaining)
    }

    @Test
    fun periodStatsAndWeekly() {
        val p = Periods.month(LocalDate.of(2026, 11, 6), 1)
        fun at(d: Int) = Dates.millisOf(LocalDate.of(2026, 11, d))
        val txns = listOf(
            Txn("a", TxType.EXPENSE, 1_000_00, categoryId = "market", memberId = "m1", occurredAt = at(2)),
            Txn("b", TxType.EXPENSE, 3_000_00, categoryId = "kira", memberId = "m2", occurredAt = at(3)),
            Txn("c", TxType.INCOME, 45_000_00, categoryId = "maas", memberId = "m1", occurredAt = at(5)),
            Txn("d", TxType.EXPENSE, 500_00, categoryId = "market", memberId = "m1", occurredAt = at(10)),
            Txn("e", TxType.TRANSFER, 9_999_00, occurredAt = at(10)),
            Txn("f", TxType.SAVING_DEPOSIT, 6_000_00, occurredAt = at(11)),
        )
        val s = Calc.periodStats(txns, p)
        assertEquals(4_500_00L, s.expense)
        assertEquals(45_000_00L, s.income)
        assertEquals(6_000_00L, s.savingsNet)
        assertEquals("kira", s.expenseByCategory.first().categoryId)
        assertEquals(1_500_00L, s.expenseByCategory.first { it.categoryId == "market" }.total)
        assertEquals(1_500_00L, s.expenseByMember["m1"])
        assertEquals(30, s.expenseBuckets.size)
        assertEquals(1_000_00L, s.expenseBuckets[1])
        val weeks = Calc.weeklyTotals(txns, p)
        assertEquals(LocalDate.of(2026, 11, 1), weeks.first().first.start) // ilk hafta ay başında kesilir
        assertEquals(txns.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }, weeks.sumOf { it.second })
        assertEquals(PeriodKind.YEAR, Periods.year(LocalDate.of(2026, 1, 1)).kind)
    }

    @Test
    fun recurringOccurrencesAndPaidState() {
        val today = LocalDate.of(2026, 11, 6)
        val created = Dates.millisOf(LocalDate.of(2026, 9, 1))
        val elec = Recurring("r", "Elektrik", 850_00, variable = true, dayOfMonth = 3, startDate = created, createdAt = created)
        val rent = Recurring("k", "Kira", 15_000_00, dayOfMonth = 31, startDate = created, createdAt = created)
        val paidOct = Txn("rp_r_2026-10", TxType.EXPENSE, 900_00, recurringId = "r", period = "2026-10", occurredAt = Dates.millisOf(LocalDate.of(2026, 10, 3)))
        val pending = Calc.pendingOccurrences(listOf(elec, rent), listOf(paidOct), today)
        val keys = pending.map { it.recurring.id + ":" + it.periodKey }
        assertTrue("Kasım elektrik bekliyor", "r:2026-11" in keys)
        assertTrue("Ekim elektrik ödendi", "r:2026-10" !in keys)
        assertTrue("Ekim kirası gecikti (31 Ekim)", "k:2026-10" in keys)
        // Şubat'ta 31 → ayın son günü
        val feb = Calc.occurrenceDates(rent, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28))
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), feb)
        val weekly = Recurring("w", "Pazar", 500_00, frequency = Frequency.WEEKLY, dayOfWeek = 7)
        assertEquals(4, Calc.occurrenceDates(weekly, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 28)).size)
        val yearly = Recurring("y", "Kasko", 12_000_00, frequency = Frequency.YEARLY, dayOfMonth = 20, monthOfYear = 3)
        assertEquals(listOf(LocalDate.of(2027, 3, 20)), Calc.occurrenceDates(yearly, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 12, 31)).take(1))
        val quarterly = Recurring("q", "Aidat", 3_000_00, frequency = Frequency.QUARTERLY, dayOfMonth = 10, startDate = Dates.millisOf(LocalDate.of(2026, 1, 10)))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 10, 10)),
            Calc.occurrenceDates(quarterly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 1)),
        )
        val next = Calc.nextOccurrence(elec, listOf(paidOct), today)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 11, 3), next!!.due)
    }

    @Test
    fun paydayAndBudgets() {
        val today = LocalDate.of(2026, 11, 15)
        val src = IncomeSource("s", "Maaş", amount = 45_000_00, payDay = 15)
        val st = Calc.paydayStatus(src, emptyList(), today)
        assertEquals(today, st.due)
        assertNull(st.received)
        val got = Txn("inc_s_2026-11", TxType.INCOME, 45_000_00, incomeSourceId = "s", period = "2026-11")
        assertNotNull(Calc.paydayStatus(src, listOf(got), today).received)
        assertEquals(LocalDate.of(2026, 12, 15), Calc.nextPayday(src, listOf(got), today))

        val p = Periods.month(today, 1)
        val txns = listOf(
            Txn("a", TxType.EXPENSE, 9_000_00, categoryId = "market", occurredAt = Dates.millisOf(LocalDate.of(2026, 11, 2))),
            Txn("b", TxType.EXPENSE, 2_000_00, categoryId = "yemek", occurredAt = Dates.millisOf(LocalDate.of(2026, 11, 3))),
        )
        val views = Calc.budgetViews(listOf(Budget("budget_total", null, 20_000_00), Budget("budget_market", "market", 10_000_00)), txns, p)
        assertEquals(11_000_00L, views.first { it.budget.categoryId == null }.spent)
        assertEquals(0.9f, views.first { it.budget.categoryId == "market" }.progress, 1e-6f)
    }
}
