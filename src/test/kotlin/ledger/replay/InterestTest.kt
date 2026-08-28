package ledger.replay

import ledger.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

class InterestTest {

    private fun settledAcc001(): Ledger {
        val ledger = Ledger(Account.acc001())
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1))
        ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3))
        ledger.append(EntryType.DEBIT, Money.aed("185.00"), Day(4))
        val backdated = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2))
        ledger.append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))
        ledger.append(EntryType.CREDIT, Money.aed("620.00"), Day(2), reversalOf = backdated.entryId)
        return ledger
    }

    @Test
    fun `ACC-001 accrues the published daily amounts`() {
        val accrual = InterestPolicy().capitalize(settledAcc001())

        assertEquals(Money.aed("0.10"), accrual.daily.getValue(Day(1)))
        assertEquals(Money.aed("0.09"), accrual.daily.getValue(Day(2)))
        assertEquals(Money.aed("0.25"), accrual.daily.getValue(Day(3)))
        assertEquals(Money.aed("0.18"), accrual.daily.getValue(Day(4)))
        assertEquals(Money.aed("0.18"), accrual.daily.getValue(Day(5)))
        assertEquals(Money.aed("0.18"), accrual.daily.getValue(Day(6)))
    }

    @Test
    fun `the capitalized total is exactly the sum of the rounded dailies`() {
        val accrual = InterestPolicy().capitalize(settledAcc001())

        assertEquals(Money.aed("0.98"), accrual.capitalized)
        assertEquals(accrual.daily.values.sum(Currency.AED), accrual.capitalized)
    }

    @Test
    fun `interest is capitalized once, on Day 6`() {
        val ledger = settledAcc001()
        InterestPolicy().capitalize(ledger)

        val credit = ledger.entriesOfType(EntryType.INTEREST_CAPITALIZATION).single()
        assertEquals(Day(6), credit.valueDate)
        assertEquals(Money.aed("0.98"), credit.amount)
        assertEquals(Money.aed("440.98"), ledger.closingBalance(Day(6)))
    }

    @Test
    fun `the Day 6 accrual is taken before capitalization, not after`() {
        val ledger = settledAcc001()
        val accrual = InterestPolicy().capitalize(ledger)

        assertEquals(Money.aed("0.18"), accrual.daily.getValue(Day(6)))
        assertEquals(
            Money.aed("440.00"),
            ledger.closingBalance(Day(6), excluding = setOf(EntryType.INTEREST_CAPITALIZATION)),
        )
    }

    @Test
    fun `ACC-002 earns only on the days it holds a balance`() {
        val ledger = Ledger(Account.acc002())
        listOf("3.333", "3.333", "3.334").forEach {
            ledger.append(EntryType.CREDIT, Money.bhd(it), Day(5))
        }

        val accrual = InterestPolicy().capitalize(ledger)

        assertEquals(Money.bhd("0.000"), accrual.daily.getValue(Day(4)))
        assertEquals(Money.bhd("0.004"), accrual.daily.getValue(Day(5)))
        assertEquals(Money.bhd("0.004"), accrual.daily.getValue(Day(6)))
        assertEquals(Money.bhd("0.008"), accrual.capitalized)
        assertEquals(Money.bhd("10.008"), ledger.closingBalance(Day(6)))
    }

    @Test
    fun `an overdrawn day earns nothing`() {
        val ledger = Ledger(Account.acc001())
        ledger.append(EntryType.DEBIT, Money.aed("100.00"), Day(1))

        val accrual = InterestPolicy().capitalize(ledger)

        assertEquals(Money.aed("0.00"), accrual.daily.getValue(Day(1)))
        assertEquals(Money.aed("0.00"), accrual.capitalized)
    }

    @Test
    fun `an account that earns nothing gets no capitalization entry`() {
        val ledger = Ledger(Account.acc001())
        InterestPolicy().capitalize(ledger)
        assertEquals(0, ledger.entriesOfType(EntryType.INTEREST_CAPITALIZATION).size)
    }
}
