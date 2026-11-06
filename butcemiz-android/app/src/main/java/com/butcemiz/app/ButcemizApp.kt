package com.butcemiz.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.butcemiz.app.sync.BackgroundWork
import com.butcemiz.app.sync.SyncService
import kotlinx.coroutines.launch

class ButcemizApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        BackgroundWork.schedule(this)

        // Uygulama ön plandayken canlı bağlantı açık
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!container.prefs.onboarded) return
                container.engine.acquire("ui")
                ensureBackgroundService()
                container.appScope.launch {
                    runCatching { container.reminders.checkSchedules() }
                    runCatching { container.rates.refreshIfStale() }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.engine.release("ui")
            }
        })

        // İnternet geri gelince hemen yeniden bağlan
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        container.engine.poke()
                    }
                },
            )
        }
    }

    fun ensureBackgroundService() {
        val p = container.prefs
        if (p.onboarded && p.backgroundSync && p.syncConfig != null) SyncService.start(this) else SyncService.stop(this)
    }
}
