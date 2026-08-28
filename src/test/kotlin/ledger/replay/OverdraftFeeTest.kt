package ledger.replay

import ledger.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OverdraftFeeTest {

    private fun ledger() = Ledger(Account.acc001())

    @Test
    fun `an overdrawn day is charged once`() {
        val ledger = ledger()
        ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2))

        OverdraftFeePolicy().assess(ledger, Day(2))

        val fee = ledger.entriesOfType(EntryType.OVERDRAFT_FEE).single()
        assertEquals(Money.aed("25.00"), fee.amount)
        assertEquals(Day(2), fee.valueDate)
    }

    @Test
    fun `a day already charged is never charged again`() {
        val ledger = ledger()
        val policy = OverdraftFeePolicy()
        ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2))

        repeat(5) { policy.assess(ledger, Day(2)) }

        assertEquals(1, ledger.entriesOfType(EntryType.OVERDRAFT_FEE).size)
        assertEquals(Money.aed("-645.00"), ledger.closingBalance(Day(2)))
    }

    @Test
    fun `a day in credit is not charged`() {
        val ledger = ledger()
        ledger.append(EntryType.CREDIT, Money.aed("100.00"), Day(1))
        OverdraftFeePolicy().assess(ledger, Day(1))
        assertEquals(0, ledger.entriesOfType(EntryType.OVERDRAFT_FEE).size)
    }

    @Test
    fun `a day at exactly zero is not overdrawn`() {
        val ledger = ledger()
        ledger.append(EntryType.CREDIT, Money.aed("100.00"), Day(1))
        ledger.append(EntryType.DEBIT, Money.aed("100.00"), Day(1))
        OverdraftFeePolicy().assess(ledger, Day(1))
        assertEquals(0, ledger.entriesOfType(EntryType.OVERDRAFT_FEE).size)
    }

    @Test
    fun `only the assessed day is considered, never its neighbours`() {
        val ledger = ledger()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1))
        ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3))
        ledger.append(EntryType.DEBIT, Money.aed("185.00"), Day(4))
        ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2))

        OverdraftFeePolicy().assess(ledger, Day(2))

        assertEquals(1, ledger.entriesOfType(EntryType.OVERDRAFT_FEE).size)
        assertEquals(Day(2), ledger.entriesOfType(EntryType.OVERDRAFT_FEE).single().valueDate)
        assertEquals(Money.aed("-180.00"), ledger.closingBalance(Day(4)))
    }

    @Test
    fun `a fee is not defined for a non-AED account`() {
        val bhd = Ledger(Account.acc002())
        bhd.append(EntryType.DEBIT, Money.bhd("1.000"), Day(1))

        assertFailsWith<FeeNotDefinedForCurrency> { OverdraftFeePolicy().assess(bhd, Day(1)) }
    }

    @Test
    fun `the idempotency key is per account, not global`() {
        val one = ledger()
        val two = Ledger(Account(AccountId("ACC-003"), Currency.AED, Money.aed("0.00")))
        val policy = OverdraftFeePolicy()
        one.append(EntryType.DEBIT, Money.aed("10.00"), Day(1))
        two.append(EntryType.DEBIT, Money.aed("10.00"), Day(1))

        policy.assess(one, Day(1))
        policy.assess(two, Day(1))

        assertEquals(1, one.entriesOfType(EntryType.OVERDRAFT_FEE).size)
        assertEquals(1, two.entriesOfType(EntryType.OVERDRAFT_FEE).size)
    }
}
