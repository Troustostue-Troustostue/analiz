package com.butcemiz.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Member::class, Account::class, Category::class, Txn::class, Saving::class, Debt::class,
        Recurring::class, IncomeSource::class, Budget::class, Device::class, Household::class,
        OpRecord::class, VectorEntry::class, KvEntry::class, PriceEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun savingDao(): SavingDao
    abstract fun debtDao(): DebtDao
    abstract fun recurringDao(): RecurringDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun budgetDao(): BudgetDao
    abstract fun deviceDao(): DeviceDao
    abstract fun householdDao(): HouseholdDao
    abstract fun opDao(): OpDao
    abstract fun vectorDao(): VectorDao
    abstract fun kvDao(): KvDao
    abstract fun priceDao(): PriceDao

    companion object {
        fun build(context: Context, name: String = "butcemiz.db"): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
