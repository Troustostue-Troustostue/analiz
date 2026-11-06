package com.butcemiz.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Para birimi yardımcıları. Tüm tutarlar uygulama içinde kuruş (Long) olarak tutulur:
 * 1 ₺ = 100 kuruş. Böylece ondalık yuvarlama hataları oluşmaz.
 */
object Money {

    enum class Decimals { AUTO, ALWAYS, NEVER }

    const val MINUS = "−" // U+2212, tipografik eksi

    fun format(
        kurus: Long,
        decimals: Decimals = Decimals.AUTO,
        sign: Boolean = false,
        symbol: Boolean = true,
    ): String {
        val negative = kurus < 0
        var absK = abs(kurus)
        val showDec = when (decimals) {
            Decimals.ALWAYS -> true
            Decimals.NEVER -> false
            Decimals.AUTO -> absK % 100 != 0L
        }
        if (!showDec) absK = ((absK + 50) / 100) * 100
        val lira = absK / 100
        val k = absK % 100
        val sb = StringBuilder()
        if (negative && absK != 0L) sb.append(MINUS) else if (sign && kurus > 0) sb.append('+')
        sb.append(group(lira))
        if (showDec) {
            sb.append(',')
            sb.append(k.toString().padStart(2, '0'))
        }
        if (symbol) sb.append(" ₺")
        return sb.toString()
    }

    /** Grafik eksenleri ve dar alanlar için: 12.500 ₺ → "12,5 bin" */
    fun compact(kurus: Long, symbol: Boolean = false): String {
        val lira = kurus / 100.0
        val a = abs(lira)
        val prefix = if (lira < 0) MINUS else ""
        val body = when {
            a >= 1_000_000_000 -> trim(a / 1_000_000_000) + " mr"
            a >= 1_000_000 -> trim(a / 1_000_000) + " mn"
            a >= 10_000 -> trim(a / 1_000) + " bin"
            else -> group(Math.round(a))
        }
        return prefix + body + if (symbol) " ₺" else ""
    }

    private fun trim(v: Double): String {
        val bd = BigDecimal(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString().replace('.', ',')
    }

    fun group(n: Long): String {
        val s = abs(n).toString()
        val sb = StringBuilder(if (n < 0) "-" else "")
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(s[i])
        }
        return sb.toString()
    }

    /**
     * Kullanıcı girdisini kuruşa çevirir. Kabul edilen biçimler:
     * "2000", "2.000", "2000,50", "2.000,50", "2000.5", "₺ 2.000", "2 000"
     * Yalnızca nokta varsa ve noktadan sonra tam 3 rakam varsa binlik ayırıcı kabul edilir.
     */
    fun parse(input: String): Long? {
        var s = input.trim()
            .replace("₺", "")
            .replace("TL", "", ignoreCase = true)
            .replace(" ", "")
            .replace(" ", "")
            .replace(MINUS, "-")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        s = s.removePrefix("-").removePrefix("+")
        if (s.isEmpty() || s.any { !(it.isDigit() || it == '.' || it == ',') }) return null
        val hasDot = '.' in s
        val hasComma = ',' in s
        val normalized: String = when {
            hasDot && hasComma -> {
                // Son görülen ayırıcı ondalıktır.
                val lastDot = s.lastIndexOf('.')
                val lastComma = s.lastIndexOf(',')
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            }
            hasComma -> {
                if (s.count { it == ',' } > 1) s.replace(",", "") else s.replace(',', '.')
            }
            hasDot -> {
                val parts = s.split('.')
                if (parts.size > 2 || (parts.size == 2 && parts[1].length == 3)) s.replace(".", "") else s
            }
            else -> s
        }
        if (normalized.isEmpty() || normalized == ".") return null
        return try {
            val bd = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
            val k = bd.movePointRight(2).longValueExact()
            if (negative) -k else k
        } catch (e: Exception) {
            null
        }
    }

    /** Kuruşu düzenleme alanına yazılacak ham metne çevirir (binlik ayırıcısız): 200050 → "2000,5" */
    fun toEditable(kurus: Long): String {
        if (kurus == 0L) return ""
        val a = abs(kurus)
        val lira = a / 100
        val k = a % 100
        val base = if (k == 0L) lira.toString() else lira.toString() + "," + k.toString().padStart(2, '0').trimEnd('0')
        return if (kurus < 0) "-$base" else base
    }

    /** Miktar biçimlendirme (birikim adetleri, gram vb.): 10.5 → "10,5" ; 1500.0 → "1.500" */
    fun quantity(value: Double, maxDecimals: Int = 3): String {
        val bd = BigDecimal(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = bd.toPlainString()
        val negative = plain.startsWith("-")
        val p = plain.removePrefix("-")
        val intPart = p.substringBefore('.')
        val frac = if ('.' in p) p.substringAfter('.') else ""
        val grouped = group(intPart.toLongOrNull() ?: 0L)
        return (if (negative) MINUS else "") + grouped + if (frac.isNotEmpty()) ",$frac" else ""
    }

    /** Miktar girdisi: "10,5" → 10.5 ; "1.500" → 1500 */
    fun parseQuantity(input: String): Double? {
        val k = parse(input) ?: run {
            // 3'ten fazla ondalık basamak (ör. kripto) için doğrudan dene
            val s = input.trim().replace(" ", "").replace(',', '.')
            return s.toDoubleOrNull()
        }
        // parse 2 basamağa yuvarlar; daha hassas miktarlar için ham değeri dene
        val raw = input.trim().replace(" ", "")
        val decimalsGiven = raw.substringAfterLast(',', "").let { if (it.length > 2 && !raw.contains('.')) it.length else 0 }
        if (decimalsGiven > 2) return raw.replace(',', '.').toDoubleOrNull()
        return k / 100.0
    }

    /** Ondalıklı TL değerini kuruşa çevirir. */
    fun fromLira(value: Double): Long = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    fun percent(part: Long, whole: Long): Float = if (whole <= 0L) 0f else (part.toDouble() / whole.toDouble()).toFloat()
}
