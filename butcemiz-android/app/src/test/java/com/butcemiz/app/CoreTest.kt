package com.butcemiz.app

import androidx.compose.ui.text.AnnotatedString
import com.butcemiz.app.core.HybridClock
import com.butcemiz.app.core.Money
import com.butcemiz.app.core.PeriodKind
import com.butcemiz.app.core.Periods
import com.butcemiz.app.prefs.AppPrefs
import com.butcemiz.app.prefs.SyncConfig
import com.butcemiz.app.rates.RatesRepository
import com.butcemiz.app.sync.Crypto
import com.butcemiz.app.sync.PairingCode
import com.butcemiz.app.ui.components.AmountVisualTransformation
import com.butcemiz.app.ui.components.amountToKurus
import com.butcemiz.app.ui.components.sanitizeAmountInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test
    fun formatsTurkishStyle() {
        assertEquals("120.000 ₺", Money.format(120_000_00))
        assertEquals("2.150,50 ₺", Money.format(2_150_50))
        assertEquals("0 ₺", Money.format(0))
        assertEquals("${Money.MINUS}2.000 ₺", Money.format(-2_000_00))
        assertEquals("+45.000 ₺", Money.format(45_000_00, sign = true))
        assertEquals("1.234.567,89 ₺", Money.format(1_234_567_89))
        assertEquals("1.235 ₺", Money.format(1_234_56, Money.Decimals.NEVER))
        assertEquals("12,00 ₺", Money.format(12_00, Money.Decimals.ALWAYS))
        assertEquals("999", Money.format(999_00, symbol = false))
    }

    @Test
    fun compactFormat() {
        assertEquals("8.450", Money.compact(8_450_00))
        assertEquals("12,5 bin", Money.compact(12_500_00))
        assertEquals("1,2 mn", Money.compact(1_200_000_00))
        assertEquals("${Money.MINUS}50 bin", Money.compact(-50_000_00))
    }

    @Test
    fun parsesManyInputStyles() {
        assertEquals(2_000_00L, Money.parse("2000"))
        assertEquals(2_000_00L, Money.parse("2.000"))
        assertEquals(2_000_50L, Money.parse("2000,50"))
        assertEquals(2_000_50L, Money.parse("2.000,50"))
        assertEquals(2_000_50L, Money.parse("2000.5"))
        assertEquals(1_234_567_00L, Money.parse("1.234.567"))
        assertEquals(2_000_00L, Money.parse("₺ 2.000"))
        assertEquals(2_000_00L, Money.parse("2 000 TL"))
        assertEquals(-150_00L, Money.parse("-150"))
        assertEquals(1_99L, Money.parse("1,99"))
        assertEquals(1_234_50L, Money.parse("1,234.50"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun editableRoundTrip() {
        for (k in listOf(1L, 99L, 100L, 150L, 2_000_50L, 120_000_00L, 1_234_567_89L)) {
            assertEquals(k, Money.parse(Money.toEditable(k)))
        }
    }

    @Test
    fun quantityFormat() {
        assertEquals("10,5", Money.quantity(10.5))
        assertEquals("1.500", Money.quantity(1500.0))
        assertEquals("0,125", Money.quantity(0.125))
    }
}

class AmountInputTest {
    @Test
    fun sanitize() {
        assertEquals("1234", sanitizeAmountInput("1234"))
        assertEquals("1234,5", sanitizeAmountInput("1234.5"))
        assertEquals("1234,56", sanitizeAmountInput("1234,567"))
        assertEquals("0,5", sanitizeAmountInput(",5"))
        assertEquals("12", sanitizeAmountInput("0012"))
        assertEquals("0", sanitizeAmountInput("000"))
        assertEquals("12,34", sanitizeAmountInput("12,3,4"))
        assertEquals("", sanitizeAmountInput("abc"))
        assertEquals(1_234_56L, amountToKurus("1234,56"))
    }

    @Test
    fun visualTransformationIsValidForAllCursorPositions() {
        val t = AmountVisualTransformation()
        val samples = listOf("", "1", "12", "123", "1234", "12345", "123456", "1234567", "1234567,8", "1234,56", "0,5", "100000000")
        for (raw in samples) {
            val out = t.filter(AnnotatedString(raw))
            val shown = out.text.text
            assertEquals(raw.replace(".", ""), shown.replace(".", ""))
            for (o in 0..raw.length) {
                val tr = out.offsetMapping.originalToTransformed(o)
                assertTrue("o2t $raw $o -> $tr", tr in 0..shown.length)
            }
            for (tOff in 0..shown.length) {
                val orig = out.offsetMapping.transformedToOriginal(tOff)
                assertTrue("t2o $raw $tOff -> $orig", orig in 0..raw.length)
            }
        }
        assertEquals("1.234.567,8", t.filter(AnnotatedString("1234567,8")).text.text)
    }
}

class PeriodsTest {
    @Test
    fun monthWithCustomStartDay() {
        val p = Periods.month(LocalDate.of(2026, 11, 6), 15)
        assertEquals(LocalDate.of(2026, 10, 15), p.start)
        assertEquals(LocalDate.of(2026, 11, 15), p.endExclusive)
        val q = Periods.month(LocalDate.of(2026, 11, 20), 15)
        assertEquals(LocalDate.of(2026, 11, 15), q.start)
    }

    @Test
    fun weekStartsMonday() {
        val w = Periods.week(LocalDate.of(2026, 11, 6)) // Cuma
        assertEquals(LocalDate.of(2026, 11, 2), w.start)
        assertEquals(7, w.days)
        val shifted = Periods.shift(w, -1, 1)
        assertEquals(LocalDate.of(2026, 10, 26), shifted.start)
        assertEquals(PeriodKind.WEEK, shifted.kind)
    }
}

class ClockTest {
    @Test
    fun hlcIsMonotonic() {
        val c = HybridClock(0) {}
        val a = c.now(1000)
        val b = c.now(1000)
        val d = c.now(999)
        assertTrue(b > a && d > b)
        c.observe(HybridClock.millisOf(d) + 50 shl 16)
        assertTrue(c.now(1000) > (HybridClock.millisOf(d) + 50 shl 16))
        assertTrue(HybridClock.isNewer(5, "b", 5, "a"))
    }
}

class CryptoTest {
    @Test
    fun sealOpenRoundTrip() {
        val key = Crypto.newKey()
        val msg = "Alperen Market için 2.000 ₺ harcadı".toByteArray()
        val sealed = Crypto.seal(key, msg)
        assertTrue(sealed.startsWith(Crypto.PREFIX))
        assertArrayEquals(msg, Crypto.open(key, sealed))
        // yanlış anahtar ve bozulmuş veri
        assertNull(Crypto.open(Crypto.newKey(), sealed))
        val tampered = sealed.dropLast(3) + if (sealed.last() == 'A') "BBB" else "AAA"
        assertNull(Crypto.open(key, tampered))
        assertNull(Crypto.open(key, "merhaba"))
    }

    @Test
    fun largePayloadCompresses() {
        val key = Crypto.newKey()
        val big = buildString { repeat(2000) { append("{\"title\":\"Migros\",\"amount\":200000},") } }.toByteArray()
        val sealed = Crypto.seal(key, big)
        assertTrue("sıkıştırma çalışmalı: ${sealed.length}", sealed.length < big.size / 5)
        assertArrayEquals(big, Crypto.open(key, sealed))
    }

    @Test
    fun pairingCodeRoundTrip() {
        val cfg = PairingCode.newConfig()
        val code = PairingCode.encode(cfg)
        assertTrue(code.startsWith("BTZ1."))
        assertEquals(cfg, PairingCode.decode(code))
        // paylaşılan mesajın içinden bulunur
        val shared = PairingCode.shareText(cfg, "Evimiz")
        assertEquals(cfg, PairingCode.decode(shared))
        // özel sunucu
        val custom = SyncConfig("https://ntfy.example.com", cfg.topic, cfg.keyB64)
        assertEquals(custom, PairingCode.decode(PairingCode.encode(custom)))
        assertEquals(AppPrefs.DEFAULT_SERVER, PairingCode.decode(code)!!.server)
        assertNull(PairingCode.decode("BTZ1.kisa.kod"))
    }
}

class RatesParseTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun truncgil() {
        val list = RatesRepository.parseTruncgil(res("truncgil_v3.json"), 1L).associateBy { it.key }
        assertNotNull(list["USD"])
        assertNotNull(list["gram-altin"])
        assertNotNull(list["ceyrek-altin"])
        assertTrue(list["gram-altin"]!!.buying > 1000)
        assertTrue(list["USD"]!!.buying in 10.0..500.0)
        assertTrue(list["ceyrek-altin"]!!.buying > list["gram-altin"]!!.buying)
    }

    @Test
    fun tcmb() {
        val list = RatesRepository.parseTcmb(res("tcmb_today.xml"), 1L).associateBy { it.key }
        assertNotNull(list["USD"])
        assertNotNull(list["EUR"])
        assertTrue(list["USD"]!!.buying in 10.0..500.0)
    }

    @Test
    fun turkishDecimals() {
        assertEquals(6543.32, RatesRepository.parseTrDecimal("6.543,32")!!, 1e-9)
        assertEquals(4057.58, RatesRepository.parseTrDecimal("$4.057,58")!!, 1e-9)
        assertEquals(-0.78, RatesRepository.parseTrDecimal("%-0,78")!!, 1e-9)
        assertEquals(50.1521, RatesRepository.parseTrDecimal("50.1521")!!, 1e-9)
    }
}
