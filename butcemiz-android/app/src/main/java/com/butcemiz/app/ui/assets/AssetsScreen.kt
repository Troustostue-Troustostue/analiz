package com.butcemiz.app.ui.assets

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AccountBalance
import com.butcemiz.app.data.AccountType
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.DebtView
import com.butcemiz.app.data.Frequency
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.PriceEntry
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.SavingGroup
import com.butcemiz.app.data.SavingView
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.AppIcons
import com.butcemiz.app.ui.components.EmptyState
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.Pill
import com.butcemiz.app.ui.components.ProgressBar
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.shell.LocalToast
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetsState(
    val balances: List<AccountBalance> = emptyList(),
    val savings: List<SavingView> = emptyList(),
    val debts: List<DebtView> = emptyList(),
    val prices: Map<String, PriceEntry> = emptyMap(),
    val members: List<Member> = emptyList(),
) {
    val total: Long get() = Calc.total(balances)
    val savingsTotal: Long get() = savings.filter { !it.saving.archived }.sumOf { it.value }
    val debtTotal: Long get() = debts.filter { it.debt.direction == DebtDirection.I_OWE && !it.debt.closed }.sumOf { it.remaining }
    val receivableTotal: Long get() = debts.filter { it.debt.direction == DebtDirection.OWED_TO_ME && !it.debt.closed }.sumOf { it.remaining }
    val net: Long get() = total + savingsTotal + receivableTotal - debtTotal
}

class AssetsViewModel(private val c: AppContainer) : ViewModel() {
    val tab = MutableStateFlow(0)
    var refreshing by mutableStateOf(false)
        private set

    val state: StateFlow<AssetsState> = combine(c.repo.accountBalances, c.repo.savings, c.repo.debts, c.repo.prices, c.repo.members) { b, s, d, p, m ->
        AssetsState(b, s, d, p, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsState())

    init {
        viewModelScope.launch { runCatching { c.rates.refreshIfStale() } }
    }

    fun refreshRates(onResult: (String) -> Unit) = viewModelScope.launch {
        refreshing = true
        val r = c.rates.refresh()
        refreshing = false
        onResult(r.fold({ "Kurlar güncellendi" }, { "Kurlar alınamadı: ${it.message ?: "bağlantı yok"}" }))
    }

    fun saveAccount(a: Account) = viewModelScope.launch { c.repo.saveAccount(a) }
    fun saveSaving(s: Saving) = viewModelScope.launch { c.repo.saveSaving(s) }

    fun saveDebt(r: DebtFormResult) = viewModelScope.launch {
        val saved = c.repo.saveDebt(r.debt, r.moneyAccountId)
        val inst = saved.installmentAmount
        val day = saved.dueDay
        if (r.addInstallments && inst != null && inst > 0 && day != null) {
            val first = Dates.nextMonthlyDate(day)
            val paidCount = if (inst > 0) (saved.alreadyPaid / inst).toInt() else 0
            val remainingCount = saved.installmentCount?.let { (it - paidCount).coerceAtLeast(1) }
            val end = remainingCount?.let { Dates.startOfDay(first.plusMonths((it - 1).toLong()).plusDays(1)) - 1 }
            c.repo.saveRecurring(
                Recurring(
                    id = "",
                    name = "${saved.name} taksidi",
                    amount = inst,
                    categoryId = Seeds.CAT_DEBT,
                    accountId = Seeds.MAIN_ACCOUNT_ID,
                    frequency = Frequency.MONTHLY,
                    dayOfMonth = day,
                    startDate = System.currentTimeMillis(),
                    endDate = end,
                    remindDaysBefore = 3,
                    debtId = saved.id,
                    ownerMemberId = saved.ownerMemberId,
                ),
            )
        }
    }
}

@Composable
fun AssetsScreen(nav: NavController) {
    val vm = appViewModel { AssetsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    var addAccount by remember { mutableStateOf(false) }
    var addSaving by remember { mutableStateOf(false) }
    var addDebt by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Varlıklar") },
                actions = {
                    if (tab == 1) {
                        if (vm.refreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        } else {
                            IconButton(onClick = { vm.refreshRates { toast(it, null, null) } }) { Icon(Icons.Rounded.Refresh, "Kurları güncelle") }
                        }
                    }
                    IconButton(onClick = {
                        when (tab) {
                            0 -> addAccount = true
                            1 -> addSaving = true
                            else -> addDebt = true
                        }
                    }) { Icon(Icons.Rounded.Add, "Ekle") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NetWorthCard(s) }
            item {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Hesaplar", "Birikimler", "Borçlar").forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { vm.tab.value = i },
                            text = { Text(t) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (tab) {
                0 -> {
                    val active = s.balances.filter { !it.account.archived }
                    val archived = s.balances.filter { it.account.archived }
                    items(active, key = { it.account.id }) { ab -> AccountCard(ab) { nav.navigate("account/${ab.account.id}") } }
                    item {
                        FilledTonalButton(onClick = { addAccount = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Hesap ekle (banka, nakit, kredi kartı)")
                        }
                    }
                    if (archived.isNotEmpty()) {
                        item { SectionHeader("Arşivlenen hesaplar") }
                        items(archived, key = { it.account.id }) { ab -> AccountCard(ab) { nav.navigate("account/${ab.account.id}") } }
                    }
                }
                1 -> {
                    item { RatesRow(s.prices) }
                    val active = s.savings.filter { !it.saving.archived }
                    if (active.isEmpty()) {
                        item {
                            AppCard {
                                EmptyState(
                                    Icons.Rounded.Savings,
                                    "Birikim ekleyin",
                                    "Altın (gram, çeyrek, bilezik…), döviz, mevduat, fon ya da hisse; güncel kurlarla TL karşılığı otomatik hesaplanır.",
                                    action = "Birikim ekle",
                                    onAction = { addSaving = true },
                                )
                            }
                        }
                    } else {
                        val groups = active.groupBy { it.saving.type.group }
                        SavingGroup.entries.forEach { g ->
                            val list = groups[g] ?: return@forEach
                            item(key = "g_" + g.name) {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(g.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(money(list.sumOf { it.value }, Money.Decimals.NEVER), style = MaterialTheme.typography.labelLarge, color = sem.gold)
                                }
                            }
                            items(list, key = { it.saving.id }) { sv -> SavingCard(sv) { nav.navigate("saving/${sv.saving.id}") } }
                        }
                        item {
                            FilledTonalButton(onClick = { addSaving = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Birikim ekle")
                            }
                        }
                    }
                    val archived = s.savings.filter { it.saving.archived }
                    if (archived.isNotEmpty()) {
                        item { SectionHeader("Arşiv") }
                        items(archived, key = { it.saving.id }) { sv -> SavingCard(sv) { nav.navigate("saving/${sv.saving.id}") } }
                    }
                }
                else -> {
                    val owe = s.debts.filter { it.debt.direction == DebtDirection.I_OWE }
                    val owed = s.debts.filter { it.debt.direction == DebtDirection.OWED_TO_ME }
                    if (s.debts.isEmpty()) {
                        item {
                            AppCard {
                                EmptyState(
                                    Icons.Rounded.CreditCard,
                                    "Borç ve alacak takibi",
                                    "Kredi, kredi kartı, taksitli alışveriş ya da arkadaşa verilen borçları ekleyin; ödedikçe kalan tutar otomatik düşer.",
                                    action = "Borç / alacak ekle",
                                    onAction = { addDebt = true },
                                )
                            }
                        }
                    }
                    if (owe.isNotEmpty()) {
                        item { SectionHeader("Borçlarım") }
                        items(owe, key = { it.debt.id }) { d -> DebtCard(d) { nav.navigate("debt/${d.debt.id}") } }
                    }
                    if (owed.isNotEmpty()) {
                        item { SectionHeader("Alacaklarım") }
                        items(owed, key = { it.debt.id }) { d -> DebtCard(d) { nav.navigate("debt/${d.debt.id}") } }
                    }
                    if (s.debts.isNotEmpty()) {
                        item {
                            FilledTonalButton(onClick = { addDebt = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Borç / alacak ekle")
                            }
                        }
                    }
                }
            }
        }
    }

    if (addAccount) AccountEditorSheet(null, onDismiss = { addAccount = false }) { vm.saveAccount(it); addAccount = false; toast("Hesap eklendi", null, null) }
    if (addSaving) SavingEditorSheet(null, s.members, onDismiss = { addSaving = false }) { vm.saveSaving(it); addSaving = false; toast("Birikim eklendi", null, null) }
    if (addDebt) DebtEditorSheet(null, s.balances, s.members, onDismiss = { addDebt = false }) { vm.saveDebt(it); addDebt = false; toast("Kaydedildi", null, null) }
}

@Composable
private fun NetWorthCard(s: AssetsState) {
    val sem = LocalSemantic.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(sem.heroBrush).padding(18.dp),
    ) {
        Column {
            Text("Net varlık", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
            Text(money(s.net, Money.Decimals.NEVER), color = Color.White, style = AmountStyle.copy(fontSize = 32.sp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                WhiteStat("Hesaplar", money(s.total, Money.Decimals.NEVER), Modifier.weight(1f))
                WhiteStat("Birikim", money(s.savingsTotal, Money.Decimals.NEVER), Modifier.weight(1f))
                WhiteStat("Borç", money(s.debtTotal, Money.Decimals.NEVER), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WhiteStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = AmountStyle.copy(fontSize = 14.sp), maxLines = 1)
    }
}

@Composable
fun AccountCard(ab: AccountBalance, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val a = ab.account
    AppCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.account(a.type), colorOf(a.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    a.type.label + if (!a.includeInTotal) " · toplama dahil değil" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(ab.balance), style = AmountStyle.copy(fontSize = 17.sp), color = if (ab.balance < 0) sem.expense else MaterialTheme.colorScheme.onSurface)
                if (a.type == AccountType.CREDIT_CARD && a.creditLimit > 0) {
                    Text("Kalan limit ${money(a.creditLimit + ab.balance, Money.Decimals.NEVER)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RatesRow(prices: Map<String, PriceEntry>) {
    val keys = listOf("USD" to "Dolar", "EUR" to "Euro", "gram-altin" to "Gram altın", "ceyrek-altin" to "Çeyrek", "22-ayar-bilezik" to "22 ayar", "gumus" to "Gümüş")
    val avail = keys.mapNotNull { (k, label) -> prices[k]?.let { Triple(label, it, k) } }
    if (avail.isEmpty()) {
        Text("Güncel kurlar için sağ üstteki yenile düğmesine dokunun.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val updated = avail.maxOf { it.second.updatedAt }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(avail, key = { it.third }) { (label, p, _) ->
                val ch = p.change
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(Money.fromLira(p.buying)), style = AmountStyle.copy(fontSize = 14.sp))
                    if (ch != null) {
                        Text(
                            (if (ch >= 0) "▲ %" else "▼ %") + String.format(com.butcemiz.app.core.TR, "%.2f", kotlin.math.abs(ch)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ch >= 0) LocalSemantic.current.income else LocalSemantic.current.expense,
                        )
                    }
                }
            }
        }
        Text(
            "Alış fiyatları · ${Dates.relative(updated)} güncellendi · ${avail.first().second.source}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SavingCard(sv: SavingView, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val s = sv.saving
    AppCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.savingGroup(s.type.group), sem.gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (s.type.unitIsLira) s.type.label else "${Money.quantity(sv.quantity)} ${s.type.unit} · ${s.type.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (sv.unitPrice == null) "Fiyat yok" else money(sv.value, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 17.sp))
                if (sv.unitPrice != null && !s.type.unitIsLira) {
                    Text("Birim ${money(sv.unitPrice)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val target = s.targetAmount
        if (target != null && target > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressBar(Money.percent(sv.value, target), sem.gold)
            Spacer(Modifier.height(4.dp))
            Text("Hedef ${money(target, Money.Decimals.NEVER)} · %${(Money.percent(sv.value, target) * 100).toInt().coerceAtMost(999)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DebtCard(dv: DebtView, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val d = dv.debt
    val owe = d.direction == DebtDirection.I_OWE
    val color = if (owe) sem.expense else sem.transfer
    AppCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (d.kind == com.butcemiz.app.data.DebtKind.CREDIT_CARD) Icons.Rounded.CreditCard else Icons.Rounded.Handshake, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(d.counterparty.takeIf { it.isNotBlank() }, d.kind.label).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (d.closed || dv.remaining == 0L) {
                Pill("Kapandı", sem.income)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(dv.remaining, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 17.sp), color = color)
                    Text("kalan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(dv.progress, sem.income)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("Ödenen ${money(dv.paid, Money.Decimals.NEVER)} / ${money(d.principal, Money.Decimals.NEVER)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val next = dv.nextDue
            if (next != null) {
                Text(
                    "Sıradaki: ${Dates.dueText(next)}" + (d.installmentAmount?.let { " · ${money(it, Money.Decimals.NEVER)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (Dates.daysUntil(next) <= 3) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
