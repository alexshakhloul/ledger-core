package ledger.domain

import java.math.BigDecimal
import kotlin.test.*

class MoneyTest {

    @Test
    fun `AED holds two decimal places`() {
        assertEquals(2500L, Money.aed("25.00").amount)
        assertEquals(120000L, Money.aed("1200.00").amount)
    }

    @Test
    fun `BHD holds three decimal places`() {
        assertEquals(10000L, Money.bhd("10.000").amount)
        assertEquals(3333L, Money.bhd("3.333").amount)
    }

    @Test
    fun `a currency's scale is applied even when the input omits it`() {
        assertEquals(Money.aed("25.00"), Money.aed("25"))
        assertEquals(Money.bhd("10.000"), Money.bhd("10"))
    }

    @Test
    fun `excess precision rounds up to the currency's scale`() {
        // The interest rate case
        assertEquals(Money.aed("0.18"), Money.aed("0.176"))
        assertEquals(Money.bhd("0.004"), Money.bhd("0.0040032"))
    }

    @Test
    fun `a half unit rounds away from zero`() {
        assertEquals(Money.aed("0.13"), Money.aed("0.125"))
        assertEquals(Money.aed("0.12"), Money.aed("0.124"))
    }

    @Test
    fun `an unrounded Money cannot be constructed`() {
        val overPrecise = Money.of(Currency.AED, BigDecimal("1.239999999"))
        assertEquals(2, overPrecise.toBigDecimal().scale())
        assertEquals(Money.aed("1.24"), overPrecise)
    }

    @Test
    fun `addition and subtraction are exact`() {
        assertEquals(Money.aed("250.00"), Money.aed("1200.00") - Money.aed("950.00"))
        assertEquals(Money.aed("-370.00"), Money.aed("250.00") - Money.aed("620.00"))
        assertEquals(Money.aed("225.00"), Money.aed("-395.00") + Money.aed("620.00"))
    }

    @Test
    fun `the BHD instalments total exactly ten`() {
        val instalments = listOf(Money.bhd("3.333"), Money.bhd("3.333"), Money.bhd("3.334"))
        assertEquals(Money.bhd("10.000"), instalments.sum(Currency.BHD))
    }

    @Test
    fun `negation flips the sign`() {
        assertEquals(Money.aed("-25.00"), -Money.aed("25.00"))
        assertEquals(Money.aed("25.00"), -Money.aed("-25.00"))
    }

    @Test
    fun `summing nothing yields a denominated zero`() {
        assertEquals(Money.bhd("0.000"), emptyList<Money>().sum(Currency.BHD))
    }

    @Test
    fun `sign reflects the amount`() {
        assertTrue(Money.aed("-0.01").isNegative)
        assertFalse(Money.aed("0.00").isNegative)
        assertTrue(Money.aed("0.00").isZero)
        assertTrue(Money.aed("0.01").isPositive)
    }

    @Test
    fun `amounts compare by value`() {
        assertTrue(Money.aed("-370.00") < Money.zero(Currency.AED))
        assertTrue(Money.aed("440.98") > Money.aed("440.00"))
        assertEquals(0, Money.aed("25.00").compareTo(Money.aed("25.00")))
    }

    @Test
    fun `adding across currencies is rejected`() {
        assertFailsWith<CurrencyMismatchException> { Money.aed("1.00") + Money.bhd("1.000") }
    }

    @Test
    fun `subtracting across currencies is rejected`() {
        assertFailsWith<CurrencyMismatchException> { Money.aed("1.00") - Money.bhd("1.000") }
    }

    @Test
    fun `comparing across currencies is rejected`() {
        assertFailsWith<CurrencyMismatchException> { Money.aed("1.00") < Money.bhd("1.000") }
    }

    @Test
    fun `equal numbers in different currencies are not equal amounts`() {
        assertFalse(Money.aed("10.00") == Money.bhd("1.000"))
    }

    @Test
    fun `rendering always shows the full currency scale`() {
        assertEquals("AED 250.00", Money.aed("250.00").toString())
        assertEquals("AED 0.98", Money.aed("0.98").toString())
        assertEquals("BHD 0.000", Money.bhd("0").toString())
        assertEquals("BHD 10.008", Money.bhd("10.008").toString())
    }

    @Test
    fun `a negative amount renders with the sign after the currency code`() {
        assertEquals("AED -370.00", Money.aed("-370.00").toString())
    }

    @Test
    fun `large amounts render without a grouping separator`() {
        assertEquals("AED 1200.00", Money.aed("1200.00").toString())
    }
}
