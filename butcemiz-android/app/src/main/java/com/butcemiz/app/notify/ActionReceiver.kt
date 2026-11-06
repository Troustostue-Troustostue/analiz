package com.butcemiz.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.butcemiz.app.ButcemizApp
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Seeds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Bildirimdeki "Ödendi" / "Yattı, ekle" düğmeleri. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ButcemizApp ?: return
        val c = app.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_PAY_BILL -> {
                        val id = intent.getStringExtra(EXTRA_ID) ?: return@launch
                        val period = intent.getStringExtra(EXTRA_PERIOD) ?: return@launch
                        val r = c.db.recurringDao().get(id) ?: return@launch
                        c.repo.payRecurring(r, period, r.amount, r.accountId ?: Seeds.MAIN_ACCOUNT_ID, c.prefs.myMemberId, System.currentTimeMillis())
                        NotificationManagerCompat.from(context).cancel(("bill" + r.id + period).hashCode())
                        val total = c.repo.totalBalance.first()
                        c.notifier.simple(("paid" + r.id).hashCode(), "${r.name} ödendi ✓", "Yeni bakiye: ${Money.format(total)}")
                    }
                    ACTION_RECORD_INCOME -> {
                        val id = intent.getStringExtra(EXTRA_ID) ?: return@launch
                        val period = intent.getStringExtra(EXTRA_PERIOD) ?: return@launch
                        val s = c.db.incomeSourceDao().get(id) ?: return@launch
                        c.repo.recordIncome(s, period, s.amount, s.accountId, System.currentTimeMillis())
                        NotificationManagerCompat.from(context).cancel(("pay" + s.id + period).hashCode())
                        val total = c.repo.totalBalance.first()
                        c.notifier.simple(("inc" + s.id).hashCode(), "${s.name} eklendi ✓", "+${Money.format(s.amount)} · Yeni bakiye: ${Money.format(total)}")
                    }
                }
            } catch (e: Exception) {
                // sessizce geç
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAY_BILL = "com.butcemiz.app.PAY_BILL"
        const val ACTION_RECORD_INCOME = "com.butcemiz.app.RECORD_INCOME"
        private const val EXTRA_ID = "id"
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_DAY = "day"

        fun payBillIntent(context: Context, recurringId: String, period: String, dueEpochDay: Long): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).apply {
                action = ACTION_PAY_BILL
                putExtra(EXTRA_ID, recurringId)
                putExtra(EXTRA_PERIOD, period)
                putExtra(EXTRA_DAY, dueEpochDay)
            }
            return PendingIntent.getBroadcast(
                context, (recurringId + period).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun recordIncomeIntent(context: Context, sourceId: String, period: String): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).apply {
                action = ACTION_RECORD_INCOME
                putExtra(EXTRA_ID, sourceId)
                putExtra(EXTRA_PERIOD, period)
            }
            return PendingIntent.getBroadcast(
                context, (sourceId + period).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
