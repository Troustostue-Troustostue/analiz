package com.butcemiz.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.butcemiz.app.ButcemizApp
import com.butcemiz.app.MainActivity
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.Money
import com.butcemiz.app.core.Periods
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.Household
import com.butcemiz.app.data.TxType

private data class WidgetData(
    val onboarded: Boolean,
    val hide: Boolean,
    val total: Long,
    val monthExpense: Long,
    val monthIncome: Long,
    val name: String,
)

/** Ana ekran widget'ı: toplam bakiye, bu ayın gelir/gideri ve hızlı ekleme düğmeleri. */
class BalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as ButcemizApp).container
        if (!c.prefs.onboarded) return WidgetData(false, false, 0, 0, 0, "Bütçemiz")
        val db = c.db
        val total = Calc.total(Calc.balances(db.accountDao().list(), db.txnDao().accountFlows()))
        val h = db.householdDao().get(Household.HOUSEHOLD_ID) ?: Household()
        val p = Periods.month(Dates.today(), h.monthStartDay)
        val txns = db.txnDao().between(p.startMillis, p.endMillis)
        return WidgetData(
            onboarded = true,
            hide = c.prefs.hideAmounts,
            total = total,
            monthExpense = txns.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            monthIncome = txns.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            name = h.name,
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val white = ColorProvider(Color.White)
        val soft = ColorProvider(Color(0xCCFFFFFF))
        fun money(v: Long) = if (d.hide) "•••• ₺" else Money.format(v, Money.Decimals.NEVER)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xFF0E7C66)).padding(14.dp)
                .clickable(actionStartActivity(open)),
        ) {
            Text(d.name, style = TextStyle(color = soft, fontSize = 12.sp))
            Text(
                if (d.onboarded) money(d.total) else "Kurulum bekleniyor",
                style = TextStyle(color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            if (d.onboarded) {
                Text(
                    "Bu ay  +${money(d.monthIncome)}  ·  ${Money.MINUS}${money(d.monthExpense)}",
                    style = TextStyle(color = soft, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                QuickButton("${Money.MINUS} Harcama", Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_EXPENSE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                QuickButton("+ Gelir", Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_INCOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun QuickButton(label: String, intent: Intent, modifier: GlanceModifier) {
        Box(
            modifier = modifier.height(36.dp).cornerRadius(18.dp).background(Color(0x33FFFFFF)).clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            BalanceWidget().updateAll(context)
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
