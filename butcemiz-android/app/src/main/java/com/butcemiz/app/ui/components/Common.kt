@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butcemiz.app.AppContainer
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.ui.theme.AmountStyle
import com.butcemiz.app.ui.theme.LocalSemantic

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer yok") }
val LocalHideAmounts = compositionLocalOf { false }

fun colorOf(v: Long): Color = Color(v)

/** Gizleme modunu dikkate alarak tutar metni */
@Composable
fun money(kurus: Long, decimals: Money.Decimals = Money.Decimals.AUTO, sign: Boolean = false): String =
    if (LocalHideAmounts.current) "•••• ₺" else Money.format(kurus, decimals, sign)

@Composable
fun compactMoney(kurus: Long): String = if (LocalHideAmounts.current) "••••" else Money.compact(kurus)

@Composable
fun txColor(type: TxType): Color {
    val s = LocalSemantic.current
    return when (type) {
        TxType.EXPENSE -> s.expense
        TxType.INCOME -> s.income
        TxType.TRANSFER -> s.transfer
        TxType.SAVING_DEPOSIT, TxType.SAVING_WITHDRAW -> s.gold
        TxType.LOAN_RECEIVED, TxType.LOAN_GIVEN -> s.warning
        TxType.ADJUSTMENT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** İşlemin listede görünen işaretli tutarı */
@Composable
fun signedAmount(t: Txn): String {
    if (LocalHideAmounts.current) return "•••• ₺"
    return when (t.type) {
        TxType.EXPENSE, TxType.SAVING_DEPOSIT, TxType.LOAN_GIVEN -> Money.MINUS + Money.format(t.amount)
        TxType.INCOME, TxType.SAVING_WITHDRAW, TxType.LOAN_RECEIVED -> "+" + Money.format(t.amount)
        TxType.ADJUSTMENT -> Money.format(t.amount, sign = true)
        TxType.TRANSFER -> Money.format(t.amount)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, LocalSemantic.current.cardBorder)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun MemberAvatar(member: Member?, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val color = member?.let { colorOf(it.color) } ?: MaterialTheme.colorScheme.outline
    val label = member?.emoji?.takeIf { it.isNotBlank() } ?: member?.name?.trim()?.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 72.dp, iconSize = 36.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(600), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = "Sil",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, iconColor, size = 38.dp, iconSize = 20.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        onClick = if (enabled) ({ onChange(!checked) }) else null,
    )
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

fun txIcon(t: TxType, category: Category?): ImageVector = when (t) {
    TxType.TRANSFER -> Icons.Rounded.SwapHoriz
    TxType.SAVING_DEPOSIT, TxType.SAVING_WITHDRAW -> Icons.Rounded.Savings
    TxType.ADJUSTMENT -> Icons.Rounded.Tune
    TxType.LOAN_RECEIVED, TxType.LOAN_GIVEN -> Icons.Rounded.Handshake
    else -> AppIcons.of(category?.icon)
}

/** Hareket satırı: ana sayfa akışı ve hareketler listesinde kullanılır. */
@Composable
fun TxnRow(
    t: Txn,
    category: Category?,
    member: Member?,
    account: Account?,
    modifier: Modifier = Modifier,
    toAccount: Account? = null,
    showTime: Boolean = true,
    relativeTime: Boolean = false,
    balanceAfter: Long? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = when (t.type) {
        TxType.EXPENSE, TxType.INCOME -> category?.let { colorOf(it.color) } ?: txColor(t.type)
        else -> txColor(t.type)
    }
    val bg by animateColorAsState(
        if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(900),
        label = "hl",
    )
    val title = t.title.ifBlank { category?.name ?: t.type.label }
    val sub = buildList {
        when (t.type) {
            TxType.TRANSFER -> add("${account?.name ?: "?"} → ${toAccount?.name ?: "?"}")
            TxType.EXPENSE, TxType.INCOME -> if (category != null && category.name != title) add(category.name)
            else -> add(t.type.label)
        }
        if (member != null) add(member.name)
        if (showTime) add(if (relativeTime) Dates.relative(t.createdAt.takeIf { it > 0 } ?: t.occurredAt) else Dates.time(t.occurredAt))
    }.joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconBadge(txIcon(t.type, category), color)
            if (member != null) {
                MemberAvatar(
                    member,
                    size = 18.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(0.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 72.dp)) {
            Text(
                signedAmount(t),
                style = AmountStyle.copy(fontSize = 16.sp),
                color = when (t.type) {
                    TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                    TxType.INCOME -> LocalSemantic.current.income
                    else -> txColor(t.type)
                },
                maxLines = 1,
            )
            if (balanceAfter != null) {
                Text(
                    "Bakiye ${money(balanceAfter, Money.Decimals.NEVER)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LabeledAmount(label: String, value: String, color: Color, modifier: Modifier = Modifier, style: TextStyle = AmountStyle.copy(fontSize = 18.sp)) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style, color = color, maxLines = 1)
    }
}

@Composable
fun rememberSnackbar(): SnackbarHostState = remember { SnackbarHostState() }

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}
