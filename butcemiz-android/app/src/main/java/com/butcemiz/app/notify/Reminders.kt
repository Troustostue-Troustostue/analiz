package com.butcemiz.app.notify

import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Household
import com.butcemiz.app.data.Repository
import com.butcemiz.app.prefs.AppPrefs
import java.time.LocalDateTime

/** Fatura/maaş günü hatırlatmaları ve bütçe uyarıları. Aynı uyarı bir kez gönderilir. */
class Reminders(
    private val repo: Repository,
    private val prefs: AppPrefs,
    private val notifier: Notifier,
) {
    suspend fun checkSchedules(now: LocalDateTime = LocalDateTime.now(Dates.zone)) {
        if (!prefs.onboarded || !prefs.notifyReminders) return
        if (now.hour < 9 || now.hour >= 22) return
        val today = now.toLocalDate()
        val db = repo.db
        val payments = db.txnDao().scheduledPayments(Dates.startOfDay(today.minusDays(400)))

        val recurring = db.recurringDao().listActive()
        for (o in Calc.pendingOccurrences(recurring, payments, today, lookBackDays = 3, aheadDays = 10)) {
            val days = Dates.daysUntil(o.due, today)
            if (days > o.recurring.remindDaysBefore || days < -2) continue
            val phase = when {
                days < 0 -> "late"
                days == 0L -> "today"
                else -> "soon"
            }
            val key = "bill:${o.recurring.id}:${o.periodKey}:$phase"
            if (prefs.wasNotified(key)) continue
            prefs.markNotified(key)
            notifier.billReminder(o)
        }

        val members = db.memberDao().list().associateBy { it.id }
        val recent = db.txnDao().between(Dates.startOfDay(today.minusDays(40)), Dates.startOfDay(today.plusDays(1)))
        for (src in db.incomeSourceDao().listActive()) {
            // her telefon yalnızca kendi sahibinin maaşını hatırlatır
            if (src.memberId != null && prefs.myMemberId != null && src.memberId != prefs.myMemberId) continue
            val st = Calc.paydayStatus(src, payments + recent, today)
            if (st.received != null || st.due != today) continue
            val key = "pay:${src.id}:${st.periodKey}"
            if (prefs.wasNotified(key)) continue
            prefs.markNotified(key)
            notifier.paydayReminder(st, src.memberId?.let { members[it]?.name })
        }
    }

    suspend fun checkBudgets() {
        if (!prefs.onboarded || !prefs.notifyBudget) return
        val db = repo.db
        val household = db.householdDao().get(Household.HOUSEHOLD_ID) ?: Household()
        val p = Periods.month(Dates.today(), household.monthStartDay)
        val txns = db.txnDao().between(p.startMillis, p.endMillis)
        val views = Calc.budgetViews(db.budgetDao().list(), txns, p)
        if (views.isEmpty()) return
        val cats = db.categoryDao().list().associateBy { it.id }
        for (v in views) {
            val level = when {
                v.progress >= 1f -> 100
                v.progress >= 0.8f -> 80
                else -> continue
            }
            val key = "budget:${v.budget.id}:${p.start}:$level"
            if (prefs.wasNotified(key)) continue
            prefs.markNotified(key)
            if (level == 100) prefs.markNotified("budget:${v.budget.id}:${p.start}:80")
            val name = v.budget.categoryId?.let { cats[it]?.name } ?: "Aylık toplam"
            val pct = (v.progress * 100).toInt()
            val title = if (level == 100) "$name bütçesi aşıldı" else "$name bütçesi: %$pct kullanıldı"
            val text = "Bu dönem ${Money.format(v.spent)} harcandı · limit ${Money.format(v.budget.amount)}" +
                if (v.remaining >= 0) " · kalan ${Money.format(v.remaining)}" else " · aşım ${Money.format(-v.remaining)}"
            notifier.budgetAlert(title, text, key)
        }
    }
}
