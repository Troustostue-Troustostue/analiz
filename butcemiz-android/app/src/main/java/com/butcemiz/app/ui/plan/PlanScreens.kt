package com.butcemiz.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.AccountBalance
import com.butcemiz.app.data.BudgetView
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.CategoryKind
import com.butcemiz.app.data.Frequency
import com.butcemiz.app.data.IncomeKind
import com.butcemiz.app.data.IncomeSource
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Occurrence
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.ui.assets.FormLabel
import com.butcemiz.app.ui.assets.FormSheet
import com.butcemiz.app.ui.assets.NameField
import com.butcemiz.app.ui.assets.SwitchLine
import com.butcemiz.app.ui.components.AmountTextField
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.AppIcons
import com.butcemiz.app.ui.components.ChipRow
import com.butcemiz.app.ui.components.ConfirmDialog
import com.butcemiz.app.ui.components.DatePickerModal
import com.butcemiz.app.ui.components.DateSelector
import com.butcemiz.app.ui.components.EmptyState
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.Pill
import com.butcemiz.app.ui.components.ProgressBar
import com.butcemiz.app.ui.components.ScreenScaffold
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.SelectField
import com.butcemiz.app.ui.components.TxnRow
import com.butcemiz.app.ui.components.amountToKurus
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.shell.LocalToast
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

data class PlanState(
    val recurring: List<Recurring> = emptyList(),
    val payments: List<Txn> = emptyList(),
    val incomes: List<IncomeSource> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
    val members: List<Member> = emptyList(),
    val monthTxns: List<Txn> = emptyList(),
    val month: Period? = null,
    val budgets: List<BudgetView> = emptyList(),
) {
    val categoryMap get() = categories.associateBy { it.id }
    val memberMap get() = members.associateBy { it.id }
}

class PlanViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo

    private data class A(val r: List<Recurring>, val p: List<Txn>, val i: List<IncomeSource>, val cat: List<Category>)
    private data class B(val acc: List<AccountBalance>, val m: List<Member>)

    private val a = combine(repo.recurring, repo.scheduledPayments(), repo.incomeSources, repo.categories) { r, p, i, cat -> A(r, p, i, cat) }
    private val b = combine(repo.accountBalances, repo.members) { acc, m -> B(acc, m) }
    private val month = repo.monthStartDay.flatMapLatest { sd ->
        val p = Periods.month(Dates.today(), sd)
        repo.txnsBetween(p).map { p to it }
    }

    val state: StateFlow<PlanState> = combine(a, b, month, repo.budgets) { x, y, (p, txns), budgets ->
        PlanState(x.r, x.p, x.i, x.cat, y.acc, y.m, txns, p, Calc.budgetViews(budgets, txns, p))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    val myMember: String? get() = c.prefs.myMemberId

    fun saveRecurring(r: Recurring) = viewModelScope.launch { repo.saveRecurring(r) }
    fun deleteRecurring(r: Recurring) = viewModelScope.launch { repo.deleteRecurring(r) }
    fun pay(o: Occurrence, amount: Long, accountId: String?, memberId: String?, date: LocalDate) = viewModelScope.launch {
        repo.payRecurring(o.recurring, o.periodKey, amount, accountId, memberId, millisFor(date))
    }

    fun saveIncome(s: IncomeSource) = viewModelScope.launch { repo.saveIncomeSource(s) }
    fun deleteIncome(s: IncomeSource) = viewModelScope.launch { repo.deleteIncomeSource(s) }
    fun recordIncome(s: IncomeSource, period: String, amount: Long, accountId: String?, date: LocalDate) = viewModelScope.launch {
        repo.recordIncome(s, period, amount, accountId, millisFor(date))
    }

    fun setBudget(categoryId: String?, amount: Long) = viewModelScope.launch { repo.setBudget(categoryId, amount) }

    private fun millisFor(d: LocalDate): Long =
        if (d == Dates.today()) System.currentTimeMillis() else Dates.millisOf(d, LocalTime.now().withNano(0))
}

fun monthlyEquivalent(r: Recurring): Long = when (r.frequency) {
    Frequency.MONTHLY -> r.amount
    Frequency.WEEKLY -> r.amount * 52 / 12
    Frequency.BIMONTHLY -> r.amount / 2
    Frequency.QUARTERLY -> r.amount / 3
    Frequency.YEARLY -> r.amount / 12
}

fun scheduleText(r: Recurring): String = when (r.frequency) {
    Frequency.WEEKLY -> "Her hafta · " + DayOfWeek.of((r.dayOfWeek ?: 1).coerceIn(1, 7)).getDisplayName(TextStyle.FULL, com.butcemiz.app.core.TR)
    Frequency.YEARLY -> "Her yıl · ${r.dayOfMonth} " + java.time.Month.of((r.monthOfYear ?: 1).coerceIn(1, 12)).getDisplayName(TextStyle.FULL, com.butcemiz.app.core.TR)
    else -> r.frequency.label + " · ayın ${r.dayOfMonth}. günü"
}

// ======================================================================
//                          DÜZENLİ ÖDEMELER
// ======================================================================

@Composable
fun PaymentsScreen(nav: NavController) {
    val vm = appViewModel { PlanViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var creating by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Occurrence?>(null) }
    val today = Dates.today()
    val pending = Calc.pendingOccurrences(s.recurring, s.payments, today, lookBackDays = 60, aheadDays = 10)
    val late = pending.filter { it.due.isBefore(today) }

    ScreenScaffold(
        title = "Düzenli ödemeler",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Ekle") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val active = s.recurring.filter { it.active }
            item {
                AppCard {
                    Text("Aylık düzenli ödeme yükü", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(active.sumOf { monthlyEquivalent(it) }, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 26.sp))
                    Text(
                        "${active.size} kayıt · fatura, kira, aidat, abonelik ve taksitler. Vadesi yaklaşınca iki telefona da hatırlatılır; biri ödeyince diğerinde de \"ödendi\" görünür.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (late.isNotEmpty()) {
                item { SectionHeader("Gecikenler") }
                items(late, key = { "late" + it.recurring.id + it.periodKey }) { o ->
                    OccurrenceRow(o, s, onPay = { paying = o })
                }
            }
            if (s.recurring.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.EventRepeat,
                            "Düzenli ödeme ekleyin",
                            "Elektrik, su, doğalgaz, internet, kira, aidat, Netflix, kredi taksiti… Her ay hatırlatılır, tek dokunuşla \"ödendi\" işaretlenir.",
                            action = "Ödeme ekle",
                            onAction = { creating = true },
                        )
                    }
                }
            } else {
                item { SectionHeader("Tümü") }
                items(s.recurring, key = { it.id }) { r ->
                    val next = Calc.nextOccurrence(r, s.payments, today)
                    val last = Calc.lastPayment(r, s.payments)
                    val cat = s.categoryMap[r.categoryId]
                    AppCard(onClick = { editing = r }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcons.of(cat?.icon ?: "receipt"), cat?.let { colorOf(it.color) } ?: MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(scheduleText(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text((if (r.variable) "≈ " else "") + money(r.amount), style = AmountStyle.copy(fontSize = 16.sp))
                                if (!r.active) Pill("Pasif", MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (r.active) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (next != null) {
                                        val d = Dates.daysUntil(next.due, today)
                                        Text(
                                            "Sıradaki: ${Dates.dayMonth(next.due)}" + if (d < 7) " · ${Dates.dueText(next.due, today)}" else "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (d < 0) sem.expense else if (d <= 2) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (last != null) {
                                        Text("Son ödeme: ${Dates.dayMonth(Dates.toDate(last.occurredAt))} · ${money(last.amount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (next != null && !next.due.isAfter(today.plusDays(10))) {
                                    FilledTonalButton(onClick = { paying = next }) { Text("Öde") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        RecurringEditorSheet(
            initial = editing,
            categories = s.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived },
            accounts = s.accounts,
            onDismiss = { creating = false; editing = null },
            onDelete = { r -> vm.deleteRecurring(r); editing = null; toast("Silindi", null, null) },
        ) { r ->
            vm.saveRecurring(r)
            creating = false
            editing = null
            toast("Kaydedildi", null, null)
        }
    }
    paying?.let { o ->
        PayOccurrenceSheet(o, s.accounts, s.members, vm.myMember, onDismiss = { paying = null }) { amount, acc, member, date ->
            vm.pay(o, amount, acc, member, date)
            paying = null
            toast("${o.recurring.name} ödendi · ${Money.format(amount)}", null, null)
        }
    }
}

@Composable
private fun OccurrenceRow(o: Occurrence, s: PlanState, onPay: () -> Unit) {
    val sem = LocalSemantic.current
    val cat = s.categoryMap[o.recurring.categoryId]
    AppCard(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.of(cat?.icon ?: "receipt"), sem.expense)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.recurring.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${Dates.dayMonth(o.due)} · ${Dates.dueText(o.due)}", style = MaterialTheme.typography.bodySmall, color = sem.expense)
            }
            FilledTonalButton(onClick = onPay) { Text("Öde") }
        }
    }
}

@Composable
fun PayOccurrenceSheet(
    o: Occurrence,
    accounts: List<AccountBalance>,
    members: List<Member>,
    myMember: String?,
    onDismiss: () -> Unit,
    onSave: (Long, String?, String?, LocalDate) -> Unit,
) {
    var amount by remember { mutableStateOf(if (o.recurring.amount > 0) Money.toEditable(o.recurring.amount) else "") }
    var accountId by remember { mutableStateOf(o.recurring.accountId ?: Seeds.MAIN_ACCOUNT_ID) }
    var member by remember { mutableStateOf(myMember) }
    var date by remember { mutableStateOf(Dates.today()) }
    FormSheet(
        title = "${o.recurring.name} · ${Dates.dayMonth(o.due)}",
        onDismiss = onDismiss,
        saveLabel = "Ödendi olarak kaydet",
        canSave = amountToKurus(amount) > 0,
        onSave = { onSave(amountToKurus(amount), accountId, member, date) },
    ) {
        AmountTextField(if (o.recurring.variable) "Bu ayki tutar" else "Tutar", amount, { amount = it })
        val active = accounts.filter { !it.account.archived }
        if (active.size > 1) {
            FormLabel("Hesap")
            ChipRow(active, active.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        if (members.size > 1) {
            FormLabel("Kim ödedi?")
            ChipRow(members, members.firstOrNull { it.id == member }, { it.name }, { member = it.id })
        }
        FormLabel("Ödeme tarihi")
        DateSelector(date, { date = it })
    }
}

@Composable
fun RecurringEditorSheet(
    initial: Recurring?,
    categories: List<Category>,
    accounts: List<AccountBalance>,
    onDismiss: () -> Unit,
    onDelete: (Recurring) -> Unit,
    onSave: (Recurring) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.let { Money.toEditable(it) } ?: "") }
    var variable by remember { mutableStateOf(initial?.variable ?: false) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: Seeds.CAT_BILLS) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: Seeds.MAIN_ACCOUNT_ID) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: Frequency.MONTHLY) }
    var day by remember { mutableStateOf((initial?.dayOfMonth ?: Dates.today().dayOfMonth).toString()) }
    var dow by remember { mutableStateOf(initial?.dayOfWeek ?: 1) }
    var month by remember { mutableStateOf(initial?.monthOfYear ?: Dates.today().monthValue) }
    var remind by remember { mutableStateOf(initial?.remindDaysBefore ?: 2) }
    var endDate by remember { mutableStateOf(initial?.endDate?.let { Dates.toDate(it) }) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    FormSheet(
        title = if (initial == null) "Yeni düzenli ödeme" else "Düzenli ödemeyi düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank(),
        onSave = {
            onSave(
                (initial ?: Recurring(id = "", name = "")).copy(
                    name = name.trim(),
                    amount = amountToKurus(amount),
                    variable = variable,
                    categoryId = categoryId,
                    accountId = accountId,
                    frequency = frequency,
                    dayOfMonth = (day.toIntOrNull() ?: 1).coerceIn(1, 31),
                    dayOfWeek = if (frequency == Frequency.WEEKLY) dow else null,
                    monthOfYear = if (frequency == Frequency.YEARLY) month else null,
                    startDate = initial?.startDate?.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    endDate = endDate?.let { Dates.startOfDay(it.plusDays(1)) - 1 },
                    remindDaysBefore = remind,
                    active = active,
                ),
            )
        },
    ) {
        NameField("Ad (ör. Elektrik, Kira, Netflix)", name) { name = it }
        AmountTextField(if (variable) "Tahmini tutar" else "Tutar", amount, { amount = it })
        SwitchLine("Tutar her ay değişiyor", "Faturalar gibi; öderken gerçek tutarı girersiniz", variable) { variable = it }
        SelectField("Kategori", categories.firstOrNull { it.id == categoryId } ?: categories.firstOrNull(), categories, { it?.name ?: "" }, { categoryId = it?.id ?: Seeds.CAT_BILLS })
        FormLabel("Sıklık")
        ChipRow(Frequency.entries, frequency, { it.label }, { frequency = it })
        when (frequency) {
            Frequency.WEEKLY -> {
                FormLabel("Gün")
                ChipRow((1..7).toList(), dow, { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, com.butcemiz.app.core.TR) }, { dow = it })
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { v -> day = v.filter { it.isDigit() }.take(2) },
                        label = { Text("Ayın günü (1-31)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    )
                    if (frequency == Frequency.YEARLY) {
                        SelectField("Ay", month, (1..12).toList(), { java.time.Month.of(it).getDisplayName(TextStyle.FULL, com.butcemiz.app.core.TR) }, { month = it }, Modifier.weight(1f))
                    }
                }
            }
        }
        val active2 = accounts.filter { !it.account.archived }
        if (active2.size > 1) {
            FormLabel("Ödenen hesap")
            ChipRow(active2, active2.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        FormLabel("Kaç gün önce hatırlatılsın?")
        ChipRow(listOf(0, 1, 2, 3, 5, 7), remind, { if (it == 0) "Aynı gün" else "$it gün" }, { remind = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Bitiş: " + (endDate?.let { Dates.dayMonthYear(it) } ?: "yok (süresiz)"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickEnd = true }) { Text("Seç") }
            if (endDate != null) TextButton(onClick = { endDate = null }) { Text("Kaldır") }
        }
        if (initial != null) {
            SwitchLine("Aktif", "Pasif ödemeler hatırlatılmaz", active) { active = it }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Bu düzenli ödemeyi sil", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (pickEnd) DatePickerModal(endDate ?: Dates.today().plusMonths(12), onDismiss = { pickEnd = false }, onPick = { endDate = it })
    if (confirmDelete && initial != null) {
        ConfirmDialog("Silinsin mi?", "\"${initial.name}\" iki telefondan da silinecek. Geçmiş ödemeler hareketlerde kalır.", onConfirm = { onDelete(initial) }, onDismiss = { confirmDelete = false })
    }
}

// ======================================================================
//                           GELİRLER & MAAŞ
// ======================================================================

@Composable
fun IncomesScreen(nav: NavController) {
    val vm = appViewModel { PlanViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    var editing by remember { mutableStateOf<IncomeSource?>(null) }
    var creating by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<IncomeSource?>(null) }
    val today = Dates.today()
    val monthIncome = s.monthTxns.filter { it.type == TxType.INCOME }

    ScreenScaffold(
        title = "Maaş ve gelirler",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Gelir kaynağı ekle") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Beklenen aylık gelir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(money(s.incomes.filter { it.active }.sumOf { it.amount }, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 22.sp))
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Bu dönem gelen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+" + money(monthIncome.sumOf { it.amount }, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 22.sp), color = sem.income)
                        }
                    }
                }
            }
            if (s.incomes.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.Work,
                            "Maaşınızı ekleyin",
                            "Maaş tutarı ve yatma gününü girin. Maaş günü bildirim gelir; \"Yattı\" deyince bakiyeye eklenir. Zam olunca tutarı güncellemeniz yeterli.",
                            action = "Maaş ekle",
                            onAction = { creating = true },
                        )
                    }
                }
            } else {
                item { SectionHeader("Düzenli gelirler") }
                items(s.incomes, key = { it.id }) { src ->
                    val st = Calc.paydayStatus(src, s.payments + s.monthTxns, today)
                    val member = s.memberMap[src.memberId]
                    AppCard(onClick = { editing = src }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (member != null) MemberAvatar(member, size = 40.dp) else IconBadge(Icons.Rounded.Work, sem.income)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(member?.name, src.kind.label, "her ayın ${src.payDay}. günü").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(money(src.amount, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 17.sp), color = sem.income)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (st.received != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = sem.income, modifier = Modifier.padding(end = 4.dp))
                                        Text("Bu ay yattı · ${money(st.received.amount)}", style = MaterialTheme.typography.labelMedium, color = sem.income)
                                    }
                                } else {
                                    val d = Dates.daysUntil(st.due, today)
                                    Text(
                                        when {
                                            d > 0 -> "Bu ay ${Dates.dayMonth(st.due)} (${d} gün sonra)"
                                            d == 0L -> "Bugün maaş günü"
                                            else -> "${Dates.dayMonth(st.due)} itibarıyla bekleniyor"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (d <= 0) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (src.amountUpdatedAt > 0) {
                                    Text("Tutar güncellendi: ${Dates.dayMonthYear(Dates.toDate(src.amountUpdatedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (st.received == null && src.active) {
                                FilledTonalButton(onClick = { recording = src }) { Text("Yattı") }
                            }
                        }
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { nav.openAdd(TxType.INCOME) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ek gelir ekle (tek seferlik)")
                }
            }
            if (monthIncome.isNotEmpty()) {
                item { SectionHeader("Bu dönemin gelirleri") }
                item {
                    AppCard(padding = PaddingValues(6.dp)) {
                        monthIncome.forEach { t ->
                            TxnRow(t, s.categoryMap[t.categoryId], s.memberMap[t.memberId], null, showTime = false, onClick = { nav.openAdd(t.type, t.id) })
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        IncomeSourceSheet(
            initial = editing,
            members = s.members,
            accounts = s.accounts,
            myMember = vm.myMember,
            onDismiss = { creating = false; editing = null },
            onDelete = { vm.deleteIncome(it); editing = null; toast("Silindi", null, null) },
        ) { src ->
            vm.saveIncome(src)
            creating = false
            editing = null
            toast("Kaydedildi · eşinizin telefonunda da güncellendi", null, null)
        }
    }
    recording?.let { src ->
        RecordIncomeSheet(src, s.accounts, onDismiss = { recording = null }) { amount, acc, date ->
            val st = Calc.paydayStatus(src, s.payments, today)
            vm.recordIncome(src, st.periodKey, amount, acc, date)
            recording = null
            toast("${src.name} eklendi · +${Money.format(amount)}", null, null)
        }
    }
}

@Composable
private fun RecordIncomeSheet(src: IncomeSource, accounts: List<AccountBalance>, onDismiss: () -> Unit, onSave: (Long, String?, LocalDate) -> Unit) {
    var amount by remember { mutableStateOf(Money.toEditable(src.amount)) }
    var accountId by remember { mutableStateOf(src.accountId ?: Seeds.MAIN_ACCOUNT_ID) }
    var date by remember { mutableStateOf(Dates.today()) }
    FormSheet(title = "${src.name} yattı", onDismiss = onDismiss, saveLabel = "Bakiyeye ekle", canSave = amountToKurus(amount) > 0, onSave = { onSave(amountToKurus(amount), accountId, date) }) {
        AmountTextField("Yatan tutar", amount, { amount = it }, supporting = "Bu ay farklı geldiyse (prim, kesinti) düzeltin")
        val active = accounts.filter { !it.account.archived }
        if (active.size > 1) {
            FormLabel("Hangi hesaba?")
            ChipRow(active, active.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        FormLabel("Tarih")
        DateSelector(date, { date = it })
    }
}

@Composable
private fun IncomeSourceSheet(
    initial: IncomeSource?,
    members: List<Member>,
    accounts: List<AccountBalance>,
    myMember: String?,
    onDismiss: () -> Unit,
    onDelete: (IncomeSource) -> Unit,
    onSave: (IncomeSource) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "Maaş") }
    var kind by remember { mutableStateOf(initial?.kind ?: IncomeKind.SALARY) }
    var member by remember { mutableStateOf(initial?.memberId ?: myMember) }
    var amount by remember { mutableStateOf(initial?.amount?.let { Money.toEditable(it) } ?: "") }
    var payDay by remember { mutableStateOf((initial?.payDay ?: 15).toString()) }
    var accountId by remember { mutableStateOf(initial?.accountId ?: Seeds.MAIN_ACCOUNT_ID) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    FormSheet(
        title = if (initial == null) "Yeni düzenli gelir" else "Geliri düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank() && amountToKurus(amount) > 0,
        onSave = {
            onSave(
                (initial ?: IncomeSource(id = "", name = "")).copy(
                    name = name.trim(),
                    kind = kind,
                    memberId = member,
                    amount = amountToKurus(amount),
                    payDay = (payDay.toIntOrNull() ?: 1).coerceIn(1, 31),
                    accountId = accountId,
                    categoryId = Seeds.incomeCategoryFor(kind),
                    active = active,
                ),
            )
        },
    ) {
        FormLabel("Tür")
        ChipRow(IncomeKind.entries, kind, { it.label }, {
            kind = it
            if (initial == null) name = it.label
        })
        NameField("Ad (ör. Maaş - PAÜ)", name) { name = it }
        AmountTextField("Aylık net tutar", amount, { amount = it }, supporting = "Zam gelince buradan güncelleyin")
        OutlinedTextField(
            value = payDay,
            onValueChange = { v -> payDay = v.filter { it.isDigit() }.take(2) },
            label = { Text("Yatma günü (ayın kaçı?)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        if (members.size > 1) {
            FormLabel("Kimin geliri?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { m -> FilterChip(selected = member == m.id, onClick = { member = m.id }, label = { Text(m.name) }, leadingIcon = { MemberAvatar(m, size = 20.dp) }) }
            }
        }
        val act = accounts.filter { !it.account.archived }
        if (act.size > 1) {
            FormLabel("Yattığı hesap")
            ChipRow(act, act.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        if (initial != null) {
            SwitchLine("Aktif", null, active) { active = it }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("Bu geliri sil", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete && initial != null) {
        ConfirmDialog("Silinsin mi?", "Geçmişte eklenen gelir hareketleri silinmez.", onConfirm = { onDelete(initial) }, onDismiss = { confirmDelete = false })
    }
}

// ======================================================================
//                              BÜTÇELER
// ======================================================================

@Composable
fun BudgetsScreen(nav: NavController) {
    val vm = appViewModel { PlanViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    var editing by remember { mutableStateOf<Pair<String?, Long>?>(null) }
    val expense = s.monthTxns.filter { it.type == TxType.EXPENSE }
    val total = s.budgets.firstOrNull { it.budget.categoryId == null }

    ScreenScaffold(
        title = "Bütçe limitleri",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { editing = "" to 0L }) { Icon(Icons.Rounded.Add, "Kategori bütçesi ekle") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "${s.month?.let { Periods.label(it) } ?: ""} dönemi. Limitin %80'ine ve %100'üne ulaşıldığında iki telefona da uyarı gelir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AppCard(onClick = { editing = null to (total?.budget?.amount ?: 0L) }) {
                    Text("Aylık toplam bütçe", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (total == null) {
                        Text("Belirlenmedi · dokunarak ayarlayın", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Bu dönem harcanan: ${money(expense.sumOf { it.amount }, Money.Decimals.NEVER)}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        BudgetProgress(total, sem)
                    }
                }
            }
            val catBudgets = s.budgets.filter { it.budget.categoryId != null }
            if (catBudgets.isNotEmpty()) {
                item { SectionHeader("Kategori bütçeleri") }
                items(catBudgets, key = { it.budget.id }) { b ->
                    val cat = s.categoryMap[b.budget.categoryId]
                    AppCard(onClick = { editing = b.budget.categoryId to b.budget.amount }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcons.of(cat?.icon), cat?.let { colorOf(it.color) } ?: MaterialTheme.colorScheme.primary, size = 36.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(cat?.name ?: "Kategori", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        BudgetProgress(b, sem)
                    }
                }
            } else {
                item {
                    AppCard {
                        EmptyState(Icons.Rounded.PieChart, "Kategori bütçesi yok", "Ör. Market için aylık 12.000 ₺, Yeme-içme için 4.000 ₺ belirleyin.", action = "Kategori bütçesi ekle", onAction = { editing = "" to 0L })
                    }
                }
            }
        }
    }

    editing?.let { (catId, current) ->
        BudgetSheet(
            categoryId = catId,
            current = current,
            categories = s.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived },
            onDismiss = { editing = null },
        ) { cat, amount ->
            vm.setBudget(cat, amount)
            editing = null
            toast(if (amount > 0) "Bütçe kaydedildi" else "Bütçe kaldırıldı", null, null)
        }
    }
}

@Composable
private fun BudgetProgress(b: BudgetView, sem: com.butcemiz.app.ui.theme.SemanticColors) {
    val color = when {
        b.progress >= 1f -> sem.expense
        b.progress >= 0.8f -> sem.warning
        else -> sem.income
    }
    ProgressBar(b.progress, color, height = 10.dp)
    Spacer(Modifier.height(6.dp))
    Row {
        Text("${money(b.spent, Money.Decimals.NEVER)} / ${money(b.budget.amount, Money.Decimals.NEVER)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            if (b.remaining >= 0) "Kalan ${money(b.remaining, Money.Decimals.NEVER)}" else "Aşım ${money(-b.remaining, Money.Decimals.NEVER)}",
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun BudgetSheet(categoryId: String?, current: Long, categories: List<Category>, onDismiss: () -> Unit, onSave: (String?, Long) -> Unit) {
    val isTotal = categoryId == null
    var cat by remember { mutableStateOf(categoryId?.takeIf { it.isNotEmpty() } ?: categories.firstOrNull()?.id) }
    var amount by remember { mutableStateOf(if (current > 0) Money.toEditable(current) else "") }
    FormSheet(
        title = if (isTotal) "Aylık toplam bütçe" else "Kategori bütçesi",
        onDismiss = onDismiss,
        saveLabel = "Kaydet",
        onSave = { onSave(if (isTotal) null else cat, amountToKurus(amount)) },
    ) {
        if (!isTotal) {
            SelectField("Kategori", categories.firstOrNull { it.id == cat }, categories, { it?.name ?: "" }, { cat = it?.id })
        }
        AmountTextField("Aylık limit", amount, { amount = it }, supporting = "Kaldırmak için boş bırakıp kaydedin")
    }
}
