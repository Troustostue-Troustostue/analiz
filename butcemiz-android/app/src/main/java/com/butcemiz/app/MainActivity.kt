package com.butcemiz.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.butcemiz.app.ui.components.LocalContainer
import com.butcemiz.app.ui.components.LocalHideAmounts
import com.butcemiz.app.ui.nav.Routes
import com.butcemiz.app.ui.onboarding.LockScreen
import com.butcemiz.app.ui.onboarding.OnboardingFlow
import com.butcemiz.app.ui.shell.AppShell
import com.butcemiz.app.ui.theme.ButcemizTheme

class MainActivity : FragmentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ButcemizApp
        val c = app.container
        locked.value = c.prefs.appLock && c.prefs.onboarded
        handleIntent(intent)

        setContent {
            val theme by c.prefs.themeFlow.collectAsStateWithLifecycle()
            val dynamic by c.prefs.dynamicColorFlow.collectAsStateWithLifecycle()
            val hide by c.prefs.hideAmountsFlow.collectAsStateWithLifecycle()
            val secure by c.prefs.secureScreenFlow.collectAsStateWithLifecycle()
            val onboarded by c.prefs.onboardedFlow.collectAsStateWithLifecycle()
            val isLocked by locked

            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            ButcemizTheme(theme, dynamic) {
                CompositionLocalProvider(LocalContainer provides c, LocalHideAmounts provides hide) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (onboarded) {
                            AppShell(pendingRoute)
                        } else {
                            OnboardingFlow(onFinished = { openInvite ->
                                c.engine.acquire("ui")
                                app.ensureBackgroundService()
                                if (openInvite) pendingRoute.value = Routes.SYNC
                            })
                        }
                        if (isLocked) LockScreen(onUnlock = ::authenticate)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val p = (application as ButcemizApp).container.prefs
        if (p.appLock && p.onboarded && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) {
            locked.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (locked.value) authenticate()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_EXPENSE -> pendingRoute.value = "add_expense"
            ACTION_ADD_INCOME -> pendingRoute.value = "add_income"
        }
        intent?.getStringExtra(EXTRA_ROUTE)?.let {
            pendingRoute.value = it
            intent.removeExtra(EXTRA_ROUTE)
        }
    }

    private fun authenticate() {
        if (authInProgress) return
        val prefs = (application as ButcemizApp).container.prefs
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Kilit yöntemi kaldırılmışsa kullanıcıyı dışarıda bırakma
            prefs.appLock = false
            locked.value = false
            return
        }
        authInProgress = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                        prefs.appLock = false
                        locked.value = false
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bütçemiz")
            .setSubtitle("Devam etmek için kimliğinizi doğrulayın")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ACTION_ADD_EXPENSE = "com.butcemiz.app.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.butcemiz.app.ADD_INCOME"
        private const val LOCK_AFTER_MS = 30_000L
    }
}
