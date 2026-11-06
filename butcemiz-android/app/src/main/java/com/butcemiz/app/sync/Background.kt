package com.butcemiz.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.butcemiz.app.ButcemizApp
import com.butcemiz.app.notify.Notifier
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Ön plan servisi: uygulama kapalıyken de ntfy bağlantısını açık tutar, böylece eşinizin
 * girdiği harcama saniyeler içinde bildirim olarak gelir. Ayarlardan kapatılabilir.
 */
class SyncService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        val c = (application as ButcemizApp).container
        try {
            ServiceCompat.startForeground(
                this,
                Notifier.NOTIF_SYNC,
                c.notifier.syncNotification(c.engine.status.value),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return
        }
        c.engine.acquire(TAG)
        lifecycleScope.launch {
            var last: SyncEngine.State? = null
            c.engine.status.collect { st ->
                if (st.state != last) {
                    last = st.state
                    c.notifier.updateSync(st)
                }
            }
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        (application as ButcemizApp).container.engine.release(TAG)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "service"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))
            } catch (e: Exception) {
                // arka plandan başlatma kısıtı: WorkManager devam eder
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncService::class.java))
        }
    }
}

/** 15 dakikada bir: önbelleği çek, bekleyen değişiklikleri gönder (servis kapalıysa yedek yol). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ButcemizApp).container
        if (!c.prefs.onboarded) return Result.success()
        val ok = c.engine.syncOnce()
        return if (ok) Result.success() else Result.retry()
    }
}

/** Birkaç saatte bir: fatura & maaş günü hatırlatmaları, bütçe kontrolleri. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ButcemizApp).container
        c.reminders.checkSchedules()
        c.reminders.checkBudgets()
        return Result.success()
    }
}

object BackgroundWork {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS).build(),
        )
    }
}

/** Telefon açıldığında / uygulama güncellendiğinde arka plan senkronizasyonunu yeniden başlatır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val c = (context.applicationContext as? ButcemizApp)?.container ?: return
        BackgroundWork.schedule(context)
        if (c.prefs.onboarded && c.prefs.backgroundSync && c.prefs.syncConfig != null) SyncService.start(context)
    }
}
