package com.butcemiz.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class TxType(val label: String) {
    EXPENSE("Gider"),
    INCOME("Gelir"),
    TRANSFER("Transfer"),
    ADJUSTMENT("Bakiye düzeltme"),
    SAVING_DEPOSIT("Birikime aktarım"),
    SAVING_WITHDRAW("Birikimden çekiş"),
    LOAN_RECEIVED("Borç alındı"),
    LOAN_GIVEN("Borç verildi");

    /** Gelir/gider istatistiklerine dahil mi? */
    val countsAsExpense: Boolean get() = this == EXPENSE
    val countsAsIncome: Boolean get() = this == INCOME
}

@Serializable
enum class AccountType(val label: String) {
    BANK("Banka hesabı"),
    CASH("Nakit"),
    CREDIT_CARD("Kredi kartı"),
    OTHER("Diğer");
}

@Serializable
enum class CategoryKind { EXPENSE, INCOME }

enum class SavingGroup(val label: String) {
    CASH("Mevduat"),
    FX("Döviz"),
    GOLD("Altın"),
    METAL("Değerli maden"),
    INVESTMENT("Yatırım"),
    OTHER("Diğer"),
}

/**
 * Birikim türleri. priceKey: otomatik fiyat anahtarı (rates paketi), null ise elle fiyat.
 * unitIsLira: miktar doğrudan TL tutarıdır (mevduat, BES).
 */
@Serializable
enum class SavingType(
    val label: String,
    val unit: String,
    val priceKey: String?,
    val group: SavingGroup,
    val unitIsLira: Boolean = false,
) {
    TRY("Türk Lirası (mevduat)", "₺", null, SavingGroup.CASH, unitIsLira = true),
    USD("Amerikan Doları", "$", "USD", SavingGroup.FX),
    EUR("Euro", "€", "EUR", SavingGroup.FX),
    GBP("İngiliz Sterlini", "£", "GBP", SavingGroup.FX),
    CHF("İsviçre Frangı", "CHF", "CHF", SavingGroup.FX),
    GOLD_GRAM("Gram altın (24 ayar)", "gr", "gram-altin", SavingGroup.GOLD),
    GOLD_QUARTER("Çeyrek altın", "adet", "ceyrek-altin", SavingGroup.GOLD),
    GOLD_HALF("Yarım altın", "adet", "yarim-altin", SavingGroup.GOLD),
    GOLD_FULL("Tam altın", "adet", "tam-altin", SavingGroup.GOLD),
    GOLD_REPUBLIC("Cumhuriyet altını", "adet", "cumhuriyet-altini", SavingGroup.GOLD),
    GOLD_ATA("Ata altın", "adet", "ata-altin", SavingGroup.GOLD),
    GOLD_RESAT("Reşat altın", "adet", "resat-altin", SavingGroup.GOLD),
    GOLD_IKIBUCUK("İkibuçuk altın", "adet", "ikibucuk-altin", SavingGroup.GOLD),
    GOLD_BESLI("Beşli altın", "adet", "besli-altin", SavingGroup.GOLD),
    GOLD_22K("22 ayar bilezik", "gr", "22-ayar-bilezik", SavingGroup.GOLD),
    GOLD_18K("18 ayar altın", "gr", "18-ayar-altin", SavingGroup.GOLD),
    GOLD_14K("14 ayar altın", "gr", "14-ayar-altin", SavingGroup.GOLD),
    SILVER("Gümüş", "gr", "gumus", SavingGroup.METAL),
    STOCK("Hisse senedi", "lot", null, SavingGroup.INVESTMENT),
    FUND("Yatırım fonu", "pay", null, SavingGroup.INVESTMENT),
    CRYPTO("Kripto para", "adet", null, SavingGroup.INVESTMENT),
    BES("Bireysel emeklilik (BES)", "₺", null, SavingGroup.INVESTMENT, unitIsLira = true),
    OTHER("Diğer", "adet", null, SavingGroup.OTHER);

    val needsManualPrice: Boolean get() = priceKey == null && !unitIsLira
}

@Serializable
enum class DebtDirection(val label: String) {
    I_OWE("Borcum"),
    OWED_TO_ME("Alacağım"),
}

@Serializable
enum class DebtKind(val label: String) {
    LOAN("Banka kredisi"),
    CREDIT_CARD("Kredi kartı borcu"),
    INSTALLMENT("Taksitli alışveriş"),
    PERSONAL("Şahıs borcu / alacağı"),
    OTHER("Diğer"),
}

@Serializable
enum class Frequency(val label: String) {
    MONTHLY("Her ay"),
    WEEKLY("Her hafta"),
    BIMONTHLY("İki ayda bir"),
    QUARTERLY("Üç ayda bir"),
    YEARLY("Her yıl"),
}

@Serializable
enum class IncomeKind(val label: String) {
    SALARY("Maaş"),
    PENSION("Emekli maaşı"),
    RENT("Kira geliri"),
    SCHOLARSHIP("Burs"),
    FREELANCE("Serbest çalışma"),
    OTHER("Diğer"),
}
