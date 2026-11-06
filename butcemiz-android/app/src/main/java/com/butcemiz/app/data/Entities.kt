package com.butcemiz.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Senkronize edilen her kayıt bu alanları taşır. Çakışmalarda (aynı kaydı iki telefon
 * aynı anda düzenlerse) daha yeni HLC damgası olan kazanır. Silme = deleted bayrağı.
 */
interface Synced {
    val id: String
    val stamp: Long
    val stampDevice: String
    val deleted: Boolean
}

@Serializable
@Entity(tableName = "members")
data class Member(
    @PrimaryKey override val id: String,
    val name: String,
    val emoji: String = "",
    val color: Long = 0xFF0E7C66,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey override val id: String,
    val name: String,
    val type: AccountType = AccountType.BANK,
    /** Hesap açılırken girilen başlangıç bakiyesi (kuruş). Sonraki düzeltmeler işlem olarak tutulur. */
    val openingBalance: Long = 0,
    val includeInTotal: Boolean = true,
    val color: Long = 0xFF0E7C66,
    val creditLimit: Long = 0,
    val ownerMemberId: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey override val id: String,
    val name: String,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val icon: String = "more",
    val color: Long = 0xFF64748B,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

/** Para hareketi. Bakiyeler her zaman bu kayıtların toplamından hesaplanır (çakışmasız). */
@Serializable
@Entity(
    tableName = "txns",
    indices = [
        Index("occurredAt"), Index("createdAt"), Index("categoryId"), Index("accountId"),
        Index("debtId"), Index("savingId"), Index("recurringId"), Index("incomeSourceId"),
    ],
)
data class Txn(
    @PrimaryKey override val id: String,
    val type: TxType = TxType.EXPENSE,
    /** kuruş; ADJUSTMENT dışında hep pozitif */
    val amount: Long = 0,
    val title: String = "",
    val categoryId: String? = null,
    /** null ise hiçbir hesabın bakiyesini etkilemez */
    val accountId: String? = null,
    val toAccountId: String? = null,
    val memberId: String? = null,
    val occurredAt: Long = 0,
    val note: String = "",
    val debtId: String? = null,
    val savingId: String? = null,
    val savingQty: Double = 0.0,
    val recurringId: String? = null,
    /** düzenli ödeme / maaş dönemi anahtarı, ör. "2026-11" */
    val period: String? = null,
    val incomeSourceId: String? = null,
    val createdAt: Long = 0,
    val createdBy: String = "",
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "savings")
data class Saving(
    @PrimaryKey override val id: String,
    val name: String,
    val type: SavingType = SavingType.TRY,
    val initialQty: Double = 0.0,
    /** elle girilen birim fiyat (kuruş). Otomatik fiyatlı türlerde doluysa onu ezer. */
    val manualUnitPrice: Long? = null,
    /** toplam maliyet (kâr/zarar göstermek için, isteğe bağlı) */
    val costBasis: Long? = null,
    /** hedef tutar (kuruş), ör. "Tatil fonu 50.000 ₺" */
    val targetAmount: Long? = null,
    val ownerMemberId: String? = null,
    val note: String = "",
    val color: Long = 0xFFF59E0B,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey override val id: String,
    val name: String,
    val direction: DebtDirection = DebtDirection.I_OWE,
    val kind: DebtKind = DebtKind.LOAN,
    val counterparty: String = "",
    /** toplam borç / alacak (kuruş) */
    val principal: Long = 0,
    /** takibe başlamadan önce ödenmiş kısım */
    val alreadyPaid: Long = 0,
    val installmentAmount: Long? = null,
    val installmentCount: Int? = null,
    val dueDay: Int? = null,
    val startDate: Long? = null,
    val interestRate: Double? = null,
    val ownerMemberId: String? = null,
    val note: String = "",
    val closed: Boolean = false,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

/** Düzenli ödeme: fatura, kira, aidat, abonelik, taksit... */
@Serializable
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey override val id: String,
    val name: String,
    val amount: Long = 0,
    /** fatura gibi her ay değişen tutar */
    val variable: Boolean = false,
    val categoryId: String? = null,
    val accountId: String? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val dayOfMonth: Int = 1,
    /** yıllık ödemeler için ay (1-12) */
    val monthOfYear: Int? = null,
    /** haftalık ödemeler için gün (1=Pzt..7=Paz) */
    val dayOfWeek: Int? = null,
    val startDate: Long = 0,
    val endDate: Long? = null,
    val remindDaysBefore: Int = 2,
    val debtId: String? = null,
    val ownerMemberId: String? = null,
    val note: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

/** Maaş ve düzenli gelirler */
@Serializable
@Entity(tableName = "income_sources")
data class IncomeSource(
    @PrimaryKey override val id: String,
    val name: String,
    val kind: IncomeKind = IncomeKind.SALARY,
    val memberId: String? = null,
    val amount: Long = 0,
    val payDay: Int = 1,
    val accountId: String? = null,
    val categoryId: String? = null,
    val active: Boolean = true,
    val note: String = "",
    val createdAt: Long = 0,
    val amountUpdatedAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

/** Aylık bütçe limiti. categoryId null ise toplam bütçe. */
@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey override val id: String,
    val categoryId: String? = null,
    val amount: Long = 0,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "devices")
data class Device(
    @PrimaryKey override val id: String,
    val name: String = "",
    val model: String = "",
    val memberId: String? = null,
    val addedAt: Long = 0,
    val appVersion: String = "",
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced

@Serializable
@Entity(tableName = "household")
data class Household(
    @PrimaryKey override val id: String = HOUSEHOLD_ID,
    val name: String = "Bütçemiz",
    val monthStartDay: Int = 1,
    val createdAt: Long = 0,
    override val stamp: Long = 0,
    override val stampDevice: String = "",
    override val deleted: Boolean = false,
) : Synced {
    companion object {
        const val HOUSEHOLD_ID = "household"
    }
}

// ---------------- Yalnızca yerel tablolar ----------------

/** İşlem günlüğü (op-log): her değişiklik bir kez, (cihaz, sıra) ile tekil. */
@Entity(tableName = "ops", primaryKeys = ["deviceId", "seq"], indices = [Index("entityId")])
data class OpRecord(
    val deviceId: String,
    val seq: Long,
    val hlc: Long,
    val entity: String,
    val entityId: String,
    val payload: String,
    val receivedAt: Long,
)

/** Durum vektörü: her cihazdan kesintisiz alınan son sıra numarası */
@Entity(tableName = "vector")
data class VectorEntry(
    @PrimaryKey val deviceId: String,
    val contiguous: Long,
    val maxSeen: Long,
)

@Entity(tableName = "kv")
data class KvEntry(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "prices")
data class PriceEntry(
    @PrimaryKey val key: String,
    val buying: Double,
    val selling: Double,
    val change: Double? = null,
    val updatedAt: Long,
    val source: String,
)
