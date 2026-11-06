package com.butcemiz.app.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AccountBalance
import com.butcemiz.app.data.AccountType
import com.butcemiz.app.data.Debt
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.DebtKind
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.SavingType
import com.butcemiz.app.ui.components.AmountTextField
import com.butcemiz.app.ui.components.ChipRow
import com.butcemiz.app.ui.components.ColorPicker
import com.butcemiz.app.ui.components.DateSelector
import com.butcemiz.app.ui.components.SelectField
import com.butcemiz.app.ui.components.amountToKurus
import com.butcemiz.app.ui.components.quantityOf
import com.butcemiz.app.ui.components.quantityToEditable
import com.butcemiz.app.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.roundToLong

/** Alt sayfa (bottom sheet) içinde form iskeleti */
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    saveLabel: String = "Kaydet",
    canSave: Boolean = true,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(saveLabel) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun FormLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun NameField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

// =============================== Hesap ===============================

@Composable
fun AccountEditorSheet(initial: Account?, onDismiss: () -> Unit, onSave: (Account) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK) }
    var opening by remember { mutableStateOf(initial?.let { Money.toEditable(it.openingBalance).removePrefix("-") } ?: "") }
    var negative by remember { mutableStateOf((initial?.openingBalance ?: 0) < 0) }
    var color by remember { mutableStateOf(initial?.color ?: Palette.choices[0]) }
    var include by remember { mutableStateOf(initial?.includeInTotal ?: true) }
    var limit by remember { mutableStateOf(initial?.creditLimit?.takeIf { it > 0 }?.let { Money.toEditable(it) } ?: "") }
    val isNew = initial == null
    FormSheet(
        title = if (isNew) "Yeni hesap" else "Hesabı düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank(),
        onSave = {
            val ob = amountToKurus(opening).let { if (negative || (type == AccountType.CREDIT_CARD && isNew && it > 0)) -it else it }
            onSave(
                (initial ?: Account(id = "", name = "")).copy(
                    name = name.trim(),
                    type = type,
                    openingBalance = if (isNew) ob else initial!!.openingBalance,
                    color = color,
                    includeInTotal = include,
                    creditLimit = amountToKurus(limit),
                ),
            )
        },
    ) {
        NameField("Hesap adı (ör. Ziraat, Cüzdan)", name) { name = it }
        FormLabel("Tür")
        ChipRow(AccountType.entries, type, { it.label }, {
            type = it
            if (isNew) include = it != AccountType.CREDIT_CARD
        })
        if (isNew) {
            AmountTextField(
                if (type == AccountType.CREDIT_CARD) "Mevcut kart borcu" else "Şu anki bakiye",
                opening,
                { opening = it },
                supporting = if (type == AccountType.CREDIT_CARD) "Kart borcu eksi bakiye olarak tutulur" else "Sonradan \"Bakiyeyi düzelt\" ile güncelleyebilirsiniz",
            )
            if (type != AccountType.CREDIT_CARD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = negative, onCheckedChange = { negative = it })
                    Text("Bakiye eksi (ek hesap kullanımı)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (type == AccountType.CREDIT_CARD) {
            AmountTextField("Kart limiti (isteğe bağlı)", limit, { limit = it })
        }
        SwitchLine("Toplam bakiyeye dahil", "Ana sayfadaki toplam bakiyede sayılsın", include) { include = it }
        FormLabel("Renk")
        ColorPicker(color, Palette.choices, { color = it })
    }
}

@Composable
fun AdjustBalanceSheet(account: AccountBalance, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(Money.toEditable(kotlin.math.abs(account.balance))) }
    var negative by remember { mutableStateOf(account.balance < 0) }
    FormSheet(
        title = "Bakiyeyi düzelt",
        onDismiss = onDismiss,
        onSave = { onSave(amountToKurus(value).let { if (negative) -it else it }) },
    ) {
        Text(
            "${account.account.name} için bankadaki/cüzdandaki gerçek bakiyeyi girin. Aradaki fark \"Bakiye düzeltme\" hareketi olarak kaydedilir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Uygulamadaki bakiye: ${Money.format(account.balance)}", style = MaterialTheme.typography.bodyMedium)
        AmountTextField("Gerçek bakiye", value, { value = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = negative, onCheckedChange = { negative = it })
            Text("Eksi bakiye", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// =============================== Birikim ===============================

@Composable
fun SavingEditorSheet(initial: Saving?, members: List<Member>, onDismiss: () -> Unit, onSave: (Saving) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: SavingType.GOLD_GRAM) }
    var qty by remember { mutableStateOf(initial?.initialQty?.let { quantityToEditable(it) } ?: "") }
    var manualPrice by remember { mutableStateOf(initial?.manualUnitPrice?.let { Money.toEditable(it) } ?: "") }
    var cost by remember { mutableStateOf(initial?.costBasis?.let { Money.toEditable(it) } ?: "") }
    var target by remember { mutableStateOf(initial?.targetAmount?.let { Money.toEditable(it) } ?: "") }
    var owner by remember { mutableStateOf(initial?.ownerMemberId) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val isNew = initial == null
    FormSheet(
        title = if (isNew) "Yeni birikim" else "Birikimi düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank() || isNew,
        onSave = {
            onSave(
                (initial ?: Saving(id = "", name = "")).copy(
                    name = name.trim().ifBlank { type.label },
                    type = type,
                    initialQty = quantityOf(qty),
                    manualUnitPrice = manualPrice.takeIf { it.isNotBlank() }?.let { amountToKurus(it) },
                    costBasis = cost.takeIf { it.isNotBlank() }?.let { amountToKurus(it) },
                    targetAmount = target.takeIf { it.isNotBlank() }?.let { amountToKurus(it) },
                    ownerMemberId = owner,
                    note = note.trim(),
                ),
            )
        },
    ) {
        SelectField("Birikim türü", type, SavingType.entries.toList(), { "${it.label} (${it.unit})" }, { type = it })
        NameField("Ad (ör. Düğün altınları, Dolar hesabı)", name) { name = it }
        AmountTextField(
            if (isNew) "Şu anki miktar" else "Başlangıç miktarı",
            qty,
            { qty = it },
            suffix = type.unit,
            maxDecimals = if (type.unitIsLira) 2 else 6,
            supporting = if (isNew) "Sonradan eklemeler için birikim ayrıntısındaki \"Ekle\" düğmesini kullanın" else null,
        )
        if (type.needsManualPrice) {
            AmountTextField("Birim fiyat (₺)", manualPrice, { manualPrice = it }, supporting = "Hisse/fon/kripto gibi türlerde güncel birim fiyatı siz girersiniz")
        } else if (!type.unitIsLira) {
            AmountTextField("Elle birim fiyat (isteğe bağlı)", manualPrice, { manualPrice = it }, supporting = "Boş bırakırsanız güncel kur otomatik kullanılır")
        }
        AmountTextField("Toplam maliyet (isteğe bağlı)", cost, { cost = it }, supporting = "Kâr/zarar hesaplamak için")
        AmountTextField("Hedef tutar (isteğe bağlı)", target, { target = it }, supporting = "Ör. tatil fonu için 50.000 ₺")
        if (members.size > 1) {
            FormLabel("Kimin?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = owner == null, onClick = { owner = null }, label = { Text("Ortak") })
                members.forEach { m -> FilterChip(selected = owner == m.id, onClick = { owner = m.id }, label = { Text(m.name) }) }
            }
        }
        NameField("Not (isteğe bağlı)", note) { note = it }
    }
}

@Composable
fun SavingMovementSheet(
    saving: Saving,
    deposit: Boolean,
    unitPrice: Long?,
    accounts: List<AccountBalance>,
    defaultAccount: String?,
    onDismiss: () -> Unit,
    onSave: (qty: Double, amount: Long, accountId: String?, date: LocalDate, note: String) -> Unit,
) {
    var qty by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var amountEdited by remember { mutableStateOf(false) }
    var useAccount by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf(defaultAccount) }
    var date by remember { mutableStateOf(Dates.today()) }
    var note by remember { mutableStateOf("") }
    val type = saving.type
    fun recalc(q: String) {
        if (!amountEdited) {
            val k = if (type.unitIsLira) amountToKurus(q) else unitPrice?.let { (quantityOf(q) * it).roundToLong() } ?: 0L
            amount = if (k > 0) Money.toEditable(k) else ""
        }
    }
    FormSheet(
        title = if (deposit) "${saving.name}: ekle" else "${saving.name}: çıkar",
        onDismiss = onDismiss,
        canSave = quantityOf(qty) > 0,
        onSave = {
            val a = if (type.unitIsLira) amountToKurus(qty) else amountToKurus(amount)
            onSave(quantityOf(qty), a, if (useAccount) accountId else null, date, note.trim())
        },
    ) {
        AmountTextField(
            if (deposit) "Eklenen miktar" else "Çıkan miktar",
            qty,
            { qty = it; recalc(it) },
            suffix = type.unit,
            maxDecimals = if (type.unitIsLira) 2 else 6,
        )
        if (!type.unitIsLira) {
            AmountTextField(
                if (deposit) "Ödenen tutar" else "Elde edilen tutar",
                amount,
                { amount = it; amountEdited = true },
                supporting = unitPrice?.let { "Güncel birim fiyat: ${Money.format(it)}" } ?: "Tutarı girin",
            )
        }
        SwitchLine(
            if (deposit) "Bakiyeden düş" else "Bakiyeye ekle",
            if (deposit) "Para hesabınızdan çıktıysa açık bırakın (hediye vb. için kapatın)" else "Satıştan gelen para hesabınıza girdiyse",
            useAccount,
        ) { useAccount = it }
        if (useAccount && accounts.size > 1) {
            ChipRow(accounts.filter { !it.account.archived }, accounts.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        FormLabel("Tarih")
        DateSelector(date, { date = it })
        NameField("Not (isteğe bağlı)", note) { note = it }
    }
}

// =============================== Borç ===============================

data class DebtFormResult(
    val debt: Debt,
    val moneyAccountId: String?,
    val addInstallments: Boolean,
)

@Composable
fun DebtEditorSheet(
    initial: Debt?,
    accounts: List<AccountBalance>,
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (DebtFormResult) -> Unit,
) {
    val isNew = initial == null
    var direction by remember { mutableStateOf(initial?.direction ?: DebtDirection.I_OWE) }
    var kind by remember { mutableStateOf(initial?.kind ?: DebtKind.LOAN) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var counterparty by remember { mutableStateOf(initial?.counterparty ?: "") }
    var principal by remember { mutableStateOf(initial?.principal?.let { Money.toEditable(it) } ?: "") }
    var paid by remember { mutableStateOf(initial?.alreadyPaid?.takeIf { it > 0 }?.let { Money.toEditable(it) } ?: "") }
    var installment by remember { mutableStateOf(initial?.installmentAmount?.let { Money.toEditable(it) } ?: "") }
    var count by remember { mutableStateOf(initial?.installmentCount?.toString() ?: "") }
    var dueDay by remember { mutableStateOf(initial?.dueDay?.toString() ?: "") }
    var rate by remember { mutableStateOf(initial?.interestRate?.let { quantityToEditable(it, 2) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var owner by remember { mutableStateOf(initial?.ownerMemberId) }
    var moneyMoved by remember { mutableStateOf(false) }
    var moneyAccount by remember { mutableStateOf(accounts.firstOrNull()?.account?.id) }
    var addInstallments by remember { mutableStateOf(true) }
    FormSheet(
        title = if (isNew) "Yeni borç / alacak" else "Düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank() && amountToKurus(principal) > 0,
        onSave = {
            val d = (initial ?: Debt(id = "", name = "")).copy(
                name = name.trim(),
                direction = direction,
                kind = kind,
                counterparty = counterparty.trim(),
                principal = amountToKurus(principal),
                alreadyPaid = amountToKurus(paid),
                installmentAmount = installment.takeIf { it.isNotBlank() }?.let { amountToKurus(it) },
                installmentCount = count.toIntOrNull(),
                dueDay = dueDay.toIntOrNull()?.coerceIn(1, 31),
                interestRate = rate.takeIf { it.isNotBlank() }?.let { quantityOf(it) },
                note = note.trim(),
                ownerMemberId = owner,
                startDate = initial?.startDate ?: System.currentTimeMillis(),
            )
            onSave(DebtFormResult(d, if (isNew && moneyMoved) moneyAccount else null, isNew && addInstallments))
        },
    ) {
        if (isNew) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(DebtDirection.I_OWE to "Borcum var", DebtDirection.OWED_TO_ME to "Alacağım var").forEachIndexed { i, (d, label) ->
                    SegmentedButton(selected = direction == d, onClick = { direction = d }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
        }
        FormLabel("Türü")
        ChipRow(DebtKind.entries, kind, { it.label }, { kind = it })
        NameField(if (direction == DebtDirection.I_OWE) "Ad (ör. Konut kredisi, Telefon taksiti)" else "Ad (ör. Ahmet'e verilen borç)", name) { name = it }
        NameField(if (direction == DebtDirection.I_OWE) "Kime / hangi banka" else "Kimden", counterparty) { counterparty = it }
        AmountTextField("Toplam tutar", principal, { principal = it })
        AmountTextField("Daha önce ödenen (isteğe bağlı)", paid, { paid = it }, supporting = "Takibe başlamadan önce ödenmiş kısım")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountTextField("Taksit tutarı", installment, { installment = it }, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = count,
                onValueChange = { v -> count = v.filter { it.isDigit() }.take(3) },
                label = { Text("Taksit sayısı") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dueDay,
                onValueChange = { v -> dueDay = v.filter { it.isDigit() }.take(2) },
                label = { Text("Ödeme günü (1-31)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            )
            AmountTextField("Faiz % (isteğe bağlı)", rate, { rate = it }, suffix = "%", modifier = Modifier.weight(1f))
        }
        if (members.size > 1) {
            FormLabel("Kimin?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = owner == null, onClick = { owner = null }, label = { Text("Ortak") })
                members.forEach { m -> FilterChip(selected = owner == m.id, onClick = { owner = m.id }, label = { Text(m.name) }) }
            }
        }
        if (isNew) {
            SwitchLine(
                if (direction == DebtDirection.I_OWE) "Para hesabıma girdi" else "Para hesabımdan çıktı",
                if (direction == DebtDirection.I_OWE) "Kredi/borç parası bakiyeye eklensin" else "Verilen borç bakiyeden düşülsün",
                moneyMoved,
            ) { moneyMoved = it }
            if (moneyMoved && accounts.size > 1) {
                ChipRow(accounts.filter { !it.account.archived }, accounts.firstOrNull { it.account.id == moneyAccount }, { it.account.name }, { moneyAccount = it.account.id })
            }
            if (direction == DebtDirection.I_OWE && installment.isNotBlank() && dueDay.isNotBlank()) {
                SwitchLine("Taksitleri düzenli ödemelere ekle", "Her ay hatırlatılır; \"Öde\" deyince borçtan düşer", addInstallments) { addInstallments = it }
            }
        }
        NameField("Not (isteğe bağlı)", note) { note = it }
    }
}

@Composable
fun DebtPaymentSheet(
    debt: Debt,
    remaining: Long,
    accounts: List<AccountBalance>,
    onDismiss: () -> Unit,
    onSave: (amount: Long, accountId: String?, date: LocalDate, note: String) -> Unit,
) {
    val suggested = debt.installmentAmount?.coerceAtMost(remaining) ?: remaining
    var amount by remember { mutableStateOf(if (suggested > 0) Money.toEditable(suggested) else "") }
    var useAccount by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull { !it.account.archived && it.account.type != AccountType.CREDIT_CARD }?.account?.id ?: accounts.firstOrNull()?.account?.id) }
    var date by remember { mutableStateOf(Dates.today()) }
    var note by remember { mutableStateOf("") }
    val owe = debt.direction == DebtDirection.I_OWE
    FormSheet(
        title = if (owe) "Ödeme yap" else "Tahsilat ekle",
        onDismiss = onDismiss,
        canSave = amountToKurus(amount) > 0,
        onSave = { onSave(amountToKurus(amount), if (useAccount) accountId else null, date, note.trim()) },
    ) {
        Text("Kalan: ${Money.format(remaining)}", style = MaterialTheme.typography.bodyMedium)
        AmountTextField("Tutar", amount, { amount = it })
        SwitchLine(if (owe) "Bakiyeden düş" else "Bakiyeye ekle", null, useAccount) { useAccount = it }
        if (useAccount && accounts.size > 1) {
            ChipRow(accounts.filter { !it.account.archived }, accounts.firstOrNull { it.account.id == accountId }, { it.account.name }, { accountId = it.account.id })
        }
        FormLabel("Tarih")
        DateSelector(date, { date = it })
        NameField("Not (isteğe bağlı)", note) { note = it }
    }
}
