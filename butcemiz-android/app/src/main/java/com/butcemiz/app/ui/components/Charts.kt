package com.butcemiz.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

data class Slice(val value: Float, val color: Color)

/** Halka (donut) grafik; ortasına içerik yerleştirilebilir. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thickness: Dp = 22.dp,
    gapDegrees: Float = 2.2f,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            var start = -90f
            val usable = 360f * anim.value
            val gaps = if (slices.count { it.value > 0 } > 1) gapDegrees else 0f
            for (s in slices) {
                if (s.value <= 0f) continue
                val sweep = (s.value / total) * usable
                val drawSweep = max(0.5f, sweep - gaps)
                drawArc(s.color, start + gaps / 2, drawSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

/**
 * Çubuk grafik. labels her çubuğun altına yazılır (labelEvery ile seyreltilebilir).
 * selected: vurgulanan çubuk; onSelect ile dokunulan çubuk bildirilir.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    labelEvery: Int = 1,
    selected: Int? = null,
    secondary: List<Long>? = null,
    secondaryColor: Color = Color.Gray,
    valueLabel: (Long) -> String = { "" },
    onSelect: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values, secondary) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val n = values.size
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(n, onSelect) {
                if (onSelect != null && n > 0) {
                    detectTapGestures { p ->
                        val slot = size.width / n.toFloat()
                        onSelect((p.x / slot).toInt().coerceIn(0, n - 1))
                    }
                }
            },
    ) {
        if (n == 0) return@Canvas
        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        val labelH = 18.dp.toPx()
        val topPad = 18.dp.toPx()
        val chartH = size.height - labelH - topPad
        val maxV = max(1L, max(values.maxOrNull() ?: 0L, secondary?.maxOrNull() ?: 0L)).toFloat()
        // yatay kılavuz çizgileri
        for (i in 0..2) {
            val y = topPad + chartH * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        val slot = size.width / n
        val pair = secondary != null
        val barW = (slot * if (pair) 0.34f else 0.58f).coerceAtMost(28.dp.toPx())
        val radius = CornerRadius(barW / 2.4f, barW / 2.4f)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            fun bar(v: Long, x: Float, c: Color) {
                if (v <= 0) return
                val h = (v / maxV) * chartH * anim.value
                drawRoundRect(c, Offset(x, topPad + chartH - h), Size(barW, h), radius)
            }
            val alpha = if (selected == null || selected == i) 1f else 0.45f
            if (pair) {
                bar(values[i], cx - barW - 1.dp.toPx(), color.copy(alpha = alpha))
                bar(secondary!![i], cx + 1.dp.toPx(), secondaryColor.copy(alpha = alpha))
            } else {
                bar(values[i], cx - barW / 2, color.copy(alpha = alpha))
            }
            if (i % labelEvery == 0 || i == selected) {
                val text = labels.getOrNull(i) ?: ""
                val m = measurer.measure(text, labelStyle)
                drawText(m, topLeft = Offset(cx - m.size.width / 2f, size.height - labelH + 3.dp.toPx()))
            }
        }
        if (selected != null && selected in 0 until n) {
            val v = values[selected]
            val text = valueLabel(v)
            if (text.isNotEmpty()) {
                val m = measurer.measure(text, TextStyle(fontSize = 11.sp, color = onSurface))
                val cx = slot * selected + slot / 2
                val x = (cx - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(x, 0f))
            }
        }
    }
}

/** Küçük ilerleme halkası (bütçe kullanım oranı için) */
@Composable
fun RingProgress(progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp, thickness: Dp = 5.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.size(size)) {
        val stroke = thickness.toPx()
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val tl = Offset(stroke / 2, stroke / 2)
        drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
