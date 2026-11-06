package com.butcemiz.app.data

/**
 * Varsayılan veriler. Her iki telefonda da AYNI kimlik ve AYNI (en eski) damga ile oluşturulur;
 * bu yüzden senkronizasyon gerektirmez ve çakışmaz. Kullanıcı bir varsayılanı düzenlerse
 * gerçek damgalı işlem oluşur ve o kazanır.
 */
object Seeds {
    const val SEED_STAMP = 1L
    const val SEED_DEVICE = "seed"
    const val MAIN_ACCOUNT_ID = "acc_main"

    const val CAT_MARKET = "cat_market"
    const val CAT_BILLS = "cat_faturalar"
    const val CAT_RENT = "cat_kira"
    const val CAT_TRANSPORT = "cat_ulasim"
    const val CAT_FOOD = "cat_yemek"
    const val CAT_DEBT = "cat_borc"
    const val CAT_SUBSCRIPTION = "cat_abonelik"
    const val CAT_OTHER_EXPENSE = "cat_diger_gider"
    const val CAT_SALARY = "cat_maas"
    const val CAT_EXTRA_INCOME = "cat_ek_gelir"
    const val CAT_RECEIVABLE = "cat_alacak"
    const val CAT_OTHER_INCOME = "cat_diger_gelir"
    const val CAT_RENT_INCOME = "cat_kira_geliri"

    private fun c(id: String, name: String, kind: CategoryKind, icon: String, color: Long, order: Int) =
        Category(id, name, kind, icon, color, order, false, SEED_STAMP, SEED_DEVICE, false)

    val categories: List<Category> = listOf(
        c(CAT_MARKET, "Market & Gıda", CategoryKind.EXPENSE, "cart", 0xFF16A34A, 0),
        c(CAT_BILLS, "Faturalar", CategoryKind.EXPENSE, "bolt", 0xFFF59E0B, 1),
        c(CAT_RENT, "Kira & Aidat", CategoryKind.EXPENSE, "home", 0xFF4F46E5, 2),
        c(CAT_TRANSPORT, "Ulaşım & Yakıt", CategoryKind.EXPENSE, "car", 0xFF2563EB, 3),
        c(CAT_FOOD, "Yeme & İçme", CategoryKind.EXPENSE, "restaurant", 0xFFEA580C, 4),
        c("cat_saglik", "Sağlık", CategoryKind.EXPENSE, "health", 0xFFDC2626, 5),
        c("cat_giyim", "Giyim", CategoryKind.EXPENSE, "clothes", 0xFFDB2777, 6),
        c("cat_egitim", "Eğitim", CategoryKind.EXPENSE, "school", 0xFF0D9488, 7),
        c("cat_eglence", "Eğlence & Hobi", CategoryKind.EXPENSE, "movie", 0xFF9333EA, 8),
        c("cat_ev", "Ev & Mobilya", CategoryKind.EXPENSE, "chair", 0xFF92400E, 9),
        c("cat_teknoloji", "Teknoloji", CategoryKind.EXPENSE, "devices", 0xFF475569, 10),
        c("cat_bakim", "Kişisel Bakım", CategoryKind.EXPENSE, "spa", 0xFFE11D48, 11),
        c("cat_hediye", "Hediye & Bağış", CategoryKind.EXPENSE, "gift", 0xFFC026D3, 12),
        c(CAT_SUBSCRIPTION, "Abonelikler", CategoryKind.EXPENSE, "subscriptions", 0xFF7C3AED, 13),
        c("cat_cocuk", "Çocuk", CategoryKind.EXPENSE, "child", 0xFF0891B2, 14),
        c("cat_evcil", "Evcil Hayvan", CategoryKind.EXPENSE, "pets", 0xFF65A30D, 15),
        c(CAT_DEBT, "Kredi & Borç Ödemesi", CategoryKind.EXPENSE, "card", 0xFFB91C1C, 16),
        c("cat_vergi", "Vergi & Resmi", CategoryKind.EXPENSE, "tax", 0xFF6B7280, 17),
        c("cat_tatil", "Tatil & Seyahat", CategoryKind.EXPENSE, "travel", 0xFF0284C7, 18),
        c(CAT_OTHER_EXPENSE, "Diğer", CategoryKind.EXPENSE, "more", 0xFF64748B, 99),

        c(CAT_SALARY, "Maaş", CategoryKind.INCOME, "work", 0xFF059669, 0),
        c(CAT_EXTRA_INCOME, "Ek Gelir", CategoryKind.INCOME, "payments", 0xFF0D9488, 1),
        c("cat_prim", "Prim & İkramiye", CategoryKind.INCOME, "celebration", 0xFFD97706, 2),
        c(CAT_RENT_INCOME, "Kira Geliri", CategoryKind.INCOME, "home", 0xFF4F46E5, 3),
        c("cat_yatirim", "Yatırım Getirisi", CategoryKind.INCOME, "trending", 0xFF7C3AED, 4),
        c("cat_hediye_gelir", "Hediye", CategoryKind.INCOME, "gift", 0xFFDB2777, 5),
        c(CAT_RECEIVABLE, "Alacak Tahsilatı", CategoryKind.INCOME, "handshake", 0xFF2563EB, 6),
        c(CAT_OTHER_INCOME, "Diğer Gelir", CategoryKind.INCOME, "more", 0xFF64748B, 99),
    )

    val mainAccount = Account(
        id = MAIN_ACCOUNT_ID,
        name = "Ortak Hesap",
        type = AccountType.BANK,
        openingBalance = 0,
        includeInTotal = true,
        color = 0xFF0E7C66,
        sortOrder = 0,
        createdAt = 0,
        stamp = SEED_STAMP,
        stampDevice = SEED_DEVICE,
    )

    val household = Household(stamp = SEED_STAMP, stampDevice = SEED_DEVICE)

    suspend fun apply(db: AppDatabase) {
        db.categoryDao().insertIgnore(categories)
        db.accountDao().insertIgnore(listOf(mainAccount))
        db.householdDao().insertIgnore(listOf(household))
    }

    fun incomeCategoryFor(kind: IncomeKind): String = when (kind) {
        IncomeKind.SALARY, IncomeKind.PENSION -> CAT_SALARY
        IncomeKind.RENT -> CAT_RENT_INCOME
        else -> CAT_EXTRA_INCOME
    }
}
