@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.EmptyState
import com.butcemiz.app.ui.components.LabeledAmount
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.TxnRow
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.components.signedAmount
import com.butcemiz.app.ui.components.txColor
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.shell.LocalToast
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TypeFilter(val label: String) { ALL("Tümü"), EXPENSE("Giderler"), INCOME("Gelirler"), OTHER("Diğer") }

data class DayGroup(val date: LocalDate, val items: List<Txn>, val expense: Long, val income: Long)

data class TxnListState(
    val period: Period? = null,
    val groups: List<DayGroup> = emptyList(),
    val flat: List<Txn> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val members: Map<String, Member> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val memberList: List<Member> = emptyList(),
    val categoryList: List<Category> = emptyList(),
)

class TransactionsViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repo
    val kind = MutableStateFlow(PeriodKind.MONTH)
    val anchor = MutableStateFlow(Dates.today())
    val typeFilter = MutableStateFlow(TypeFilter.ALL)
    val memberFilter = MutableStateFlow<String?>(null)
    val categoryFilter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    val table = MutableStateFlow(false)

    val period: StateFlow<Period> = combine(kind, anchor, repo.monthStartDay) { k, a, sd -> Periods.of(k, a, sd) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Periods.month(Dates.today()))

    private data class Filters(val type: TypeFilter, val member: String?, val category: String?, val q: String)

    private val filters = combine(typeFilter, memberFilter, categoryFilter, query) { t, m, cat, q -> Filters(t, m, cat, q) }
    private data class Refs(val members: List<Member>, val cats: List<Category>, val accs: List<Account>)
    private val refs = combine(repo.members, repo.categories, repo.accounts) { m, cat, a -> Refs(m, cat, a) }

    val state: StateFlow<TxnListState> = combine(period.flatMapLatest { repo.txnsBetween(it) }, filters, refs, period) { list, f, r, p ->
        val cats = r.cats.associateBy { it.id }
        val q = f.q.trim()
        val filtered = list.filter { t ->
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> t.type == TxType.EXPENSE
                TypeFilter.INCOME -> t.type == TxType.INCOME
                TypeFilter.OTHER -> t.type != TxType.EXPENSE && t.type != TxType.INCOME
            }
            typeOk && (f.member == null || t.memberId == f.member) &&
                (f.category == null || t.categoryId == f.category) &&
                (q.isEmpty() || t.title.contains(q, true) || t.note.contains(q, true) || (cats[t.categoryId]?.name?.contains(q, true) == true))
        }
        val groups = filtered.groupBy { Dates.toDate(it.occurredAt) }.map { (d, items) ->
            DayGroup(d, items, items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }, items.filter { it.type == TxType.INCOME }.sumOf { it.amount })
        }.sortedByDescending { it.date }
        TxnListState(
            period = p,
            groups = groups,
            flat = filtered,
            income = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            expense = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            members = r.members.associateBy { it.id },
            categories = cats,
            accounts = r.accs.associateBy { it.id },
            memberList = r.members,
            categoryList = r.cats,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnListState())

    fun shift(n: Int) {
        val p = period.value
        anchor.value = when (p.kind) {
            PeriodKind.WEEK -> p.start.plusWeeks(n.toLong())
            PeriodKind.MONTH -> p.start.plusMonths(n.toLong())
            PeriodKind.YEAR -> p.start.plusYears(n.toLong())
        }
    }

    fun exportCsv(uri: android.net.Uri, onDone: (Int) -> Unit) = viewModelScope.launch {
        val p = period.value
        val n = runCatching { c.backup.exportCsv(uri, p.startMillis, p.endMillis) }.getOrDefault(-1)
        onDone(n)
    }
}

@Composable
fun TransactionsScreen(nav: NavController) {
    val vm = appViewModel { TransactionsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val member by vm.memberFilter.collectAsStateWithLifecycle()
    val category by vm.categoryFilter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val table by vm.table.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    val toast = LocalToast.current
    val sem = LocalSemantic.current

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri) { n -> toast(if (n >= 0) "$n hareket CSV olarak kaydedildi" else "Dışa aktarılamadı", null, null) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Hareketler") },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) { Icon(Icons.Rounded.Search, "Ara") }
                    IconButton(onClick = { vm.table.value = !table }) {
                        Icon(if (table) Icons.Rounded.ViewList else Icons.Rounded.TableChart, if (table) "Liste görünümü" else "Tablo görünümü")
                    }
                    IconButton(onClick = {
                        val p = s.period ?: return@IconButton
                        csvLauncher.launch("butcemiz_${p.start}_${p.endExclusive.minusDays(1)}.csv")
                    }) { Icon(Icons.Rounded.FileDownload, "CSV olarak dışa aktar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.openAdd(TxType.EXPENSE) }, shape = RoundedCornerShape(20.dp)) { Icon(Icons.Rounded.Add, "Ekle") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searching) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("Açıklama, not veya kategori ara") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Rounded.Close, "Temizle") } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
            item {
                PeriodBar(kind, s.period, onKind = { vm.kind.value = it; vm.anchor.value = Dates.today() }, onShift = vm::shift)
            }
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth()) {
                        LabeledAmount("Gelir", "+" + money(s.income, Money.Decimals.NEVER), sem.income, Modifier.weight(1f))
                        LabeledAmount("Gider", Money.MINUS + money(s.expense, Money.Decimals.NEVER), sem.expense, Modifier.weight(1f))
                        LabeledAmount("Net", money(s.income - s.expense, Money.Decimals.NEVER, sign = true), if (s.income >= s.expense) sem.income else sem.expense, Modifier.weight(1f))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TypeFilter.entries) { f ->
                        FilterChip(selected = type == f, onClick = { vm.typeFilter.value = f }, label = { Text(f.label) })
                    }
                    if (s.memberList.size > 1) {
                        items(s.memberList, key = { "m" + it.id }) { m ->
                            FilterChip(
                                selected = member == m.id,
                                onClick = { vm.memberFilter.value = if (member == m.id) null else m.id },
                                label = { Text(m.name) },
                                leadingIcon = { MemberAvatar(m, size = 20.dp) },
                            )
                        }
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = category != null,
                                onClick = { if (category != null) vm.categoryFilter.value = null else catMenu = true },
                                label = { Text(category?.let { s.categories[it]?.name } ?: "Kategori") },
                                trailingIcon = if (category != null) ({ Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }) else null,
                            )
                            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                s.categoryList.filter { !it.archived }.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat.name) }, onClick = { vm.categoryFilter.value = cat.id; catMenu = false })
                                }
                            }
                        }
                    }
                }
            }
            if (s.groups.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.ReceiptLong,
                        "Bu dönemde hareket yok",
                        if (query.isNotBlank() || type != TypeFilter.ALL || member != null || category != null) "Filtreleri değiştirerek tekrar deneyin." else "Eklediğiniz harcama ve gelirler burada listelenir.",
                    )
                }
            } else if (table) {
                item { TableHeader() }
                items(s.flat, key = { "t" + it.id }) { t ->
                    TableRow(t, s, onClick = { nav.openAdd(t.type, t.id) })
                }
            } else {
                s.groups.forEach { g ->
                    item(key = "h" + g.date) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Dates.dayHeader(g.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (g.income > 0) Text("+" + money(g.income, Money.Decimals.NEVER) + "  ", style = MaterialTheme.typography.labelMedium, color = sem.income)
                            if (g.expense > 0) Text(Money.MINUS + money(g.expense, Money.Decimals.NEVER), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "g" + g.date) {
                        AppCard(padding = PaddingValues(6.dp)) {
                            g.items.forEach { t ->
                                TxnRow(
                                    t = t,
                                    category = s.categories[t.categoryId],
                                    member = s.members[t.memberId],
                                    account = s.accounts[t.accountId],
                                    toAccount = s.accounts[t.toAccountId],
                                    onClick = { nav.openAdd(t.type, t.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodBar(kind: PeriodKind, period: Period?, onKind: (PeriodKind) -> Unit, onShift: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val opts = listOf(PeriodKind.WEEK to "Hafta", PeriodKind.MONTH to "Ay", PeriodKind.YEAR to "Yıl")
            opts.forEachIndexed { i, (k, label) ->
                SegmentedButton(selected = kind == k, onClick = { onKind(k) }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(label) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Önceki") }
            Text(
                period?.let { Periods.label(it) } ?: "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            val isCurrent = period?.containsDate(Dates.today()) == true
            IconButton(onClick = { onShift(1) }, enabled = !isCurrent) { Icon(Icons.Rounded.ChevronRight, "Sonraki") }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val st = MaterialTheme.typography.labelMedium
        Text("Tarih", style = st, modifier = Modifier.width(48.dp))
        Text("Açıklama · Kategori", style = st, modifier = Modifier.weight(1f))
        Text("Kişi", style = st, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        Text("Tutar", style = st, modifier = Modifier.width(100.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TableRow(t: Txn, s: TxnListState, onClick: () -> Unit) {
    val d = Dates.toDate(t.occurredAt)
    val cat = s.categories[t.categoryId]
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d.%02d".format(d.dayOfMonth, d.monthValue), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title.ifBlank { cat?.name ?: t.type.label }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cat != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colorOf(cat.color)))
                Spacer(Modifier.width(4.dp))
                Text(cat?.name ?: t.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { MemberAvatar(s.members[t.memberId], size = 24.dp) }
        Text(
            signedAmount(t),
            style = AmountStyle.copy(fontSize = 14.sp),
            color = if (t.type == TxType.EXPENSE) MaterialTheme.colorScheme.onSurface else txColor(t.type),
            modifier = Modifier.width(100.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
