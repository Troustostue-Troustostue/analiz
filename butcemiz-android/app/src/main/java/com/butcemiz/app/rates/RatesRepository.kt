package com.butcemiz.app.rates

import com.butcemiz.app.data.AppDatabase
import com.butcemiz.app.data.PriceEntry
import com.butcemiz.app.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Döviz ve altın fiyatları. Birincil kaynak: Truncgil Finans (altın türleri + döviz),
 * yedek: TCMB günlük kurlar (yalnızca döviz). Değerleme "alış" fiyatıyla yapılır.
 */
class RatesRepository(
    private val db: AppDatabase,
    private val prefs: AppPrefs,
    baseClient: OkHttpClient,
) {
    private val http = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun refreshIfStale(maxAgeMs: Long = 30 * 60_000L) {
        if (System.currentTimeMillis() - prefs.lastRatesFetch > maxAgeMs) refresh()
    }

    /** Başarılıysa güncellenen kalem sayısı, aksi halde hata mesajı. */
    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entries = LinkedHashMap<String, PriceEntry>()
        var error: String? = null
        try {
            parseTruncgil(get(TRUNCGIL_V3), now).forEach { entries[it.key] = it }
        } catch (e: Exception) {
            error = e.message
        }
        if (FX_KEYS.any { it !in entries }) {
            try {
                parseTcmb(get(TCMB), now).forEach { if (it.key !in entries) entries[it.key] = it }
            } catch (e: Exception) {
                if (error == null) error = e.message
            }
        }
        if (entries.isEmpty()) return@withContext Result.failure(IllegalStateException(error ?: "Fiyatlar alınamadı"))
        db.priceDao().upsertAll(entries.values.toList())
        prefs.lastRatesFetch = now
        Result.success(entries.size)
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "Butcemiz-Android/1.0").get().build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return r.body?.string() ?: throw IllegalStateException("Boş yanıt")
        }
    }

    fun parseTruncgil(body: String, now: Long): List<PriceEntry> {
        val root = json.parseToJsonElement(body).jsonObject
        val out = mutableListOf<PriceEntry>()
        for (key in TRUNCGIL_KEYS) {
            val o = root[key] as? JsonObject ?: continue
            val buying = num(o["Buying"] ?: o["Alış"])
            val selling = num(o["Selling"] ?: o["Satış"])
            val change = num(o["Change"] ?: o["Değişim"])
            if (buying == null && selling == null) continue
            out += PriceEntry(key, buying ?: selling!!, selling ?: buying!!, change, now, "Truncgil")
        }
        return out
    }

    fun parseTcmb(body: String, now: Long): List<PriceEntry> {
        val out = mutableListOf<PriceEntry>()
        val block = Regex("<Currency[^>]*Kod=\"([A-Z]{3})\"[^>]*>(.*?)</Currency>", RegexOption.DOT_MATCHES_ALL)
        for (m in block.findAll(body)) {
            val code = m.groupValues[1]
            if (code !in FX_KEYS) continue
            val inner = m.groupValues[2]
            fun tag(t: String) = Regex("<$t>([^<]*)</$t>").find(inner)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
            val unit = tag("Unit") ?: 1.0
            val buying = tag("ForexBuying") ?: tag("BanknoteBuying") ?: continue
            val selling = tag("ForexSelling") ?: tag("BanknoteSelling") ?: buying
            out += PriceEntry(code, buying / unit, selling / unit, null, now, "TCMB")
        }
        return out
    }

    private fun num(e: kotlinx.serialization.json.JsonElement?): Double? {
        val p = e as? JsonPrimitive ?: return null
        return parseTrDecimal(p.content)
    }

    companion object {
        const val TRUNCGIL_V3 = "https://finans.truncgil.com/v3/today.json"
        const val TCMB = "https://www.tcmb.gov.tr/kurlar/today.xml"
        val FX_KEYS = listOf("USD", "EUR", "GBP", "CHF")
        val TRUNCGIL_KEYS = FX_KEYS + listOf(
            "gram-altin", "ceyrek-altin", "yarim-altin", "tam-altin", "cumhuriyet-altini", "ata-altin",
            "resat-altin", "ikibucuk-altin", "besli-altin", "22-ayar-bilezik", "18-ayar-altin", "14-ayar-altin",
            "gumus", "ons",
        )

        /** "6.543,32" → 6543.32 ; "$4.057,58" → 4057.58 ; "%-0,78" → -0.78 ; "50.1521" → 50.1521 */
        fun parseTrDecimal(raw: String): Double? {
            val s = raw.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
            if (s.isEmpty()) return null
            val normalized = if (',' in s) s.replace(".", "").replace(',', '.') else s
            return normalized.toDoubleOrNull()
        }
    }
}
