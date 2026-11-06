package com.butcemiz.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.butcemiz.app.AppContainer
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Period
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AccountBalance
import com.butcemiz.app.data.BudgetView
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.CategoryTotal
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.DebtView
import com.butcemiz.app.data.Household
import com.butcemiz.app.data.IncomeSource
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Occurrence
import com.butcemiz.app.data.PaydayStatus
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.SavingView
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.data.Budget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HomeState(
    val loading: Boolean = true,
    val householdName: String = "",
    val me: Member? = null,
    val members: Map<String, Member> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val total: Long = 0,
    val accountCount: Int = 0,
    val period: Period? = null,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val prevMonthExpenseSoFar: Long = 0,
    val weekExpense: Long = 0,
    val prevWeekExpense: Long = 0,
    val todayExpense: Long = 0,
    val feed: List<Txn> = emptyList(),
    val balanceAfter: Map<String, Long> = emptyMap(),
    val upcoming: List<Occurrence> = emptyList(),
    val paydays: List<PaydayStatus> = emptyList(),
    val budgets: List<BudgetView> = emptyList(),
    val savingsTotal: Long = 0,
    val debtTotal: Long = 0,
    val receivableTotal: Long = 0,
    val topCategories: List<CategoryTotal> = emptyList(),
    val monthSavings: Long = 0,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    private val today: LocalDate = Dates.today()

    private data class Ref(
        val members: List<Member>,
        val categories: List<Category>,
        val balances: List<AccountBalance>,
        val household: Household,
        val me: String?,
    )

    private data class Window(val month: Period, val prevMonth: Period, val week: Period, val txns: List<Txn>)

    private data class Plans(
        val recurring: List<Recurring>,
        val payments: List<Txn>,
        val incomes: List<IncomeSource>,
        val budgets: List<Budget>,
    )

    private data class Wealth(val savings: List<SavingView>, val debts: List<DebtView>)

    private val ref = combine(repo.members, repo.categories, repo.accountBalances, repo.household, c.prefs.myMemberIdFlow) { m, cat, b, h, me ->
        Ref(m, cat, b, h, me)
    }

    private val window = repo.monthStartDay.flatMapLatest { sd ->
        val month = Periods.month(today, sd)
        val prev = Periods.shift(month, -1, sd)
        val week = Periods.week(today)
        val start = minOf(prev.startMillis, Periods.shift(week, -1, sd).startMillis)
        val end = maxOf(month.endMillis, week.endMillis)
        repo.txnsBetween(start, end).map { Window(month, prev, week, it) }
    }

    private val plans = combine(repo.recurring, repo.scheduledPayments(), repo.incomeSources, repo.budgets) { r, p, i, b ->
        Plans(r, p, i, b)
    }

    private val wealth = combine(repo.savings, repo.debts) { s, d -> Wealth(s, d) }

    val state: StateFlow<HomeState> = combine(ref, window, plans, wealth, repo.recentTxns(25)) { r, w, p, wl, recent ->
        build(r, w, p, wl, recent)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun build(r: Ref, w: Window, p: Plans, wl: Wealth, recent: List<Txn>): HomeState {
        val total = Calc.total(r.balances)
        val included = r.balances.filter { Calc.includedInTotal(it.account) }.map { it.account.id }.toSet()
        val stats = Calc.periodStats(w.txns, w.month)
        val daysIn = ChronoUnit.DAYS.between(w.month.start, today) + 1
        val prevCut = Dates.startOfDay(w.prevMonth.start.plusDays(daysIn))
        val prevSoFar = w.txns.filter { it.type == TxType.EXPENSE && it.occurredAt >= w.prevMonth.startMillis && it.occurredAt < minOf(prevCut, w.prevMonth.endMillis) }.sumOf { it.amount }
        val prevWeek = Periods.week(today.minusWeeks(1))
        fun expenseIn(pr: Period) = w.txns.filter { it.type == TxType.EXPENSE && pr.contains(it.occurredAt) }.sumOf { it.amount }
        val todayP = Period(com.butcemiz.app.core.PeriodKind.WEEK, today, today.plusDays(1))
        val paydays = p.incomes.filter { it.active }
            .map { Calc.paydayStatus(it, p.payments + w.txns, today) }
            .filter { it.received == null && !it.due.isAfter(today) && ChronoUnit.DAYS.between(it.due, today) <= 5 }
        return HomeState(
            loading = false,
            householdName = r.household.name,
            me = r.members.firstOrNull { it.id == r.me },
            members = r.members.associateBy { it.id },
            categories = r.categories.associateBy { it.id },
            accounts = r.balances.associate { it.account.id to it.account },
            total = total,
            accountCount = r.balances.count { !it.account.archived },
            period = w.month,
            monthIncome = stats.income,
            monthExpense = stats.expense,
            prevMonthExpenseSoFar = prevSoFar,
            weekExpense = expenseIn(w.week),
            prevWeekExpense = expenseIn(prevWeek),
            todayExpense = expenseIn(todayP),
            feed = recent,
            balanceAfter = Calc.runningBalances(recent, total, included),
            upcoming = Calc.pendingOccurrences(p.recurring, p.payments, today, lookBackDays = 40, aheadDays = 7),
            paydays = paydays,
            budgets = Calc.budgetViews(p.budgets, w.txns, w.month),
            savingsTotal = wl.savings.filter { !it.saving.archived }.sumOf { it.value },
            debtTotal = wl.debts.filter { it.debt.direction == DebtDirection.I_OWE && !it.debt.closed }.sumOf { it.remaining },
            receivableTotal = wl.debts.filter { it.debt.direction == DebtDirection.OWED_TO_ME && !it.debt.closed }.sumOf { it.remaining },
            topCategories = stats.expenseByCategory.take(4),
            monthSavings = stats.savingsNet,
        )
    }

    fun toggleHide() {
        c.prefs.hideAmounts = !c.prefs.hideAmounts
    }

    fun payOccurrence(o: Occurrence, onDone: (Long) -> Unit) = viewModelScope.launch {
        c.repo.payRecurring(o.recurring, o.periodKey, o.recurring.amount, o.recurring.accountId ?: Seeds.MAIN_ACCOUNT_ID, c.prefs.myMemberId, System.currentTimeMillis())
        onDone(o.recurring.amount)
    }

    fun recordPayday(st: PaydayStatus) = viewModelScope.launch {
        c.repo.recordIncome(st.source, st.periodKey, st.source.amount, st.source.accountId, System.currentTimeMillis())
    }
}
