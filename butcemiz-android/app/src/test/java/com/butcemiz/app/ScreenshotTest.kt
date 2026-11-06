package com.butcemiz.app

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.butcemiz.app.core.Dates
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AccountType
import com.butcemiz.app.data.Budget
import com.butcemiz.app.data.Debt
import com.butcemiz.app.data.DebtDirection
import com.butcemiz.app.data.DebtKind
import com.butcemiz.app.data.Frequency
import com.butcemiz.app.data.IncomeKind
import com.butcemiz.app.data.IncomeSource
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.SavingType
import com.butcemiz.app.data.Seeds
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import com.butcemiz.app.prefs.ThemeMode
import com.butcemiz.app.rates.RatesRepository
import com.butcemiz.app.sync.PairingCode
import com.butcemiz.app.ui.components.LocalContainer
import com.butcemiz.app.ui.components.LocalHideAmounts
import com.butcemiz.app.ui.nav.Routes
import com.butcemiz.app.ui.onboarding.OnboardingFlow
import com.butcemiz.app.ui.shell.AppShell
import com.butcemiz.app.ui.theme.ButcemizTheme
import com.butcemiz.app.ui.theme.Palette
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Testte gerçek ntfy yerine sahte aktarıcı kullanan uygulama */
class ShotApp : ButcemizApp() {
    override fun onCreate() {
        AppContainer.transportOverride = FakeRelay()
        super.onCreate()
    }
}

/**
 * Ekran görüntüleri (S23 FE / S24 FE boyutunda: 411x891 dp). Çıktı: proje kökünde screenshots/.
 * Çalıştırma: ./gradlew testDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = ShotApp::class)
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var c: AppContainer
    private val outDir = File(System.getProperty("butcemiz.shots") ?: "build/screenshots").apply { mkdirs() }

    private fun day(d: LocalDate, h: Int, m: Int = 0) = Dates.millisOf(d, LocalTime.of(h, m))

    @Before
    fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ButcemizApp>()
        c = app.container
        val p = c.prefs
        p.setFlag("notif_asked")
        p.backgroundSync = false
        p.lastRatesFetch = System.currentTimeMillis()
        p.syncConfig = PairingCode.newConfig()
        val repo = c.repo
        repo.createHousehold("Evimizin bütçesi", "Alperen", "Ayşe", 120_000_00, 85_000_00, 15, Palette.members[0] to Palette.members[1])
        p.paired = true
        p.lastPeerSeen = System.currentTimeMillis() - 2 * 60_000
        val members = c.db.memberDao().list()
        val alperen = members.first { it.name == "Alperen" }.id
        val ayse = members.first { it.name == "Ayşe" }.id
        repo.registerThisDevice(alperen)

        val cash = repo.saveAccount(Account("", "Cüzdan", AccountType.CASH, openingBalance = 3_500_00, color = 0xFF16A34A))
        repo.saveAccount(Account("", "Bonus Kart", AccountType.CREDIT_CARD, openingBalance = -4_250_00, includeInTotal = false, creditLimit = 60_000_00, color = 0xFF7C3AED))

        val today = Dates.today()
        val main = Seeds.MAIN_ACCOUNT_ID
        suspend fun tx(type: TxType, amount: Long, title: String, cat: String?, who: String, at: Long, acc: String = main) =
            repo.saveTxn(Txn("", type, amount, title, cat, acc, null, who, at, createdAt = at, createdBy = p.deviceId))

        // Geçmiş aylar (trend için)
        for (back in 1..5L) {
            val d = today.minusMonths(back).withDayOfMonth(3)
            tx(TxType.INCOME, 85_000_00, "Maaş", Seeds.CAT_SALARY, alperen, day(d.withDayOfMonth(15), 10))
            tx(TxType.INCOME, 62_000_00, "Maaş", Seeds.CAT_SALARY, ayse, day(d.withDayOfMonth(1), 9))
            tx(TxType.EXPENSE, 18_500_00, "Kira", Seeds.CAT_RENT, alperen, day(d.withDayOfMonth(1), 12))
            tx(TxType.EXPENSE, (14_000_00 + back * 1_250_00), "Market alışverişleri", Seeds.CAT_MARKET, ayse, day(d, 18))
            tx(TxType.EXPENSE, (6_000_00 + back * 700_00), "Faturalar", Seeds.CAT_BILLS, alperen, day(d.withDayOfMonth(12), 9))
            tx(TxType.EXPENSE, (9_000_00 - back * 600_00), "Yeme içme", Seeds.CAT_FOOD, alperen, day(d.withDayOfMonth(20), 21))
        }
        // Bu ay
        val d1 = today.withDayOfMonth(1)
        tx(TxType.INCOME, 62_000_00, "Maaş", Seeds.CAT_SALARY, ayse, day(d1, 9))
        tx(TxType.EXPENSE, 18_500_00, "Kira", Seeds.CAT_RENT, alperen, day(d1, 12))
        tx(TxType.EXPENSE, 3_200_00, "Mont", "cat_giyim", ayse, day(today.minusDays(4).coerceAtLeast(d1), 15, 20))
        tx(TxType.EXPENSE, 2_150_00, "Doğalgaz", Seeds.CAT_BILLS, alperen, day(today.minusDays(3).coerceAtLeast(d1), 10, 5))
        tx(TxType.EXPENSE, 1_240_00, "Elektrik", Seeds.CAT_BILLS, alperen, day(today.minusDays(3).coerceAtLeast(d1), 10, 9))
        tx(TxType.EXPENSE, 2_800_00, "Akaryakıt", Seeds.CAT_TRANSPORT, alperen, day(today.minusDays(2).coerceAtLeast(d1), 8, 40))
        tx(TxType.EXPENSE, 1_350_00, "Akşam yemeği", Seeds.CAT_FOOD, ayse, day(today.minusDays(2).coerceAtLeast(d1), 21, 15))
        tx(TxType.INCOME, 6_500_00, "Ek ders ücreti", Seeds.CAT_EXTRA_INCOME, alperen, day(today.minusDays(1).coerceAtLeast(d1), 11, 30))
        tx(TxType.EXPENSE, 640_00, "A101", Seeds.CAT_MARKET, ayse, day(today.minusDays(1).coerceAtLeast(d1), 19, 2))
        tx(TxType.EXPENSE, 420_00, "Eczane", "cat_saglik", ayse, day(today, 9, 12))
        tx(TxType.EXPENSE, 185_00, "Kahve", Seeds.CAT_FOOD, alperen, day(today, 10, 25), cash.id)
        tx(TxType.EXPENSE, 2_000_00, "Migros", Seeds.CAT_MARKET, alperen, System.currentTimeMillis() - 4 * 60_000)

        // Birikimler + kurlar
        val prices = RatesRepository.parseTruncgil(javaClass.classLoader!!.getResource("truncgil_v3.json")!!.readText(), System.currentTimeMillis() - 20 * 60_000)
        c.db.priceDao().upsertAll(prices)
        repo.saveSaving(Saving("", "Düğün altınları", SavingType.GOLD_QUARTER, initialQty = 12.0, ownerMemberId = ayse))
        repo.saveSaving(Saving("", "Gram altın", SavingType.GOLD_GRAM, initialQty = 25.0, costBasis = 120_000_00))
        repo.saveSaving(Saving("", "Dolar", SavingType.USD, initialQty = 1_500.0))
        repo.saveSaving(Saving("", "Vadeli mevduat", SavingType.TRY, initialQty = 50_000.0, targetAmount = 100_000_00))
        repo.saveSaving(Saving("", "BES", SavingType.BES, initialQty = 34_000.0, ownerMemberId = alperen))

        // Borçlar
        val loan = repo.saveDebt(Debt("", "Konut kredisi", DebtDirection.I_OWE, DebtKind.LOAN, "Ziraat", principal = 850_000_00, alreadyPaid = 210_000_00, installmentAmount = 14_750_00, installmentCount = 120, dueDay = 5))
        repo.saveDebt(Debt("", "Telefon taksiti", DebtDirection.I_OWE, DebtKind.INSTALLMENT, "Bonus Kart", principal = 36_000_00, alreadyPaid = 9_000_00, installmentAmount = 3_000_00, installmentCount = 12, dueDay = 20))
        repo.saveDebt(Debt("", "Mehmet'e verilen", DebtDirection.OWED_TO_ME, DebtKind.PERSONAL, "Mehmet", principal = 5_000_00, alreadyPaid = 1_000_00))

        // Düzenli ödemeler
        val created = day(today.minusMonths(2), 9)
        repo.saveRecurring(Recurring("", "Kira", 18_500_00, categoryId = Seeds.CAT_RENT, dayOfMonth = 1, createdAt = created, startDate = created))
        repo.saveRecurring(Recurring("", "İnternet", 449_90, categoryId = Seeds.CAT_BILLS, dayOfMonth = today.plusDays(2).dayOfMonth.coerceAtMost(28), createdAt = created, startDate = created))
        repo.saveRecurring(Recurring("", "Aidat", 1_500_00, categoryId = Seeds.CAT_RENT, dayOfMonth = today.plusDays(4).dayOfMonth.coerceAtMost(28), createdAt = created, startDate = created))
        repo.saveRecurring(Recurring("", "Netflix", 229_99, categoryId = Seeds.CAT_SUBSCRIPTION, dayOfMonth = 18, createdAt = created, startDate = created))
        repo.saveRecurring(Recurring("", "Konut kredisi taksidi", 14_750_00, categoryId = Seeds.CAT_DEBT, dayOfMonth = 5, debtId = loan.id, createdAt = created, startDate = created, frequency = Frequency.MONTHLY))
        val rent = c.db.recurringDao().listActive().first { it.name == "Kira" }
        repo.payRecurring(rent, com.butcemiz.app.data.Calc.monthKey(today), 18_500_00, main, alperen, day(d1, 12))

        repo.saveIncomeSource(IncomeSource("", "Maaş", IncomeKind.SALARY, ayse, 62_000_00, 1, main, Seeds.CAT_SALARY))

        // Bütçeler
        repo.setBudget(null, 60_000_00)
        repo.setBudget(Seeds.CAT_MARKET, 12_000_00)
        repo.setBudget(Seeds.CAT_FOOD, 4_000_00)
        c.engine.acquire("ui")
        Unit
    }

    private fun LocalDate.coerceAtLeast(min: LocalDate) = if (this.isBefore(min)) min else this

    private fun capture(name: String) {
        rule.waitForIdle()
        repeat(8) {
            rule.mainClock.advanceTimeBy(400)
            Thread.sleep(150)
            rule.waitForIdle()
        }
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, route: String? = null, theme: ThemeMode = ThemeMode.LIGHT, hide: Boolean = false) {
        val pending = mutableStateOf(route)
        rule.setContent {
            ButcemizTheme(theme) {
                CompositionLocalProvider(LocalContainer provides c, LocalHideAmounts provides hide) {
                    AppShell(pending)
                }
            }
        }
        capture(name)
    }

    @Test fun home() = shot("01_ana_sayfa")
    @Test fun homeDark() = shot("02_ana_sayfa_koyu", theme = ThemeMode.DARK)
    @Test fun addExpense() = shot("03_harcama_ekle", "add_expense")
    @Test fun transactions() = shot("04_hareketler", Routes.TXNS)
    @Test fun stats() = shot("05_analiz", Routes.STATS)
    @Test fun assets() = shot("06_varliklar", Routes.ASSETS)
    @Test fun payments() = shot("07_duzenli_odemeler", Routes.PAYMENTS)
    @Test fun incomes() = shot("08_maas_gelirler", Routes.INCOMES)
    @Test fun sync() = shot("09_eslestirme", Routes.SYNC)
    @Test fun more() = shot("10_diger", Routes.MORE)
    @Test fun budgets() = shot("11_butceler", Routes.BUDGETS)

    @Test
    fun onboarding() {
        rule.setContent {
            ButcemizTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalContainer provides c) { OnboardingFlow(onFinished = {}) }
            }
        }
        capture("00_karsilama")
    }
}
