package ledger.replay

import ledger.domain.Currency
import ledger.domain.Money
import ledger.domain.sum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstalmentSplitTest {

    @Test
    fun `BHD ten splits three ways as 3_333 3_333 3_334`() {
        assertEquals(
            listOf(Money.bhd("3.333"), Money.bhd("3.333"), Money.bhd("3.334")),
            InstalmentSplitter.split(Money.bhd("10.000"), 3),
        )
    }

    @Test
    fun `the instalments add back up to exactly the total`() {
        val parts = InstalmentSplitter.split(Money.bhd("10.000"), 3)
        assertEquals(Money.bhd("10.000"), parts.sum(Currency.BHD))
    }

    @Test
    fun `the remainder lands on the final instalment`() {
        val parts = InstalmentSplitter.split(Money.aed("10.00"), 3)
        assertEquals(listOf(Money.aed("3.33"), Money.aed("3.33"), Money.aed("3.34")), parts)
    }

    @Test
    fun `nothing is lost for any total or count`() {
        val totals = listOf("0.001", "0.01", "1.000", "7.777", "10.000", "999.999")
        for (total in totals) {
            for (count in 1..7) {
                val amount = Money.bhd(total)
                assertEquals(
                    amount,
                    InstalmentSplitter.split(amount, count).sum(Currency.BHD),
                    "$total split $count ways",
                )
            }
        }
    }

    @Test
    fun `a single instalment is the whole total`() {
        assertEquals(listOf(Money.bhd("10.000")), InstalmentSplitter.split(Money.bhd("10.000"), 1))
    }

    @Test
    fun `an impossible split is rejected`() {
        assertFailsWith<IllegalArgumentException> { InstalmentSplitter.split(Money.bhd("10.000"), 0) }
        assertFailsWith<IllegalArgumentException> { InstalmentSplitter.split(Money.bhd("-1.000"), 3) }
    }
}
