package com.butcemiz.app.ui.transactions

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.butcemiz.app.AppContainer
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.AccountBalance
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.CategoryKind
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.data.TitleSuggestion
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.ui.components.AmountTextField
import com.butcemiz.app.ui.components.AppIcons
import com.butcemiz.app.ui.components.BigAmountField
import com.butcemiz.app.ui.components.ConfirmDialog
import com.butcemiz.app.ui.components.DateSelector
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.amountToKurus
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.quantityOf
import com.butcemiz.app.ui.components.quantityToEditable
import com.butcemiz.app.ui.components.txColor
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.shell.LocalToast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class EditTxnViewModel(
    private val c: AppContainer,
    initialType: TxType,
    private val editId: String?,
    presetAccount: String?,
) : ViewModel() {
    var loaded by mutableStateOf(editId == null)
    var type by mutableStateOf(initialType)
    var amount by mutableStateOf("")
    var title by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var accountId by mutableStateOf<String?>(presetAccount ?: c.prefs.lastAccount(initialType) ?: Seeds.MAIN_ACCOUNT_ID)
    var toAccountId by mutableStateOf<String?>(null)
    var memberId by mutableStateOf(c.prefs.myMemberId)
    var date by mutableStateOf(Dates.today())
    var note by mutableStateOf("")
    var quantity by mutableStateOf("")
    var categoryTouched = false
    var original: Txn? = null
        private set

    val categories: StateFlow<List<Category>> = c.repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<AccountBalance>> = c.repo.accountBalances.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val members: StateFlow<List<Member>> = c.repo.members.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val suggestions: StateFlow<List<TitleSuggestion>> = snapshotFlow { type }
        .flatMapLatest { c.repo.titleSuggestions(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (editId != null) {
            viewModelScope.launch {
                val t = c.repo.getTxn(editId)
                if (t != null) {
                    original = t
                    type = t.type
                    amount = Money.toEditable(if (t.type == TxType.ADJUSTMENT) t.amount else kotlin.math.abs(t.amount))
                    title = t.title
                    categoryId = t.categoryId
                    categoryTouched = true
                    accountId = t.accountId
                    toAccountId = t.toAccountId
                    memberId = t.memberId
                    date = Dates.toDate(t.occurredAt)
                    note = t.note
                    quantity = if (t.savingId != null) quantityToEditable(t.savingQty) else ""
                }
                loaded = true
            }
        }
    }

    val isEdit get() = editId != null
    val editableType get() = type == TxType.EXPENSE || type == TxType.INCOME || type == TxType.TRANSFER

    fun pickSuggestion(s: TitleSuggestion) {
        title = s.title
        if (s.categoryId != null) {
            categoryId = s.categoryId
            categoryTouched = true
        }
    }

    fun onTitleChange(v: String) {
        title = v
        if (!categoryTouched) {
            val match = suggestions.value.firstOrNull { it.title.equals(v.trim(), ignoreCase = true) }
            if (match?.categoryId != null) categoryId = match.categoryId
        }
    }

    fun validationError(): String? {
        val k = amountToKurus(amount)
        if (k == 0L && type != TxType.ADJUSTMENT) return "Tutar girin"
        if (type == TxType.TRANSFER) {
            if (accountId == null || toAccountId == null) return "Kaynak ve hedef hesabı seçin"
            if (accountId == toAccountId) return "Aynı hesaba transfer yapılamaz"
        }
        return null
    }

    fun save(onSaved: (Txn, Long) -> Unit) = viewModelScope.launch {
        val k = amountToKurus(amount)
        val orig = original
        val time = when {
            orig != null && Dates.toDate(orig.occurredAt) == date -> orig.occurredAt
            date == Dates.today() -> System.currentTimeMillis()
            else -> Dates.millisOf(date, LocalTime.now().withNano(0))
        }
        val cat = when (type) {
            TxType.EXPENSE -> categoryId ?: Seeds.CAT_OTHER_EXPENSE
            TxType.INCOME -> categoryId ?: Seeds.CAT_OTHER_INCOME
            else -> categoryId
        }
        val base = orig ?: Txn(id = "")
        val t = base.copy(
            type = type,
            amount = if (type == TxType.ADJUSTMENT) (orig?.amount?.let { if (it < 0) -k else k } ?: k) else k,
            title = title.trim(),
            categoryId = if (type == TxType.TRANSFER) null else cat,
            accountId = accountId,
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            memberId = memberId,
            occurredAt = time,
            note = note.trim(),
            savingQty = if (base.savingId != null) quantityOf(quantity) else base.savingQty,
        )
        val saved = c.repo.saveTxn(t)
        c.prefs.setLastAccount(type, accountId)
        val total = c.repo.totalBalance.first()
        onSaved(saved, total)
    }

    fun delete(onDone: (Txn) -> Unit) = viewModelScope.launch {
        val o = original ?: return@launch
        c.repo.deleteTxn(o)
        onDone(o)
    }

    fun undoDelete(t: Txn) = c.appScope.launch { c.repo.restoreTxn(t) }
}

@Composable
fun EditTxnScreen(nav: NavController, initialType: TxType, editId: String?, presetAccount: String?) {
    val vm = appViewModel(key = "edit_${editId}_$initialType") { EditTxnViewModel(it, initialType, editId, presetAccount) }
    val toast = LocalToast.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    val color = txColor(vm.type)

    LaunchedEffect(vm.loaded) {
        if (vm.loaded && !vm.isEdit) runCatching { amountFocus.requestFocus() }
    }

    val screenTitle = when {
        vm.isEdit -> "Hareketi düzenle"
        vm.type == TxType.INCOME -> "Gelir ekle"
        vm.type == TxType.TRANSFER -> "Transfer"
        else -> "Harcama ekle"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Kapat") } },
                actions = {
                    if (vm.isEdit) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Sil") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(16.dp)) {
                Button(
                    onClick = {
                        val err = vm.validationError()
                        if (err != null) {
                            error = err
                        } else {
                            vm.save { t, total ->
                                val label = when (t.type) {
                                    TxType.EXPENSE -> "Harcama kaydedildi"
                                    TxType.INCOME -> "Gelir kaydedildi"
                                    else -> "Kaydedildi"
                                }
                                toast("$label · Yeni bakiye ${Money.format(total)}", null, null)
                                nav.popBackStack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(if (vm.isEdit) "Değişiklikleri kaydet" else "Kaydet", style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.editableType) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(TxType.EXPENSE to "Gider", TxType.INCOME to "Gelir", TxType.TRANSFER to "Transfer")
                    opts.forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = vm.type == t,
                            onClick = {
                                if (vm.type != t) {
                                    vm.type = t
                                    vm.categoryId = null
                                    vm.categoryTouched = false
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(label) }
                    }
                }
            } else {
                Text(vm.type.label, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            BigAmountField(
                value = vm.amount,
                onValueChange = { vm.amount = it; error = null },
                color = color,
                focusRequester = amountFocus,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            OutlinedTextField(
                value = vm.title,
                onValueChange = vm::onTitleChange,
                label = { Text(if (vm.type == TxType.INCOME) "Açıklama (ör. Maaş, Ek ders)" else if (vm.type == TxType.TRANSFER) "Açıklama (isteğe bağlı)" else "Ne için? (ör. Migros, Fatura)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            val q = vm.title.trim()
            val filtered = suggestions.filter { q.isEmpty() || (it.title.contains(q, ignoreCase = true) && !it.title.equals(q, ignoreCase = true)) }.take(12)
            if (filtered.isNotEmpty() && vm.type != TxType.TRANSFER) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.title }) { s ->
                        AssistChip(onClick = { vm.pickSuggestion(s) }, label = { Text(s.title, maxLines = 1) })
                    }
                }
            }

            when (vm.type) {
                TxType.EXPENSE, TxType.INCOME -> {
                    val kind = if (vm.type == TxType.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
                    val cats = categories.filter { it.kind == kind && (!it.archived || it.id == vm.categoryId) }
                    FieldLabel("Kategori")
                    CategoryGrid(cats, vm.categoryId) {
                        vm.categoryId = it
                        vm.categoryTouched = true
                    }
                    val active = accounts.filter { !it.account.archived || it.account.id == vm.accountId }
                    if (active.size > 1) {
                        FieldLabel("Hesap")
                        AccountChips(active, vm.accountId) { vm.accountId = it }
                    }
                }
                TxType.TRANSFER -> {
                    val active = accounts.filter { !it.account.archived }
                    FieldLabel("Nereden")
                    AccountChips(active, vm.accountId) { vm.accountId = it }
                    FieldLabel("Nereye")
                    AccountChips(active, vm.toAccountId) { vm.toAccountId = it }
                    if (active.size < 2) {
                        Text(
                            "Transfer için en az iki hesap gerekir. Varlıklar → Hesaplar bölümünden hesap ekleyebilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    if (vm.original?.savingId != null) {
                        AmountTextField("Miktar", vm.quantity, { vm.quantity = it }, suffix = "birim", maxDecimals = 6)
                    }
                    val active = accounts.filter { !it.account.archived || it.account.id == vm.accountId }
                    if (active.size > 1) {
                        FieldLabel("Hesap")
                        AccountChips(active, vm.accountId) { vm.accountId = it }
                    }
                }
            }

            if (members.size > 1) {
                FieldLabel(if (vm.type == TxType.INCOME) "Kimin geliri?" else "Kim?")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members, key = { it.id }) { m ->
                        FilterChip(
                            selected = vm.memberId == m.id,
                            onClick = { vm.memberId = m.id },
                            label = { Text(m.name) },
                            leadingIcon = { MemberAvatar(m, size = 22.dp) },
                        )
                    }
                }
            }

            FieldLabel("Tarih")
            DateSelector(vm.date, { vm.date = it })

            OutlinedTextField(
                value = vm.note,
                onValueChange = { vm.note = it },
                label = { Text("Not (isteğe bağlı)") },
                leadingIcon = { Icon(Icons.Rounded.Notes, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                minLines = 1,
                maxLines = 4,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hareket silinsin mi?",
            text = "Bu hareket iki telefondan da silinecek ve bakiye buna göre güncellenecek.",
            onConfirm = {
                vm.delete { deleted ->
                    toast("Hareket silindi", "Geri al") { vm.undoDelete(deleted) }
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryGrid(cats: List<Category>, selected: String?, onSelect: (String) -> Unit) {
    val rows = if (cats.size > 8) 2 else 1
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier.fillMaxWidth().height(if (rows == 2) 200.dp else 100.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(cats, key = { it.id }) { cat ->
            val isSel = cat.id == selected
            Column(
                Modifier
                    .width(86.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSel) colorOf(cat.color).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable { onSelect(cat.id) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(AppIcons.of(cat.icon), colorOf(cat.color), size = 42.dp, iconSize = 22.dp, filled = isSel)
                Spacer(Modifier.height(4.dp))
                Text(
                    cat.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AccountChips(list: List<AccountBalance>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.account.id }) { ab ->
            FilterChip(
                selected = ab.account.id == selected,
                onClick = { onSelect(ab.account.id) },
                label = { Text(ab.account.name, maxLines = 1) },
                leadingIcon = { Icon(AppIcons.account(ab.account.type), null, Modifier.size(18.dp), tint = colorOf(ab.account.color)) },
            )
        }
    }
}
