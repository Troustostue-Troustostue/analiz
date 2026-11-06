@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.home

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Occurrence
import com.butcemiz.app.data.PaydayStatus
import com.butcemiz.app.data.TxType
import com.butcemiz.app.sync.SyncEngine
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.EmptyState
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.LocalContainer
import com.butcemiz.app.ui.components.LocalHideAmounts
import com.butcemiz.app.ui.components.Pill
import com.butcemiz.app.ui.components.ProgressBar
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.TxnRow
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.money
import com.butcemiz.app.ui.components.rememberSnackbar
import com.butcemiz.app.ui.nav.Routes
import com.butcemiz.app.ui.settings.isIgnoringBattery
import com.butcemiz.app.ui.settings.requestIgnoreBattery
import com.butcemiz.app.ui.nav.appViewModel
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.nav.openTab
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavController) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalContainer.current
    val sync by c.engine.status.collectAsStateWithLifecycle()
    val paired by c.prefs.pairedFlow.collectAsStateWithLifecycle()
    val hidden = LocalHideAmounts.current
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val highlighted = remember { mutableStateMapOf<String, Boolean>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryTip by remember { mutableStateOf(!c.prefs.batteryPromptShown && !isIgnoringBattery(context)) }

    // Eşten gelen hareketleri ekranda göster
    LaunchedEffect(Unit) {
        c.remoteActivity.collect { list ->
            list.forEach { highlighted[it.id] = true }
            val t = list.last()
            val who = s.members[t.memberId]?.name ?: "Eşin"
            val what = t.title.ifBlank { s.categories[t.categoryId]?.name ?: t.type.label }
            val msg = when (t.type) {
                TxType.EXPENSE -> "$who, $what için ${Money.format(t.amount)} harcadı"
                TxType.INCOME -> "$who gelir ekledi: $what +${Money.format(t.amount)}"
                else -> "$who: $what · ${Money.format(t.amount)}"
            }
            launch {
                delay(4_500)
                list.forEach { highlighted.remove(it.id) }
            }
            snackbar.showSnackbar(if (list.size > 1) "$msg (+${list.size - 1} hareket daha)" else msg)
        }
    }

    // Android 13+: bildirim izni
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !c.notifier.canPost() && !c.prefs.flag("notif_asked")) {
            c.prefs.setFlag("notif_asked")
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.openAdd(TxType.EXPENSE) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Rounded.Add, contentDescription = "Harcama ekle", modifier = Modifier.size(28.dp)) }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(
                    name = s.me?.name,
                    householdName = s.householdName,
                    sync = sync,
                    paired = paired,
                    hidden = hidden,
                    onToggleHide = vm::toggleHide,
                    onSync = { nav.navigate(Routes.SYNC) },
                )
            }
            item {
                HeroCard(
                    total = s.total,
                    income = s.monthIncome,
                    expense = s.monthExpense,
                    periodLabel = s.period?.let { com.butcemiz.app.core.Periods.label(it) } ?: "",
                    accountCount = s.accountCount,
                    onClick = { nav.openTab(Routes.ASSETS) },
                )
            }
            item {
                QuickActions(
                    onExpense = { nav.openAdd(TxType.EXPENSE) },
                    onIncome = { nav.openAdd(TxType.INCOME) },
                    onTransfer = { nav.openAdd(TxType.TRANSFER) },
                    onSaving = { nav.openTab(Routes.ASSETS) },
                )
            }
            if (!paired) {
                item { InviteCard(onClick = { nav.navigate(Routes.SYNC) }) }
            } else if (showBatteryTip) {
                item {
                    BatteryCard(
                        onFix = {
                            requestIgnoreBattery(context)
                            c.prefs.batteryPromptShown = true
                            showBatteryTip = false
                        },
                        onDismiss = {
                            c.prefs.batteryPromptShown = true
                            showBatteryTip = false
                        },
                    )
                }
            }
            items(s.paydays, key = { "pay_" + it.source.id }) { st ->
                PaydayCard(st, s.members[st.source.memberId]?.name, onRecord = {
                    vm.recordPayday(st)
                    scope.launch { snackbar.showSnackbar("${st.source.name} eklendi: +${Money.format(st.source.amount)}") }
                })
            }
            if (s.upcoming.isNotEmpty()) {
                item {
                    UpcomingCard(
                        list = s.upcoming.take(4),
                        more = s.upcoming.size - 4,
                        onPay = { o ->
                            if (o.recurring.amount > 0 && !o.recurring.variable) {
                                vm.payOccurrence(o) { amt -> scope.launch { snackbar.showSnackbar("${o.recurring.name} ödendi: ${Money.format(amt)}") } }
                            } else {
                                nav.navigate(Routes.PAYMENTS)
                            }
                        },
                        onAll = { nav.navigate(Routes.PAYMENTS) },
                    )
                }
            }
            item { SummaryTiles(s) }
            if (s.budgets.isNotEmpty()) {
                item { BudgetCard(s, onClick = { nav.navigate(Routes.BUDGETS) }) }
            }
            item {
                SectionHeader("Canlı akış", action = "Tümü", onAction = { nav.openTab(Routes.TXNS) })
            }
            if (s.feed.isEmpty() && !s.loading) {
                item {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.ReceiptLong,
                            "Henüz hareket yok",
                            "İlk harcamanızı ya da gelirinizi ekleyin. Eşinizin girdikleri de burada anında görünecek.",
                            action = "Harcama ekle",
                            onAction = { nav.openAdd(TxType.EXPENSE) },
                        )
                    }
                }
            } else {
                item {
                    AppCard(padding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                        s.feed.take(12).forEach { t ->
                            TxnRow(
                                t = t,
                                category = s.categories[t.categoryId],
                                member = s.members[t.memberId],
                                account = s.accounts[t.accountId],
                                toAccount = s.accounts[t.toAccountId],
                                relativeTime = true,
                                balanceAfter = s.balanceAfter[t.id],
                                highlight = highlighted[t.id] == true,
                                onClick = { nav.openAdd(t.type, t.id) },
                            )
                        }
                    }
                }
            }
            item { WealthCard(s, onClick = { nav.openTab(Routes.ASSETS) }) }
        }
    }
}

@Composable
private fun Header(
    name: String?,
    householdName: String,
    sync: SyncEngine.Status,
    paired: Boolean,
    hidden: Boolean,
    onToggleHide: () -> Unit,
    onSync: () -> Unit,
) {
    val today = Dates.today()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name != null) "Merhaba, $name 👋" else "Merhaba 👋",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Dates.dayMonth(today)} ${Dates.weekday(today)} · $householdName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleHide) {
            Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Tutarları gizle/göster")
        }
        SyncChip(sync, paired, onSync)
    }
}

@Composable
fun SyncChip(sync: SyncEngine.Status, paired: Boolean, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val (color, label) = when (sync.state) {
        SyncEngine.State.CONNECTED -> sem.income to "Senkron"
        SyncEngine.State.WAITING_PARTNER -> sem.warning to "Eş bekleniyor"
        SyncEngine.State.CONNECTING -> sem.warning to "Bağlanıyor"
        SyncEngine.State.OFFLINE -> MaterialTheme.colorScheme.outline to "Çevrimdışı"
        SyncEngine.State.QUOTA -> sem.warning to "Sınırda"
        SyncEngine.State.NOT_CONFIGURED -> MaterialTheme.colorScheme.outline to "Eşleşmedi"
        SyncEngine.State.IDLE -> (if (paired) sem.income else MaterialTheme.colorScheme.outline) to (if (paired) "Hazır" else "Eşleşmedi")
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            if (sync.pendingOps > 0 && paired) "$label · ${sync.pendingOps}" else label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroCard(total: Long, income: Long, expense: Long, periodLabel: String, accountCount: Int, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(sem.heroBrush)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Toplam bakiye", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (accountCount > 1) {
                    Text("$accountCount hesap", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                money(total),
                style = AmountStyle.copy(fontSize = 38.sp),
                color = Color.White,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Gelir · $periodLabel", "+" + money(income, Money.Decimals.NEVER), Icons.Rounded.CallReceived, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                HeroStat("Gider · $periodLabel", Money.MINUS + money(expense, Money.Decimals.NEVER), Icons.Rounded.Remove, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = Color.White, style = AmountStyle.copy(fontSize = 15.sp), maxLines = 1)
        }
    }
}

@Composable
private fun QuickActions(onExpense: () -> Unit, onIncome: () -> Unit, onTransfer: () -> Unit, onSaving: () -> Unit) {
    val sem = LocalSemantic.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction("Harcama", Icons.Rounded.Remove, sem.expense, onExpense)
        QuickAction("Gelir", Icons.Rounded.Add, sem.income, onIncome)
        QuickAction("Transfer", Icons.Rounded.SwapHoriz, sem.transfer, onTransfer)
        QuickAction("Birikim", Icons.Rounded.Savings, sem.gold, onSaving)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InviteCard(onClick: () -> Unit) {
    AppCard(onClick = onClick, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.GroupAdd, MaterialTheme.colorScheme.primary, filled = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Eşinizi davet edin", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "QR kodu okutun; harcamalar iki telefonda anında görünsün.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(onFix: () -> Unit, onDismiss: () -> Unit) {
    val sem = LocalSemantic.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.BatteryAlert, sem.warning)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bildirimler gecikmesin", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Samsung pil tasarrufu arka plan bağlantısını kesebilir. Bütçemiz için pil optimizasyonunu kapatın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Sonra") }
            FilledTonalButton(onClick = onFix) { Text("Ayarla") }
        }
    }
}

@Composable
private fun PaydayCard(st: PaydayStatus, memberName: String?, onRecord: () -> Unit) {
    val sem = LocalSemantic.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Celebration, sem.income)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (st.due == Dates.today()) "Bugün ${st.source.name.lowercase(com.butcemiz.app.core.TR)} günü!" else "${st.source.name} bekleniyor",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(memberName, money(st.source.amount), Dates.dayMonth(st.due)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onRecord) { Text("Yattı") }
        }
    }
}

@Composable
private fun UpcomingCard(list: List<Occurrence>, more: Int, onPay: (Occurrence) -> Unit, onAll: () -> Unit) {
    val c = LocalContainer.current
    val sem = LocalSemantic.current
    AppCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EventRepeat, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Yaklaşan ödemeler", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text(if (more > 0) "+$more · Tümü" else "Tümü") }
        }
        list.forEach { o ->
            val days = Dates.daysUntil(o.due)
            val late = days < 0
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.recurring.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Dates.dueText(o.due) + if (o.recurring.amount > 0) " · " + (if (o.recurring.variable) "≈ " else "") + money(o.recurring.amount) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) sem.expense else if (days <= 1) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onPay(o) }) { Text(if (o.recurring.amount > 0 && !o.recurring.variable) "Öde" else "Gir") }
            }
        }
    }
}

@Composable
private fun SummaryTiles(s: HomeState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile("Bugün", s.todayExpense, null, Modifier.weight(1f))
        SummaryTile("Bu hafta", s.weekExpense, s.prevWeekExpense, Modifier.weight(1f))
        SummaryTile("Bu ay", s.monthExpense, s.prevMonthExpenseSoFar, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: Long, previous: Long?, modifier: Modifier) {
    val sem = LocalSemantic.current
    AppCard(modifier, padding = PaddingValues(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(value, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 17.sp), maxLines = 1)
        if (previous != null) {
            val text: String
            val color: Color
            if (previous <= 0L) {
                text = "gider"
                color = MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                val pct = ((value - previous) * 100 / previous).toInt()
                text = if (pct > 0) "▲ %$pct fazla" else if (pct < 0) "▼ %${-pct} az" else "değişmedi"
                color = if (pct > 0) sem.expense else sem.income
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        } else {
            Text("gider", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetCard(s: HomeState, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    AppCard(onClick = onClick) {
        Text("Bütçe durumu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        s.budgets.take(3).forEach { b ->
            val cat = b.budget.categoryId?.let { s.categories[it] }
            val color = when {
                b.progress >= 1f -> sem.expense
                b.progress >= 0.8f -> sem.warning
                else -> cat?.let { colorOf(it.color) } ?: MaterialTheme.colorScheme.primary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat?.name ?: "Aylık toplam", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${money(b.spent, Money.Decimals.NEVER)} / ${money(b.budget.amount, Money.Decimals.NEVER)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(b.progress, color)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WealthCard(s: HomeState, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val net = s.total + s.savingsTotal + s.receivableTotal - s.debtTotal
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Net varlık", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(net, Money.Decimals.NEVER), style = AmountStyle.copy(fontSize = 22.sp))
            }
            Pill("Bakiye + birikim − borç", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Birikimler", money(s.savingsTotal, Money.Decimals.NEVER), sem.gold, Modifier.weight(1f))
            MiniStat("Borçlar", money(s.debtTotal, Money.Decimals.NEVER), sem.expense, Modifier.weight(1f))
            MiniStat("Alacaklar", money(s.receivableTotal, Money.Decimals.NEVER), sem.transfer, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.09f)).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = AmountStyle.copy(fontSize = 14.sp), color = color, maxLines = 1)
    }
}
