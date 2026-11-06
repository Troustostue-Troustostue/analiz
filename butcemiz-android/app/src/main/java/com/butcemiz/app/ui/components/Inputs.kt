package com.butcemiz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butcemiz.app.core.Dates
import com.butcemiz.app.ui.theme.AmountStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Tutar girişini temizler: yalnız rakam ve tek ondalık virgül, en çok 2 ondalık. */
fun sanitizeAmountInput(input: String, maxDecimals: Int = 2, maxIntDigits: Int = 12): String {
    val sb = StringBuilder()
    var sepSeen = false
    var decimals = 0
    var intDigits = 0
    for (ch in input) {
        when {
            ch.isDigit() -> if (sepSeen) {
                if (decimals < maxDecimals) {
                    sb.append(ch); decimals++
                }
            } else if (intDigits < maxIntDigits) {
                sb.append(ch); intDigits++
            }
            (ch == ',' || ch == '.') && !sepSeen && maxDecimals > 0 -> {
                sepSeen = true
                if (sb.isEmpty()) sb.append('0')
                sb.append(',')
            }
        }
    }
    // baştaki gereksiz sıfırlar
    val s = sb.toString()
    val sep = s.indexOf(',')
    val intPart = if (sep >= 0) s.substring(0, sep) else s
    val trimmed = intPart.trimStart('0').ifEmpty { if (intPart.isNotEmpty()) "0" else "" }
    return trimmed + if (sep >= 0) s.substring(sep) else ""
}

/** "1234567,5" → "1.234.567,5" görünümü (imleç konumları korunur) */
class AmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val sep = raw.indexOf(',')
        val intPart = if (sep >= 0) raw.substring(0, sep) else raw
        val n = intPart.length
        val out = StringBuilder()
        val o2t = IntArray(raw.length + 1)
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) out.append('.')
            o2t[i] = out.length
            out.append(intPart[i])
        }
        for (j in n until raw.length) {
            o2t[j] = out.length
            out.append(raw[j])
        }
        o2t[raw.length] = out.length
        val t2o = IntArray(out.length + 1)
        var o = 0
        for (t in 0..out.length) {
            while (o < raw.length && o2t[o + 1] <= t) o++
            t2o[t] = o
        }
        val outLen = out.length
        return TransformedText(
            AnnotatedString(out.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, outLen)]
            },
        )
    }
}

/** Ekleme ekranındaki büyük tutar alanı */
@Composable
fun BigAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNext: () -> Unit = {},
) {
    val style = AmountStyle.copy(fontSize = 46.sp, color = color, textAlign = TextAlign.Center)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeAmountInput(it)) },
        modifier = modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        textStyle = style,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        visualTransformation = AmountVisualTransformation(),
        cursorBrush = SolidColor(color),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text("0", style = style.copy(color = color.copy(alpha = 0.35f)))
                    inner()
                }
                Text(" ₺", style = style.copy(fontSize = 34.sp, color = color.copy(alpha = 0.8f)))
            }
        },
    )
}

/** Formlarda kullanılan standart tutar alanı */
@Composable
fun AmountTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "₺",
    maxDecimals: Int = 2,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeAmountInput(it, maxDecimals)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        visualTransformation = AmountVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Ham giriş ("1234,5") → kuruş */
fun amountToKurus(raw: String): Long {
    if (raw.isBlank()) return 0L
    return com.butcemiz.app.core.Money.parse(raw) ?: 0L
}

/** Ham giriş → ondalıklı sayı (miktarlar için) */
fun quantityOf(raw: String): Double = raw.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0

/** Ondalıklı sayı → düzenlenebilir ham giriş */
fun quantityToEditable(v: Double, maxDecimals: Int = 6): String {
    if (v == 0.0) return ""
    val s = java.math.BigDecimal(v).setScale(maxDecimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return s.replace('.', ',')
}

@Composable
fun DateSelector(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    val today = Dates.today()
    val yesterday = today.minusDays(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = date == today, onClick = { onChange(today) }, label = { Text("Bugün") })
        FilterChip(selected = date == yesterday, onClick = { onChange(yesterday) }, label = { Text("Dün") })
        val other = date != today && date != yesterday
        FilterChip(
            selected = other,
            onClick = { show = true },
            label = { Text(if (other) Dates.dayMonthShort(date) + if (date.year != today.year) " ${date.year}" else "" else "Tarih seç") },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
    }
    if (show) DatePickerModal(date, onDismiss = { show = false }, onPick = onChange)
}

@Composable
fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    ) {
        DatePicker(state = state)
    }
}

/** Açılır seçim alanı */
@Composable
fun <T> SelectField(
    label: String,
    selected: T,
    options: List<T>,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(display(o)) },
                    onClick = {
                        onSelect(o)
                        open = false
                    },
                    trailingIcon = if (o == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                )
            }
        }
    }
}

/** Yatay kaydırılabilir seçim çipleri */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o), maxLines = 1) },
                leadingIcon = leading?.let { { it(o) } },
            )
        }
    }
}

@Composable
fun ColorPicker(selected: Long, colors: List<Long>, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colorOf(c))
                    .border(if (isSel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconPicker(selected: String, color: Long, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcons.map.forEach { (key, icon) ->
            val isSel = key == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(if (isSel) 2.dp else 0.dp, colorOf(color), CircleShape)
                    .clickable { onSelect(key) }
                    .padding(2.dp),
            ) {
                IconBadge(icon, colorOf(color), size = 40.dp, iconSize = 20.dp, filled = isSel)
            }
        }
    }
}
