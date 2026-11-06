package com.butcemiz.app.ui.assets

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.Debt
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.DebtView
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.SavingView
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.ConfirmDialog
import com.butcemiz.app.ui.components.LabeledAmount
import com.butcemiz.app.ui.components.ProgressBar
import com.butcemiz.app.ui.components.ScreenScaffold
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.TxnRow
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.shell.LocalToast
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class DetailRefs(
    val accounts: List<AccountBalance> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val members: List<Member> = emptyList(),
) {
    val accountMap: Map<String, Account> get() = accounts.associate { it.account.id to it.account }
    val memberMap: Map<String, Member> get() = members.associateBy { it.id }
}

class DetailViewModel(private val c: AppContainer, private val kind: String, private val id: String) : ViewModel() {
    val refs: StateFlow<DetailRefs> = combine(c.repo.accountBalances, c.repo.categories, c.repo.members) { a, cat, m ->
        DetailRefs(a, cat.associateBy { it.id }, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailRefs())

    val txns: StateFlow<List<Txn>> = when (kind) {
        "account" -> c.repo.txnsForAccount(id)
        "saving" -> c.repo.txnsForSaving(id)
        else -> c.repo.txnsForDebt(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savings: StateFlow<List<SavingView>> = c.repo.savings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debts: StateFlow<List<DebtView>> = c.repo.debts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myMember: String? get() = c.prefs.myMemberId

    fun saveAccount(a: Account) = viewModelScope.launch { c.repo.saveAccount(a) }
    fun removeAccount(a: Account, hasTxns: Boolean) = viewModelScope.launch { c.repo.removeAccount(a, hasTxns) }
    fun adjust(ab: AccountBalance, target: Long) = viewModelScope.launch { c.repo.adjustBalance(ab.account.id, ab.balance, target) }

    fun saveSaving(s: Saving) = viewModelScope.launch { c.repo.saveSaving(s) }
    fun deleteSaving(s: Saving) = viewModelScope.launch { c.repo.deleteSaving(s) }
    fun movement(s: Saving, deposit: Boolean, qty: Double, amount: Long, accountId: String?, date: LocalDate, note: String) = viewModelScope.launch {
        c.repo.savingMovement(s, deposit, qty, amount, accountId, c.prefs.myMemberId, millisFor(date), note)
    }

    fun saveDebt(d: Debt) = viewModelScope.launch { c.repo.saveDebt(d) }
    fun deleteDebt(d: Debt) = viewModelScope.launch { c.repo.deleteDebt(d) }
    fun pay(d: Debt, amount: Long, accountId: String?, date: LocalDate, note: String) = viewModelScope.launch {
        c.repo.payDebt(d, amount, accountId, c.prefs.myMemberId, millisFor(date), note)
    }

    private fun millisFor(d: LocalDate): Long =
        if (d == Dates.today()) System.currentTimeMillis() else Dates.millisOf(d, LocalTime.now().withNano(0))
}

@Composable
private fun TxnHistory(txns: List<Txn>, refs: DetailRefs, nav: NavController, emptyText: String) {
    if (txns.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        return
    }
    AppCard(padding = PaddingValues(6.dp)) {
        txns.forEach { t ->
            TxnRow(
                t = t,
                category = refs.categories[t.categoryId],
                member = refs.memberMap[t.memberId],
                account = refs.accountMap[t.accountId],
                toAccount = refs.accountMap[t.toAccountId],
                showTime = false,
                onClick = { nav.openAdd(t.type, t.id) },
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.padding(end = 4.dp))
        Text(label, maxLines = 1)
    }
}

// ================================ Hesap ================================

@Composable
fun AccountDetailScreen(nav: NavController, id: String) {
    val vm = appViewModel(key = "acc_$id") { DetailViewModel(it, "account", id) }
    val refs by vm.refs.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    val ab = refs.accounts.firstOrNull { it.account.id == id }
    var edit by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = ab?.account?.name ?: "Hesap",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { edit = true }) { Icon(Icons.Rounded.Edit, "Düzenle") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Diğer") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Bakiyeyi düzelt") }, onClick = { menu = false; adjust = true })
                if (ab != null && ab.account.archived) {
                    DropdownMenuItem(text = { Text("Arşivden çıkar") }, onClick = { menu = false; vm.saveAccount(ab.account.copy(archived = false)) })
                } else {
                    DropdownMenuItem(text = { Text(if (txns.isEmpty()) "Hesabı sil" else "Hesabı arşivle") }, onClick = { menu = false; confirmRemove = true })
                }
            }
        },
    ) { pad ->
        if (ab == null) return@ScreenScaffold
        val a = ab.account
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(a.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(ab.balance), style = AmountStyle.copy(fontSize = 34.sp), color = if (ab.balance < 0) sem.expense else MaterialTheme.colorScheme.onSurface)
                    if (a.type == AccountType.CREDIT_CARD && a.creditLimit > 0) {
                        val used = (-ab.balance).coerceAtLeast(0)
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(Money.percent(used, a.creditLimit), sem.expense)
                        Spacer(Modifier.height(4.dp))
                        Text("Kullanılan ${money(used, Money.Decimals.NEVER)} · Kalan limit ${money(a.creditLimit - used, Money.Decimals.NEVER)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!a.includeInTotal) {
                        Text("Toplam bakiyeye dahil değil", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Harcama", Icons.Rounded.Remove, Modifier.weight(1f)) { nav.openAdd(TxType.EXPENSE, accountId = id) }
                    ActionButton("Gelir", Icons.Rounded.Add, Modifier.weight(1f)) { nav.openAdd(TxType.INCOME, accountId = id) }
                    ActionButton("Transfer", Icons.Rounded.SwapHoriz, Modifier.weight(1f)) { nav.openAdd(TxType.TRANSFER, accountId = id) }
                }
            }
            item {
                OutlinedButton(onClick = { adjust = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Tune, null, Modifier.padding(end = 6.dp))
                    Text("Bakiyeyi gerçek tutara eşitle")
                }
            }
            item { SectionHeader("Hareketler") }
            item { TxnHistory(txns, refs, nav, "Bu hesapta henüz hareket yok.") }
        }
    }

    if (edit && ab != null) AccountEditorSheet(ab.account, onDismiss = { edit = false }) { vm.saveAccount(it); edit = false; toast("Hesap güncellendi", null, null) }
    if (adjust && ab != null) AdjustBalanceSheet(ab, onDismiss = { adjust = false }) { vm.adjust(ab, it); adjust = false; toast("Bakiye düzeltildi", null, null) }
    if (confirmRemove && ab != null) {
        ConfirmDialog(
            title = if (txns.isEmpty()) "Hesap silinsin mi?" else "Hesap arşivlensin mi?",
            text = if (txns.isEmpty()) "Bu hesap iki telefondan da silinecek." else "Hareketleri korunur; hesap seçim listelerinde görünmez.",
            confirm = if (txns.isEmpty()) "Sil" else "Arşivle",
            onConfirm = {
                vm.removeAccount(ab.account, txns.isNotEmpty())
                nav.popBackStack()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

// ================================ Birikim ================================

@Composable
fun SavingDetailScreen(nav: NavController, id: String) {
    val vm = appViewModel(key = "sav_$id") { DetailViewModel(it, "saving", id) }
    val refs by vm.refs.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val savings by vm.savings.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    val sv = savings.firstOrNull { it.saving.id == id }
    var edit by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<Boolean?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = sv?.saving?.name ?: "Birikim",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { edit = true }) { Icon(Icons.Rounded.Edit, "Düzenle") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Diğer") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (sv != null) {
                    DropdownMenuItem(
                        text = { Text(if (sv.saving.archived) "Arşivden çıkar" else "Arşivle") },
                        onClick = { menu = false; vm.saveSaving(sv.saving.copy(archived = !sv.saving.archived)) },
                    )
                }
                DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; confirmDelete = true })
            }
        },
    ) { pad ->
        if (sv == null) return@ScreenScaffold
        val s = sv.saving
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(s.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (sv.unitPrice == null) "Fiyat girilmedi" else money(sv.value), style = AmountStyle.copy(fontSize = 32.sp), color = sem.gold)
                    if (!s.type.unitIsLira) {
                        Text("${Money.quantity(sv.quantity)} ${s.type.unit}", style = MaterialTheme.typography.titleMedium)
                        val up = sv.unitPrice
                        Text(
                            if (up == null) "Birim fiyat için düzenleyin" else "Birim ${Money.format(up)}" + when {
                                s.manualUnitPrice != null -> " · elle girildi"
                                sv.priceUpdatedAt != null -> " · ${Dates.relative(sv.priceUpdatedAt)}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val totalCost = (s.costBasis ?: 0L) + sv.netInvested
                    if ((s.costBasis != null || sv.netInvested != 0L) && sv.unitPrice != null && totalCost > 0) {
                        val pl = sv.value - totalCost
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            LabeledAmount("Maliyet", money(totalCost, Money.Decimals.NEVER), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            LabeledAmount(
                                if (pl >= 0) "Kâr" else "Zarar",
                                money(pl, Money.Decimals.NEVER, sign = true) + " (%${(pl * 100 / totalCost)})",
                                if (pl >= 0) sem.income else sem.expense,
                                Modifier.weight(1f),
                            )
                        }
                    }
                    val target = s.targetAmount
                    if (target != null && target > 0) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(Money.percent(sv.value, target), sem.gold)
                        Spacer(Modifier.height(4.dp))
                        Text("Hedef ${money(target, Money.Decimals.NEVER)} · kalan ${money((target - sv.value).coerceAtLeast(0), Money.Decimals.NEVER)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Ekle", Icons.Rounded.Add, Modifier.weight(1f)) { move = true }
                    ActionButton("Çıkar / Sat", Icons.Rounded.Remove, Modifier.weight(1f)) { move = false }
                }
            }
            item { SectionHeader("Geçmiş") }
            item { TxnHistory(txns, refs, nav, "Henüz ekleme/çıkarma yok. Başlangıç miktarı: ${Money.quantity(s.initialQty)} ${s.type.unit}") }
        }
    }

    if (edit && sv != null) SavingEditorSheet(sv.saving, refs.members, onDismiss = { edit = false }) { vm.saveSaving(it); edit = false; toast("Birikim güncellendi", null, null) }
    val m = move
    if (m != null && sv != null) {
        SavingMovementSheet(
            sv.saving, m, sv.unitPrice, refs.accounts, com.butcemiz.app.data.Seeds.MAIN_ACCOUNT_ID,
            onDismiss = { move = null },
        ) { qty, amount, acc, date, note ->
            vm.movement(sv.saving, m, qty, amount, acc, date, note)
            move = null
            toast(if (m) "Birikime eklendi" else "Birikimden çıkarıldı", null, null)
        }
    }
    if (confirmDelete && sv != null) {
        ConfirmDialog(
            title = "Birikim silinsin mi?",
            text = "\"${sv.saving.name}\" iki telefondan da silinecek. Bağlı hareketler listede kalır.",
            onConfirm = { vm.deleteSaving(sv.saving); nav.popBackStack() },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ================================ Borç ================================

@Composable
fun DebtDetailScreen(nav: NavController, id: String) {
    val vm = appViewModel(key = "debt_$id") { DetailViewModel(it, "debt", id) }
    val refs by vm.refs.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val sem = LocalSemantic.current
    val dv = debts.firstOrNull { it.debt.id == id }
    var edit by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = dv?.debt?.name ?: "Borç",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { edit = true }) { Icon(Icons.Rounded.Edit, "Düzenle") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Diğer") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (dv != null) {
                    DropdownMenuItem(
                        text = { Text(if (dv.debt.closed) "Yeniden aç" else "Kapandı olarak işaretle") },
                        onClick = { menu = false; vm.saveDebt(dv.debt.copy(closed = !dv.debt.closed)) },
                    )
                }
                DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; confirmDelete = true })
            }
        },
    ) { pad ->
        if (dv == null) return@ScreenScaffold
        val d = dv.debt
        val owe = d.direction == DebtDirection.I_OWE
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(listOfNotNull(d.direction.label, d.kind.label, d.counterparty.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(dv.remaining), style = AmountStyle.copy(fontSize = 32.sp), color = if (owe) sem.expense else sem.transfer)
                    Text("kalan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(dv.progress, sem.income, height = 10.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Ödenen ${money(dv.paid, Money.Decimals.NEVER)} / ${money(d.principal, Money.Decimals.NEVER)} · %${(dv.progress * 100).toInt()}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        d.installmentAmount?.let { LabeledAmount("Taksit", money(it, Money.Decimals.NEVER), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) }
                        d.installmentCount?.let { n ->
                            val paidCount = d.installmentAmount?.takeIf { it > 0 }?.let { (dv.paid / it).toInt() }
                            LabeledAmount("Taksit sayısı", if (paidCount != null) "$paidCount / $n" else "$n", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                        }
                        dv.nextDue?.let { LabeledAmount("Sıradaki ödeme", Dates.dueText(it), if (Dates.daysUntil(it) <= 3) sem.warning else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) }
                    }
                    d.interestRate?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Faiz oranı: %${Money.quantity(it, 2)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (d.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(d.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!d.closed && dv.remaining > 0) {
                item {
                    ActionButton(if (owe) "Ödeme yap" else "Tahsilat ekle", Icons.Rounded.Payments, Modifier.fillMaxWidth()) { pay = true }
                }
            }
            item { SectionHeader(if (owe) "Ödemeler" else "Tahsilatlar") }
            item { TxnHistory(txns, refs, nav, "Henüz kayıt yok.") }
        }
    }

    if (edit && dv != null) {
        DebtEditorSheet(dv.debt, refs.accounts, refs.members, onDismiss = { edit = false }) { r -> vm.saveDebt(r.debt); edit = false; toast("Güncellendi", null, null) }
    }
    if (pay && dv != null) {
        DebtPaymentSheet(dv.debt, dv.remaining, refs.accounts, onDismiss = { pay = false }) { amount, acc, date, note ->
            vm.pay(dv.debt, amount, acc, date, note)
            pay = false
            toast(if (dv.debt.direction == DebtDirection.I_OWE) "Ödeme kaydedildi" else "Tahsilat kaydedildi", null, null)
        }
    }
    if (confirmDelete && dv != null) {
        ConfirmDialog(
            title = "Silinsin mi?",
            text = "\"${dv.debt.name}\" iki telefondan da silinecek. Bağlı ödeme hareketleri listede kalır.",
            onConfirm = { vm.deleteDebt(dv.debt); nav.popBackStack() },
            onDismiss = { confirmDelete = false },
        )
    }
}
