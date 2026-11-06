package com.butcemiz.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr-TR")

enum class PeriodKind { WEEK, MONTH, YEAR }

/** [start, endExclusive) aralığı */
data class Period(val kind: PeriodKind, val start: LocalDate, val endExclusive: LocalDate) {
    val startMillis: Long get() = start.atStartOfDay(Dates.zone).toInstant().toEpochMilli()
    val endMillis: Long get() = endExclusive.atStartOfDay(Dates.zone).toInstant().toEpochMilli()
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
    fun contains(millis: Long): Boolean = millis in startMillis until endMillis
    fun containsDate(d: LocalDate): Boolean = !d.isBefore(start) && d.isBefore(endExclusive)
}

object Periods {

    fun week(date: LocalDate): Period {
        val s = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return Period(PeriodKind.WEEK, s, s.plusDays(7))
    }

    /** startDay: ayın başlangıç günü (ör. maaş günü 15 ise 15'inden 14'üne kadar) */
    fun month(date: LocalDate, startDay: Int = 1): Period {
        val sd = startDay.coerceIn(1, 28)
        val ym = YearMonth.from(date)
        val startThis = ym.atDay(sd)
        val s = if (date.isBefore(startThis)) ym.minusMonths(1).atDay(sd) else startThis
        return Period(PeriodKind.MONTH, s, YearMonth.from(s).plusMonths(1).atDay(sd))
    }

    fun year(date: LocalDate): Period {
        val s = LocalDate.of(date.year, 1, 1)
        return Period(PeriodKind.YEAR, s, s.plusYears(1))
    }

    fun of(kind: PeriodKind, date: LocalDate, monthStartDay: Int): Period = when (kind) {
        PeriodKind.WEEK -> week(date)
        PeriodKind.MONTH -> month(date, monthStartDay)
        PeriodKind.YEAR -> year(date)
    }

    fun shift(p: Period, steps: Int, monthStartDay: Int): Period = when (p.kind) {
        PeriodKind.WEEK -> week(p.start.plusWeeks(steps.toLong()))
        PeriodKind.MONTH -> month(p.start.plusMonths(steps.toLong()), monthStartDay)
        PeriodKind.YEAR -> year(p.start.plusYears(steps.toLong()))
    }

    fun label(p: Period): String = when (p.kind) {
        PeriodKind.WEEK -> {
            val endIncl = p.endExclusive.minusDays(1)
            if (p.start.month == endIncl.month) "${p.start.dayOfMonth}–${endIncl.dayOfMonth} ${Dates.monthShort(endIncl)}"
            else "${p.start.dayOfMonth} ${Dates.monthShort(p.start)} – ${endIncl.dayOfMonth} ${Dates.monthShort(endIncl)}"
        }
        PeriodKind.MONTH -> {
            if (p.start.dayOfMonth == 1) Dates.monthYear(p.start)
            else {
                val endIncl = p.endExclusive.minusDays(1)
                "${p.start.dayOfMonth} ${Dates.monthShort(p.start)} – ${endIncl.dayOfMonth} ${Dates.monthShort(endIncl)}"
            }
        }
        PeriodKind.YEAR -> p.start.year.toString()
    }
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val monthYearFmt = DateTimeFormatter.ofPattern("LLLL yyyy", TR)
    private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMMM", TR)
    private val dayMonthYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", TR)
    private val dayMonthShortFmt = DateTimeFormatter.ofPattern("d MMM", TR)
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM", TR)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", TR)
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", TR)
    private val weekdayShortFmt = DateTimeFormatter.ofPattern("EEE", TR)
    private val fileFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", TR)
    private val csvFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", TR)

    fun today(): LocalDate = LocalDate.now(zone)
    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun toDateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun millisOf(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthYear(d: LocalDate): String = monthYearFmt.format(d).replaceFirstChar { it.titlecase(TR) }
    fun dayMonth(d: LocalDate): String = dayMonthFmt.format(d)
    fun dayMonthYear(d: LocalDate): String = dayMonthYearFmt.format(d)
    fun dayMonthShort(d: LocalDate): String = dayMonthShortFmt.format(d)
    fun monthShort(d: LocalDate): String = monthShortFmt.format(d).trimEnd('.')
    fun weekday(d: LocalDate): String = weekdayFmt.format(d).replaceFirstChar { it.titlecase(TR) }
    fun weekdayShort(d: LocalDate): String = weekdayShortFmt.format(d).trimEnd('.').replaceFirstChar { it.titlecase(TR) }
    fun time(millis: Long): String = timeFmt.format(toDateTime(millis))
    fun fileStamp(): String = fileFmt.format(LocalDateTime.now(zone))
    fun csv(millis: Long): String = csvFmt.format(toDateTime(millis))

    /** "Bugün", "Dün", "Salı, 3 Kasım" */
    fun dayHeader(d: LocalDate, today: LocalDate = today()): String = when (d) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        else -> if (d.year == today.year) "${weekday(d)}, ${dayMonth(d)}" else "${weekday(d)}, ${dayMonthYear(d)}"
    }

    /** Göreli zaman: "şimdi", "5 dk önce", "2 sa önce", "dün 14:20", "3 Kas 09:12" */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        val d = toDate(millis)
        val t = today()
        return when {
            diff < 0 -> "${dayMonthShort(d)} ${time(millis)}"
            diff < 60_000 -> "şimdi"
            diff < 3_600_000 -> "${diff / 60_000} dk önce"
            d == t -> "${diff / 3_600_000} sa önce"
            d == t.minusDays(1) -> "dün ${time(millis)}"
            d.year == t.year -> "${dayMonthShort(d)} ${time(millis)}"
            else -> "${dayMonthShort(d)} ${d.year}"
        }
    }

    /** Bir sonraki "ayın N. günü" tarihi (N ay uzunluğunu aşarsa ayın son günü) */
    fun nextMonthlyDate(dayOfMonth: Int, from: LocalDate = today()): LocalDate {
        val thisMonth = clampDay(YearMonth.from(from), dayOfMonth)
        return if (!thisMonth.isBefore(from)) thisMonth else clampDay(YearMonth.from(from).plusMonths(1), dayOfMonth)
    }

    fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

    fun daysUntil(d: LocalDate, from: LocalDate = today()): Long = ChronoUnit.DAYS.between(from, d)

    fun dueText(d: LocalDate, from: LocalDate = today()): String {
        val n = daysUntil(d, from)
        return when {
            n < -1 -> "${-n} gün gecikti"
            n == -1L -> "Dün son gündü"
            n == 0L -> "Bugün son gün"
            n == 1L -> "Yarın"
            n < 7 -> "$n gün sonra"
            else -> dayMonth(d)
        }
    }
}
