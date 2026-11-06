package com.butcemiz.app.backup

import android.content.Context
import android.net.Uri
import com.butcemiz.app.core.Dates
import com.butcemiz.app.core.HybridClock
import com.butcemiz.app.core.Money
import com.butcemiz.app.data.Account
import com.butcemiz.app.data.AppJson
import com.butcemiz.app.data.Budget
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.Debt
import com.butcemiz.app.data.EntityType
import com.butcemiz.app.data.Household
import com.butcemiz.app.data.IncomeSource
import com.butcemiz.app.data.Member
import com.butcemiz.app.data.Recurring
import com.butcemiz.app.data.Repository
import com.butcemiz.app.data.Saving
import com.butcemiz.app.data.Synced
import com.butcemiz.app.data.TxType
import com.butcemiz.app.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val app: String = "Butcemiz",
    val version: Int = 1,
    val exportedAt: Long = 0,
    val household: List<Household> = emptyList(),
    val members: List<Member> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val savings: List<Saving> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val budgets: List<Budget> = emptyList(),
)

data class ImportResult(val imported: Int, val skipped: Int)

/** JSON yedek (tam) ve CSV dışa aktarma (Excel uyumlu). */
class BackupManager(private val context: Context, private val repo: Repository) {

    suspend fun buildBackup(): BackupFile {
        val db = repo.db
        return BackupFile(
            exportedAt = System.currentTimeMillis(),
            household = db.householdDao().all(),
            members = db.memberDao().all(),
            accounts = db.accountDao().all(),
            categories = db.categoryDao().all(),
            txns = db.txnDao().all(),
            savings = db.savingDao().all(),
            debts = db.debtDao().all(),
            recurring = db.recurringDao().all(),
            incomeSources = db.incomeSourceDao().all(),
            budgets = db.budgetDao().all(),
        )
    }

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val text = AppJson.encodeToString(BackupFile.serializer(), buildBackup())
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun importJson(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val file = AppJson.decodeFromString(BackupFile.serializer(), text)
        require(file.app == "Butcemiz") { "Bu dosya bir Bütçemiz yedeği değil" }
        importBackup(file)
    }

    /** Yedekteki kayıt yereldekinden yeniyse (ya da yerelde yoksa) alınır ve eşe de gönderilir. */
    suspend fun importBackup(file: BackupFile): ImportResult {
        val log = repo.log
        var imported = 0
        var skipped = 0
        suspend fun <T : Synced> merge(type: EntityType<T>, items: List<T>) {
            for (item in items) {
                val local = type.get(item.id)
                if (local == null || HybridClock.isNewer(item.stamp, item.stampDevice, local.stamp, local.stampDevice)) {
                    log.commit(type, item)
                    imported++
                } else {
                    skipped++
                }
            }
        }
        merge(log.households, file.household)
        merge(log.members, file.members)
        merge(log.accounts, file.accounts)
        merge(log.categories, file.categories)
        merge(log.savings, file.savings)
        merge(log.debts, file.debts)
        merge(log.recurring, file.recurring)
        merge(log.incomeSources, file.incomeSources)
        merge(log.budgets, file.budgets)
        merge(log.txns, file.txns)
        repo.prefs.let { } // no-op
        return ImportResult(imported, skipped)
    }

    /** Excel (Türkçe) uyumlu CSV: UTF-8 BOM + noktalı virgül ayırıcı. */
    suspend fun exportCsv(uri: Uri, startMillis: Long? = null, endMillis: Long? = null) = withContext(Dispatchers.IO) {
        val db = repo.db
        val cats = db.categoryDao().all().associateBy { it.id }
        val accs = db.accountDao().all().associateBy { it.id }
        val mems = db.memberDao().all().associateBy { it.id }
        val txns = db.txnDao().listAll().filter {
            (startMillis == null || it.occurredAt >= startMillis) && (endMillis == null || it.occurredAt < endMillis)
        }
        val sb = StringBuilder("﻿")
        sb.append("Tarih;Tür;Açıklama;Kategori;Tutar (TL);Hesap;Hedef hesap;Kişi;Not\n")
        for (t in txns) {
            val signed = when (t.type) {
                TxType.EXPENSE, TxType.SAVING_DEPOSIT, TxType.LOAN_GIVEN -> -t.amount
                else -> t.amount
            }
            val cells = listOf(
                Dates.csv(t.occurredAt),
                t.type.label,
                t.title,
                t.categoryId?.let { cats[it]?.name } ?: "",
                Money.format(signed, Money.Decimals.ALWAYS, symbol = false).replace(Money.MINUS, "-").replace(".", ""),
                t.accountId?.let { accs[it]?.name } ?: "",
                t.toAccountId?.let { accs[it]?.name } ?: "",
                t.memberId?.let { mems[it]?.name } ?: "",
                t.note,
            )
            sb.append(cells.joinToString(";") { csvCell(it) }).append('\n')
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        txns.size
    }

    private fun csvCell(v: String): String {
        val needs = v.contains(';') || v.contains('"') || v.contains('\n')
        val esc = v.replace("\"", "\"\"").replace("\n", " ")
        return if (needs) "\"$esc\"" else esc
    }
}
