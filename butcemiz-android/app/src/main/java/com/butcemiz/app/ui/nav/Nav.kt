package com.butcemiz.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.butcemiz.app.AppContainer
import com.butcemiz.app.ButcemizApp
import com.butcemiz.app.data.TxType

object Routes {
    const val HOME = "home"
    const val TXNS = "transactions"
    const val STATS = "stats"
    const val ASSETS = "assets"
    const val MORE = "more"
    const val ADD = "add?type={type}&id={id}&account={account}"
    const val ACCOUNT = "account/{id}"
    const val SAVING = "saving/{id}"
    const val DEBT = "debt/{id}"
    const val PAYMENTS = "payments"
    const val INCOMES = "incomes"
    const val BUDGETS = "budgets"
    const val CATEGORIES = "categories"
    const val MEMBERS = "members"
    const val SYNC = "sync"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val ABOUT = "about"

    val tabs = setOf(HOME, TXNS, STATS, ASSETS, MORE)
}

fun NavController.openAdd(type: TxType = TxType.EXPENSE, id: String? = null, accountId: String? = null) {
    navigate("add?type=${type.name}&id=${id.orEmpty()}&account=${accountId.orEmpty()}")
}

fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ButcemizApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
