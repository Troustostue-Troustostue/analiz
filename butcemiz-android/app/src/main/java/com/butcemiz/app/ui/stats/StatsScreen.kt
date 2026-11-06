package com.butcemiz.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.butcemiz.app.AppContainer
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.core.Period
import com.butcemiz.app.core.PeriodKind
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.PeriodStats
import com.butcemiz.app.data.TxType
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.AppIcons
import com.butcemiz.app.ui.components.BarChart
import com.butcemiz.app.ui.components.DonutChart
import com.butcemiz.app.ui.components.EmptyState
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.LabeledAmount
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.ProgressBar
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.Slice
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.compactMoney
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import com.butcemiz.app.ui.transactions.PeriodBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TrendPoint(val label: String, val income: Long, val expense: Long)

data class StatsState(
    val stats: PeriodStats? = null,
    val weekly: List<Pair<Period, Long>> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val members: Map<String, Member> = emptyMap(),
)

class StatsViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val kind = MutableStateFlow(PeriodKind.MONTH)
    val anchor = MutableStateFlow(Dates.today())

    val period: StateFlow<Period> = combine(kind, anchor, repo.monthStartDay) { k, a, sd -> Periods.of(k, a, sd) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Periods.month(Dates.today()))

    private val trend = repo.monthStartDay.flatMapLatest { sd ->
        val cur = Periods.month(Dates.today(), sd)
        val first = Periods.shift(cur, -5, sd)
        repo.txnsBetween(first.startMillis, cur.endMillis).map { list ->
            (0..5).map { i ->
                val p = Periods.shift(first, i, sd)
                val inP = list.filter { p.contains(it.occurredAt) }
                TrendPoint(
                    Dates.monthShort(if (sd == 1) p.start else p.endExclusive.minusDays(1)).replaceFirstChar { it.titlecase(com.butcemiz.app.core.TR) },
                    inP.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                    inP.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
                )
            }
        }
    }

    val state: StateFlow<StatsState> = combine(period.flatMapLatest { p -> repo.txnsBetween(p).map { p to it } }, repo.categories, repo.members, trend) { (p, list), cats, mems, tr ->
        StatsState(
            stats = Calc.periodStats(list, p),
            weekly = if (p.kind == PeriodKind.MONTH) Calc.weeklyTotals(list, p) else emptyList(),
            trend = tr,
            categories = cats.associateBy { it.id },
            members = mems.associateBy { it.id },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun shift(n: Int) {
        val p = period.value
        anchor.value = when (p.kind) {
            PeriodKind.WEEK -> p.start.plusWeeks(n.toLong())
            PeriodKind.MONTH -> p.start.plusMonths(n.toLong())
            PeriodKind.YEAR -> p.start.plusYears(n.toLong())
        }
    }
}

@Composable
fun StatsScreen(nav: NavController) {
    val vm = appViewModel { StatsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current
    val st = s.stats

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Analiz") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PeriodBar(kind, period, onKind = { vm.kind.value = it; vm.anchor.value = Dates.today() }, onShift = vm::shift) }
            if (st == null) return@LazyColumn
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth()) {
                        LabeledAmount("Gelir", "+" + money(st.income, Money.Decimals.NEVER), sem.income, Modifier.weight(1f))
                        LabeledAmount("Gider", Money.MINUS + money(st.expense, Money.Decimals.NEVER), sem.expense, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        LabeledAmount("Net", money(st.net, Money.Decimals.NEVER, sign = true), if (st.net >= 0) sem.income else sem.expense, Modifier.weight(1f))
                        LabeledAmount("Günlük ort. gider", money(st.dailyAverage, Money.Decimals.NEVER), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    }
                    if (st.savingsNet != 0L) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            LabeledAmount("Birikime aktarılan", money(st.savingsNet, Money.Decimals.NEVER), sem.gold, Modifier.weight(1f))
                            val rate = if (st.income > 0) (st.savingsNet * 100 / st.income).toInt() else 0
                            LabeledAmount("Birikim oranı", "%$rate", sem.gold, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (st.count == 0) {
                item {
                    EmptyState(Icons.Rounded.PieChart, "Bu dönemde veri yok", "Harcama ve gelir ekledikçe grafikler burada oluşur.", action = "Harcama ekle", onAction = { nav.openAdd(TxType.EXPENSE) })
                }
                return@LazyColumn
            }
            if (st.expense > 0) {
                item { CategoryDonut(st, s.categories) }
            }
            item { DailyBars(st, period) }
            if (s.weekly.isNotEmpty()) {
                item { WeeklyCard(s.weekly) }
            }
            if (s.members.size > 1 && st.expense > 0) {
                item { MemberSplit(st, s.members) }
            }
            if (st.incomeByCategory.isNotEmpty()) {
                item { IncomeCard(st, s.categories) }
            }
            if (st.largestExpenses.isNotEmpty()) {
                item {
                    AppCard {
                        Text("En büyük harcamalar", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        st.largestExpenses.forEachIndexed { i, t ->
                            val cat = s.categories[t.categoryId]
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title.ifBlank { cat?.name ?: "Harcama" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${cat?.name ?: ""} · ${Dates.dayMonthShort(Dates.toDate(t.occurredAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(money(t.amount), style = AmountStyle.copy(fontSize = 14.sp))
                            }
                        }
                    }
                }
            }
            if (s.trend.any { it.income > 0 || it.expense > 0 }) {
                item { TrendCard(s.trend) }
            }
        }
    }
}

@Composable
private fun CategoryDonut(st: PeriodStats, cats: Map<String, Category>) {
    val other = MaterialTheme.colorScheme.outline
    AppCard {
        Text("Kategorilere göre gider", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        val top = st.expenseByCategory.take(7)
        val rest = st.expenseByCategory.drop(7)
        val slices = top.map { Slice(it.total.toFloat(), cats[it.categoryId]?.let { c -> colorOf(c.color) } ?: other) } +
            if (rest.isNotEmpty()) listOf(Slice(rest.sumOf { it.total }.toFloat(), other)) else emptyList()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(slices, size = 200.dp, thickness = 26.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Toplam", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(st.expense, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 20.sp))
                    Text("${st.expenseByCategory.sumOf { it.count }} harcama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        st.expenseByCategory.forEach { ct ->
            val cat = cats[ct.categoryId]
            val color = cat?.let { colorOf(it.color) } ?: other
            val pct = if (st.expense > 0) ct.total.toFloat() / st.expense else 0f
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.of(cat?.icon), color, size = 34.dp, iconSize = 18.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat?.name ?: "Kategorisiz", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(ct.total, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 14.sp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressBar(pct, color, Modifier.weight(1f), height = 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("%${(pct * 100).toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBars(st: PeriodStats, p: Period) {
    val sem = LocalSemantic.current
    var selected by remember(p) { mutableStateOf<Int?>(null) }
    val labels: List<String>
    val every: Int
    when (p.kind) {
        PeriodKind.WEEK -> {
            labels = (0 until 7).map { Dates.weekdayShort(p.start.plusDays(it.toLong())) }
            every = 1
        }
        PeriodKind.MONTH -> {
            labels = (0 until p.days).map { p.start.plusDays(it.toLong()).dayOfMonth.toString() }
            every = 5
        }
        PeriodKind.YEAR -> {
            labels = (1..12).map { Dates.monthShort(java.time.LocalDate.of(p.start.year, it, 1)).take(3) }
            every = 1
        }
    }
    val title = when (p.kind) {
        PeriodKind.YEAR -> "Aylara göre gelir ve gider"
        else -> "Günlere göre gider"
    }
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val sel = selected
        val subtitle = if (sel != null) {
            val label = when (p.kind) {
                PeriodKind.YEAR -> labels[sel]
                else -> Dates.dayMonth(p.start.plusDays(sel.toLong()))
            }
            "$label: ${money(st.expenseBuckets[sel], Money.Decimals.NEVER)} gider" +
                if (p.kind == PeriodKind.YEAR) " · ${money(st.incomeBuckets[sel], Money.Decimals.NEVER)} gelir" else ""
        } else {
            "Bir çubuğa dokunarak ayrıntı görün"
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = st.expenseBuckets.toList(),
            labels = labels,
            color = sem.expense,
            labelEvery = every,
            selected = selected,
            secondary = if (p.kind == PeriodKind.YEAR) st.incomeBuckets.toList() else null,
            secondaryColor = sem.income,
            onSelect = { selected = if (selected == it) null else it },
        )
        if (p.kind == PeriodKind.YEAR) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend("Gider", sem.expense)
                Spacer(Modifier.width(16.dp))
                Legend("Gelir", sem.income)
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WeeklyCard(weeks: List<Pair<Period, Long>>) {
    val sem = LocalSemantic.current
    val max = weeks.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    AppCard {
        Text("Haftalık giderler", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        weeks.forEachIndexed { i, (wp, total) ->
            val isNow = wp.containsDate(Dates.today())
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(110.dp)) {
                    Text("${i + 1}. hafta", style = MaterialTheme.typography.bodyMedium, fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium)
                    Text(Periods.label(wp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProgressBar(total.toFloat() / max, if (isNow) sem.expense else sem.expense.copy(alpha = 0.55f), Modifier.weight(1f), height = 10.dp)
                Spacer(Modifier.width(10.dp))
                Text(money(total, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 14.sp), modifier = Modifier.width(92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun MemberSplit(st: PeriodStats, members: Map<String, Member>) {
    AppCard {
        Text("Kim ne kadar harcadı?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        st.expenseByMember.entries.sortedByDescending { it.value }.forEach { (id, total) ->
            val m = id?.let { members[it] }
            val pct = if (st.expense > 0) total.toFloat() / st.expense else 0f
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(m, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(m?.name ?: "Belirtilmemiş", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${money(total, Money.Decimals.NEVER)} · %${(pct * 100).toInt()}", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(pct, m?.let { colorOf(it.color) } ?: MaterialTheme.colorScheme.outline, height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun IncomeCard(st: PeriodStats, cats: Map<String, Category>) {
    val sem = LocalSemantic.current
    AppCard {
        Text("Gelir kaynakları", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        st.incomeByCategory.forEach { ct ->
            val cat = cats[ct.categoryId]
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.of(cat?.icon), cat?.let { colorOf(it.color) } ?: sem.income, size = 32.dp, iconSize = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(cat?.name ?: "Diğer", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("+" + money(ct.total, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 14.sp), color = sem.income)
            }
        }
    }
}

@Composable
private fun TrendCard(trend: List<TrendPoint>) {
    val sem = LocalSemantic.current
    var selected by remember { mutableStateOf<Int?>(trend.lastIndex) }
    AppCard {
        SectionHeader("Son 6 ay")
        val sel = selected
        if (sel != null && sel in trend.indices) {
            val t = trend[sel]
            Text(
                "${t.label}: +${money(t.income, Money.Decimals.NEVER)} gelir · ${Money.MINUS}${money(t.expense, Money.Decimals.NEVER)} gider",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        BarChart(
            values = trend.map { it.expense },
            labels = trend.map { it.label },
            color = sem.expense,
            secondary = trend.map { it.income },
            secondaryColor = sem.income,
            selected = selected,
            onSelect = { selected = it },
            height = 160.dp,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Legend("Gider", sem.expense)
            Spacer(Modifier.width(16.dp))
            Legend("Gelir", sem.income)
            Spacer(Modifier.weight(1f))
            val avg = trend.map { it.expense }.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.sum() / it.size }
            Text("Ort. gider ${compactMoney(avg)} ₺", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
