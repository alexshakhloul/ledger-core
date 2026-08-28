package ledger.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerTest {

    private fun aed() = Ledger(Account.acc001())
    private fun bhd() = Ledger(Account.acc002())

    @Test
    fun `entry ids are assigned monotonically from one`() {
        val ledger = aed()
        val first = ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        val second = ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1))
        val third = ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3))

        assertEquals(
            listOf(EntryId(1), EntryId(2), EntryId(3)),
            listOf(first, second, third).map { it.entryId },
        )
    }

    @Test
    fun `each account numbers its own entries independently`() {
        val one = aed()
        val two = bhd()
        one.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        one.append(EntryType.DEBIT, Money.aed("950.00"), Day(1))
        val firstOnTwo = two.append(EntryType.CREDIT, Money.bhd("3.333"), Day(5))

        // ACC-002's first entry is #1 regardless of ACC-001
        assertEquals(EntryId(1), firstOnTwo.entryId)
    }

    @Test
    fun `entries returns a snapshot that later appends do not disturb`() {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        val snapshot = ledger.entries()

        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1))

        assertEquals(1, snapshot.size)
        assertEquals(2, ledger.entries().size)
    }

    @Test
    fun `a reversal appends a compensating credit and leaves the original untouched`() {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1), EventId("E1"))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1), EventId("E2"))
        val original = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))
        val before = ledger.entries()

        ledger.append(
            EntryType.CREDIT, Money.aed("620.00"), Day(2), EventId("E9"),
            reversalOf = original.entryId,
        )

        assertTrue(ledger.entries().containsAll(before))
        assertEquals(4, ledger.entries().size)
        assertEquals(Money.aed("620.00"), original.amount)
    }

    @Test
    fun `closing balance counts only entries value-dated on or before the day`() {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1), EventId("E1"))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1), EventId("E2"))
        ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))

        assertEquals(Money.aed("250.00"), ledger.closingBalance(Day(1)))
        assertEquals(Money.aed("250.00"), ledger.closingBalance(Day(2)))
        assertEquals(Money.aed("650.00"), ledger.closingBalance(Day(3)))
    }

    @Test
    fun `a backdated entry changes what an earlier day closed at`() {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1), EventId("E1"))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1), EventId("E2"))
        ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))
        ledger.append(EntryType.DEBIT, Money.aed("185.00"), Day(4), EventId("E5"))
        ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))

        assertEquals(Money.aed("-370.00"), ledger.closingBalance(Day(2)))
    }

    @Test
    fun `the fee leaves Day 2 still negative, which is why the idempotency key is needed`() {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1), EventId("E1"))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1), EventId("E2"))
        ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))
        ledger.append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))

        assertEquals(Money.aed("-395.00"), ledger.closingBalance(Day(2)))
    }

    @Test
    fun `the ACC-001 history reproduces every closing balance in the architecture`() {
        val ledger = fullyReplayedAcc001()

        assertEquals(Money.aed("250.00"), ledger.closingBalance(Day(1)))
        assertEquals(Money.aed("225.00"), ledger.closingBalance(Day(2)))
        assertEquals(Money.aed("625.00"), ledger.closingBalance(Day(3)))
        assertEquals(Money.aed("440.00"), ledger.closingBalance(Day(4)))
        assertEquals(Money.aed("440.00"), ledger.closingBalance(Day(5)))
        assertEquals(Money.aed("440.00"), ledger.closingBalance(Day(6)))
    }

    @Test
    fun `exactly one overdraft fee survives the whole history, on Day 2, for AED 25`() {
        val fee = fullyReplayedAcc001().entriesOfType(EntryType.OVERDRAFT_FEE).single()
        assertEquals(Day(2), fee.valueDate)
        assertEquals(Money.aed("25.00"), fee.amount)
    }

    @Test
    fun `excluding capitalization pins the Day 6 interest base below the closing balance`() {
        val ledger = fullyReplayedAcc001()
        ledger.append(EntryType.INTEREST_CAPITALIZATION, Money.aed("0.98"), Day(6))

        assertEquals(Money.aed("440.98"), ledger.closingBalance(Day(6)))
        assertEquals(
            Money.aed("440.00"),
            ledger.closingBalance(Day(6), excluding = setOf(EntryType.INTEREST_CAPITALIZATION)),
        )
    }

    @Test
    fun `the exclusion works identically at BHD three-decimal scale`() {
        val ledger = bhd()
        listOf("3.333", "3.333", "3.334").forEach {
            ledger.append(EntryType.CREDIT, Money.bhd(it), Day(5), EventId("E10"))
        }
        assertEquals(Money.bhd("10.000"), ledger.closingBalance(Day(5)))

        ledger.append(EntryType.INTEREST_CAPITALIZATION, Money.bhd("0.008"), Day(6))

        assertEquals(Money.bhd("10.008"), ledger.closingBalance(Day(6)))
        assertEquals(
            Money.bhd("10.000"),
            ledger.closingBalance(Day(6), excluding = setOf(EntryType.INTEREST_CAPITALIZATION)),
        )
    }

    @Test
    fun `an account with no entries closes at its opening balance`() {
        assertEquals(Money.bhd("0.000"), bhd().closingBalance(Day(4)))
    }

    private fun fullyReplayedAcc001(): Ledger {
        val ledger = aed()
        ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1), EventId("E1"))
        ledger.append(EntryType.DEBIT, Money.aed("950.00"), Day(1), EventId("E2"))
        ledger.append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))
        ledger.append(EntryType.DEBIT, Money.aed("185.00"), Day(4), EventId("E5"))
        val backdated = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))
        ledger.append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))
        ledger.append(
            EntryType.CREDIT, Money.aed("620.00"), Day(2), EventId("E9"),
            reversalOf = backdated.entryId,
        )
        return ledger
    }
}
