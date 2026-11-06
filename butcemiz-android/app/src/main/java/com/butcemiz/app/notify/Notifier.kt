package com.butcemiz.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.butcemiz.app.MainActivity
import com.butcemiz.app.R
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Occurrence
import com.butcemiz.app.data.PaydayStatus
import com.butcemiz.app.data.Txn
import com.butcemiz.app.data.TxType
import com.butcemiz.app.sync.SyncEngine

class Notifier(private val context: Context) {

    companion object {
        const val CH_PARTNER = "partner_activity"
        const val CH_REMINDERS = "reminders"
        const val CH_BUDGET = "budget_alerts"
        const val CH_SYNC = "sync_service"
        const val NOTIF_SYNC = 1001
        private const val GROUP_PARTNER = "com.butcemiz.PARTNER"

        private const val COLOR_EXPENSE = 0xFFE5484D.toInt()
        private const val COLOR_INCOME = 0xFF12A150.toInt()
        private const val COLOR_BRAND = 0xFF0E7C66.toInt()
    }

    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannels(
            listOf(
                NotificationChannel(CH_PARTNER, "Eşimin harcama ve gelirleri", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Eşiniz harcama ya da gelir girdiğinde anında bildirim"
                },
                NotificationChannel(CH_REMINDERS, "Fatura ve maaş hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Yaklaşan ödemeler ve maaş günü"
                },
                NotificationChannel(CH_BUDGET, "Bütçe uyarıları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aylık bütçe limitine yaklaşınca uyarı"
                },
                NotificationChannel(CH_SYNC, "Arka plan senkronizasyonu", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Anlık bildirimler için arka planda açık bağlantı"
                    setShowBadge(false)
                },
            ),
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(route: String? = null, requestCode: Int = 0): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, n: Notification) {
        if (!canPost()) return
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // izin kaldırılmış olabilir
        }
    }

    /** "Alperen harcama yaptı · −2.000 ₺" */
    fun partnerTxn(memberName: String, t: Txn, categoryName: String?, newBalance: Long?) {
        val isExpense = t.type == TxType.EXPENSE || t.type == TxType.SAVING_DEPOSIT || t.type == TxType.LOAN_GIVEN
        val verb = when (t.type) {
            TxType.EXPENSE -> if (t.debtId != null) "borç ödemesi yaptı" else "harcama yaptı"
            TxType.INCOME -> "gelir ekledi"
            TxType.TRANSFER -> "transfer yaptı"
            TxType.SAVING_DEPOSIT -> "birikime ekledi"
            TxType.SAVING_WITHDRAW -> "birikimden çekti"
            TxType.ADJUSTMENT -> "bakiyeyi düzeltti"
            TxType.LOAN_RECEIVED -> "borç aldı"
            TxType.LOAN_GIVEN -> "borç verdi"
        }
        val amountText = when (t.type) {
            TxType.ADJUSTMENT -> Money.format(t.amount, sign = true)
            TxType.TRANSFER -> Money.format(t.amount)
            else -> (if (isExpense) Money.MINUS else "+") + Money.format(t.amount)
        }
        val what = listOfNotNull(t.title.takeIf { it.isNotBlank() }, categoryName?.takeIf { it != t.title }).joinToString(" · ")
        val title = "$memberName $verb · $amountText"
        val line1 = if (what.isNotBlank()) (if (t.type == TxType.EXPENSE) "$what için" else what) else t.type.label
        val balanceLine = newBalance?.let { "Yeni bakiye: ${Money.format(it)}" }
        val body = listOfNotNull(line1, balanceLine).joinToString("\n")
        val n = NotificationCompat.Builder(context, CH_PARTNER)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(if (isExpense) COLOR_EXPENSE else COLOR_INCOME)
            .setContentTitle(title)
            .setContentText(listOfNotNull(line1, balanceLine).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent("home", 10))
            .setAutoCancel(true)
            .setGroup(GROUP_PARTNER)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(t.id.hashCode(), n)
    }

    fun partnerSummary(count: Int, memberName: String, newBalance: Long?) {
        val n = NotificationCompat.Builder(context, CH_PARTNER)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_BRAND)
            .setContentTitle("$memberName $count yeni hareket ekledi")
            .setContentText(newBalance?.let { "Güncel bakiye: ${Money.format(it)}" } ?: "Ayrıntılar için dokunun")
            .setContentIntent(openAppIntent("home", 11))
            .setAutoCancel(true)
            .setGroup(GROUP_PARTNER)
            .build()
        post(2001, n)
    }

    fun peerJoined(deviceName: String?) {
        val n = NotificationCompat.Builder(context, CH_PARTNER)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_BRAND)
            .setContentTitle("Eşleşme tamamlandı 🎉")
            .setContentText(
                (deviceName?.let { "$it bağlandı. " } ?: "") + "Artık harcama ve gelirler iki telefonda anında görünecek.",
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    (deviceName?.let { "$it bağlandı. " } ?: "") + "Artık harcama ve gelirler iki telefonda anında görünecek.",
                ),
            )
            .setContentIntent(openAppIntent("home", 12))
            .setAutoCancel(true)
            .build()
        post(2002, n)
    }

    fun billReminder(o: Occurrence) {
        val r = o.recurring
        val due = Dates.dueText(o.due)
        val amount = if (r.amount > 0) (if (r.variable) "≈ " else "") + Money.format(r.amount) else null
        val builder = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_BRAND)
            .setContentTitle("${r.name}: $due")
            .setContentText(listOfNotNull(amount, "Son gün ${Dates.dayMonth(o.due)}").joinToString(" · "))
            .setContentIntent(openAppIntent("payments", 20))
            .setAutoCancel(true)
        if (r.amount > 0 && !r.variable) {
            builder.addAction(
                0, "Ödendi olarak işaretle",
                ActionReceiver.payBillIntent(context, r.id, o.periodKey, o.due.toEpochDay()),
            )
        }
        post(("bill" + r.id + o.periodKey).hashCode(), builder.build())
    }

    fun paydayReminder(st: PaydayStatus, memberName: String?) {
        val s = st.source
        val who = memberName?.let { "$it · " } ?: ""
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_INCOME)
            .setContentTitle("Bugün ${s.name.lowercase(com.butcemiz.app.core.TR)} günü 🎉")
            .setContentText("$who${Money.format(s.amount)} yattıysa tek dokunuşla ekleyin")
            .setContentIntent(openAppIntent("incomes", 21))
            .setAutoCancel(true)
            .addAction(0, "Yattı, ekle", ActionReceiver.recordIncomeIntent(context, s.id, st.periodKey))
            .build()
        post(("pay" + s.id + st.periodKey).hashCode(), n)
    }

    fun budgetAlert(title: String, text: String, key: String) {
        val n = NotificationCompat.Builder(context, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_EXPENSE)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent("budgets", 22))
            .setAutoCancel(true)
            .build()
        post(key.hashCode(), n)
    }

    fun simple(id: Int, title: String, text: String, channel: String = CH_REMINDERS) {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(null, 23))
            .setAutoCancel(true)
            .build()
        post(id, n)
    }

    fun syncNotification(status: SyncEngine.Status): Notification {
        val text = when (status.state) {
            SyncEngine.State.CONNECTED -> "Eşinizle bağlı · değişiklikler anında gelir"
            SyncEngine.State.WAITING_PARTNER -> "Eşinizin katılması bekleniyor"
            SyncEngine.State.CONNECTING -> "Bağlanıyor…"
            SyncEngine.State.OFFLINE -> "İnternet bekleniyor"
            SyncEngine.State.QUOTA -> "Gönderim sınırı, kısa süre sonra devam edecek"
            SyncEngine.State.NOT_CONFIGURED -> "Eşleştirme yapılmadı"
            SyncEngine.State.IDLE -> "Hazır"
        }
        return NotificationCompat.Builder(context, CH_SYNC)
            .setSmallIcon(R.drawable.ic_stat_butcemiz)
            .setColor(COLOR_BRAND)
            .setContentTitle("Bütçemiz senkronizasyonu")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent("sync", 24))
            .build()
    }

    fun updateSync(status: SyncEngine.Status) {
        post(NOTIF_SYNC, syncNotification(status))
    }
}
