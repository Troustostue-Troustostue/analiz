package com.butcemiz.app

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.butcemiz.app.backup.BackupManager
import com.butcemiz.app.data.AppDatabase
import com.butcemiz.app.data.AppliedChange
import com.butcemiz.app.data.Calc
import com.butcemiz.app.data.OpLog
import com.butcemiz.app.data.Repository
import com.butcemiz.app.data.Txn
import com.butcemiz.app.notify.Notifier
import com.butcemiz.app.notify.Reminders
import com.butcemiz.app.prefs.AppPrefs
import com.butcemiz.app.rates.RatesRepository
import com.butcemiz.app.sync.NtfyTransport
import com.butcemiz.app.sync.RelayTransport
import com.butcemiz.app.sync.SyncEngine
import com.butcemiz.app.sync.SyncListener
import com.butcemiz.app.widget.BalanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Uygulama genelindeki tekil nesneler (basit bağımlılık kabı). */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = AppPrefs(app)
    val db: AppDatabase = AppDatabase.build(app)
    val notifier = Notifier(app)
    val http = OkHttpClient()
    val log = OpLog(db) { prefs.deviceId }

    /** Eşten gelen yeni hareketler (uygulama açıkken ekranda şerit göstermek için) */
    private val _remoteActivity = MutableSharedFlow<List<Txn>>(extraBufferCapacity = 8)
    val remoteActivity: SharedFlow<List<Txn>> = _remoteActivity.asSharedFlow()

    val repo: Repository = Repository(
        db = db,
        log = log,
        prefs = prefs,
        onLocalChange = { onLocalChange() },
        deviceInfo = { deviceName() to Build.MODEL },
        appVersion = BuildConfig.VERSION_NAME,
    )

    private val listener = object : SyncListener {
        override suspend fun onRemoteChanges(changes: List<AppliedChange>) {
            sideEffects.trySend(Unit)
            val now = System.currentTimeMillis()
            val fresh = changes
                .filter { it.entity == "txn" && it.isNew }
                .mapNotNull { it.after as? Txn }
                .filter { !it.deleted && now - it.createdAt < 48 * 3_600_000L && it.createdBy != prefs.deviceId }
                .sortedBy { it.createdAt }
            if (fresh.isEmpty()) return
            _remoteActivity.tryEmit(fresh)
            if (!prefs.notifyPartner || isForeground()) return
            val total = Calc.total(Calc.balances(db.accountDao().list(), db.txnDao().accountFlows()))
            val members = db.memberDao().all().associateBy { it.id }
            val cats = db.categoryDao().all().associateBy { it.id }
            if (fresh.size <= 3) {
                for (t in fresh) {
                    notifier.partnerTxn(members[t.memberId]?.name ?: "Eşiniz", t, t.categoryId?.let { cats[it]?.name }, total)
                }
            } else {
                val who = fresh.groupingBy { it.memberId }.eachCount().maxByOrNull { it.value }?.key
                notifier.partnerSummary(fresh.size, members[who]?.name ?: "Eşiniz", total)
            }
        }

        override suspend fun onPeerJoined(deviceName: String?) {
            notifier.peerJoined(deviceName)
        }
    }

    val transport: RelayTransport = transportOverride ?: NtfyTransport(http)
    val engine = SyncEngine(prefs, log, transport, listener, appScope, deviceName = { deviceName() })
    val rates = RatesRepository(db, prefs, http)
    val reminders = Reminders(repo, prefs, notifier)
    val backup = BackupManager(app, repo)

    private val sideEffects = Channel<Unit>(Channel.CONFLATED)

    init {
        appScope.launch {
            for (s in sideEffects) {
                delay(1_200)
                runCatching { reminders.checkBudgets() }
                runCatching { BalanceWidget.refresh(app) }
            }
        }
    }

    private fun onLocalChange() {
        engine.onLocalChange()
        sideEffects.trySend(Unit)
    }

    fun isForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        /** Yalnızca testler için: gerçek ntfy yerine sahte aktarıcı */
        @Volatile
        var transportOverride: RelayTransport? = null
    }

    fun deviceName(): String =
        runCatching { Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL)
}
