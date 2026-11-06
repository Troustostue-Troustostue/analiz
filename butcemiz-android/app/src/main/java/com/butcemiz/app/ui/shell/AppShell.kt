@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.butcemiz.app.data.TxType
import com.butcemiz.app.ui.assets.AccountDetailScreen
import com.butcemiz.app.ui.assets.AssetsScreen
import com.butcemiz.app.ui.assets.DebtDetailScreen
import com.butcemiz.app.ui.assets.SavingDetailScreen
import com.butcemiz.app.ui.home.HomeScreen
import com.butcemiz.app.ui.nav.Routes
import com.butcemiz.app.ui.nav.openAdd
import com.butcemiz.app.ui.nav.openTab
import com.butcemiz.app.ui.plan.BudgetsScreen
import com.butcemiz.app.ui.plan.IncomesScreen
import com.butcemiz.app.ui.plan.PaymentsScreen
import com.butcemiz.app.ui.settings.AboutScreen
import com.butcemiz.app.ui.settings.BackupScreen
import com.butcemiz.app.ui.settings.CategoriesScreen
import com.butcemiz.app.ui.settings.MembersScreen
import com.butcemiz.app.ui.settings.MoreScreen
import com.butcemiz.app.ui.settings.SettingsScreen
import com.butcemiz.app.ui.settings.SyncScreen
import com.butcemiz.app.ui.stats.StatsScreen
import com.butcemiz.app.ui.transactions.EditTxnScreen
import com.butcemiz.app.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch

/** Ekranlar arası kısa bildirim (snackbar): mesaj, isteğe bağlı eylem */
typealias Toast = (message: String, action: String?, onAction: (() -> Unit)?) -> Unit

val LocalToast = staticCompositionLocalOf<Toast> { { _, _, _ -> } }

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Ana sayfa", Icons.Rounded.Home),
    Tab(Routes.TXNS, "Hareketler", Icons.Rounded.ReceiptLong),
    Tab(Routes.STATS, "Analiz", Icons.Rounded.PieChart),
    Tab(Routes.ASSETS, "Varlıklar", Icons.Rounded.AccountBalanceWallet),
    Tab(Routes.MORE, "Diğer", Icons.Rounded.GridView),
)

@Composable
fun AppShell(pendingRoute: MutableState<String?>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: Toast = remember {
        { msg, action, onAction ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(msg, actionLabel = action, withDismissAction = action == null, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
            }
        }
    }

    LaunchedEffect(pendingRoute.value) {
        val r = pendingRoute.value ?: return@LaunchedEffect
        pendingRoute.value = null
        when (r) {
            "add_expense" -> nav.openAdd(TxType.EXPENSE)
            "add_income" -> nav.openAdd(TxType.INCOME)
            Routes.HOME -> nav.openTab(Routes.HOME)
            else -> runCatching { nav.navigate(r) }
        }
    }

    CompositionLocalProvider(LocalToast provides toast) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (route in Routes.tabs) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = { if (route != t.route) nav.openTab(t.route) },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
                NavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(nav) }
                    composable(Routes.TXNS) { TransactionsScreen(nav) }
                    composable(Routes.STATS) { StatsScreen(nav) }
                    composable(Routes.ASSETS) { AssetsScreen(nav) }
                    composable(Routes.MORE) { MoreScreen(nav) }
                    composable(
                        Routes.ADD,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                            navArgument("id") { type = NavType.StringType; defaultValue = "" },
                            navArgument("account") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { e ->
                        val type = runCatching { TxType.valueOf(e.arguments?.getString("type") ?: "EXPENSE") }.getOrDefault(TxType.EXPENSE)
                        val id = e.arguments?.getString("id")?.takeIf { it.isNotBlank() }
                        val acc = e.arguments?.getString("account")?.takeIf { it.isNotBlank() }
                        EditTxnScreen(nav, type, id, acc)
                    }
                    composable(Routes.ACCOUNT) { e -> AccountDetailScreen(nav, e.arguments?.getString("id").orEmpty()) }
                    composable(Routes.SAVING) { e -> SavingDetailScreen(nav, e.arguments?.getString("id").orEmpty()) }
                    composable(Routes.DEBT) { e -> DebtDetailScreen(nav, e.arguments?.getString("id").orEmpty()) }
                    composable(Routes.PAYMENTS) { PaymentsScreen(nav) }
                    composable(Routes.INCOMES) { IncomesScreen(nav) }
                    composable(Routes.BUDGETS) { BudgetsScreen(nav) }
                    composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
                    composable(Routes.MEMBERS) { MembersScreen(nav) }
                    composable(Routes.SYNC) { SyncScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable(Routes.BACKUP) { BackupScreen(nav) }
                    composable(Routes.ABOUT) { AboutScreen(nav) }
                }
            }
        }
    }
}
