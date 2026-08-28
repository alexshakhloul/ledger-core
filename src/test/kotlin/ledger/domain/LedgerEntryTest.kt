package ledger.domain

import kotlin.test.*

class LedgerEntryTest {

    private fun ledger() = Ledger(Account.acc001())

    @Test
    fun `credits and interest capitalization add`() {
        val ledger = ledger()
        val credit = ledger.append(EntryType.CREDIT, Money.aed("1200.00"), Day(1))
        val interest = ledger.append(EntryType.INTEREST_CAPITALIZATION, Money.aed("0.98"), Day(6))

        assertEquals(Money.aed("1200.00"), credit.signedAmount)
        assertEquals(Money.aed("0.98"), interest.signedAmount)
    }

    @Test
    fun `debits and overdraft fees subtract`() {
        val ledger = ledger()
        val debit = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2))
        val fee = ledger.append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))

        assertEquals(Money.aed("-620.00"), debit.signedAmount)
        assertEquals(Money.aed("-25.00"), fee.signedAmount)
    }

    @Test
    fun `the amount itself is always a positive magnitude, direction lives in the type`() {
        val fee = ledger().append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))
        assertEquals(Money.aed("25.00"), fee.amount)
        assertEquals(Money.aed("-25.00"), fee.signedAmount)
    }

    @Test
    fun `a negative amount is rejected, because the sign belongs to the type`() {
        assertFailsWith<IllegalArgumentException> {
            ledger().append(EntryType.DEBIT, Money.aed("-620.00"), Day(2))
        }
    }


    @Test
    fun `an engine-raised entry has no source event`() {
        // The overdraft fee is assessed by the engine, not requested by any event in the stream.
        val fee = ledger().append(EntryType.OVERDRAFT_FEE, Money.aed("25.00"), Day(2))
        assertNull(fee.sourceEventId)
        assertNull(fee.reversalOf)
    }

    @Test
    fun `a reversal carries both its own event and the entry it offsets`() {
        // E9 reverses E7.
        val ledger = ledger()
        val original = ledger.append(
            EntryType.DEBIT, Money.aed("620.00"), Day(2), sourceEventId = EventId("E7"),
        )
        val reversal = ledger.append(
            EntryType.CREDIT,
            Money.aed("620.00"),
            Day(2),
            sourceEventId = EventId("E9"),
            reversalOf = original.entryId,
        )

        assertEquals(EventId("E9"), reversal.sourceEventId)
        assertEquals(original.entryId, reversal.reversalOf)
        assertEquals(EntryType.CREDIT, reversal.type)
    }

    @Test
    fun `entries with the same fields are equal`() {
        val a = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))
        val b = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `every field counts towards equality`() {
        val base = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E4"))

        val differingType = ledger().append(EntryType.DEBIT, Money.aed("400.00"), Day(3), EventId("E4"))
        val differingAmount = ledger().append(EntryType.CREDIT, Money.aed("401.00"), Day(3), EventId("E4"))
        val differingValueDate = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(4), EventId("E4"))
        val differingEvent = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(3), EventId("E5"))
        val noEvent = ledger().append(EntryType.CREDIT, Money.aed("400.00"), Day(3))

        listOf(differingType, differingAmount, differingValueDate, differingEvent, noEvent)
            .forEach { assertNotEquals(base, it) }
    }

    @Test
    fun `two entries differing only in what they reverse are not equal`() {
        val ledger = ledger()
        val first = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))
        val second = ledger.append(EntryType.DEBIT, Money.aed("620.00"), Day(2), EventId("E7"))

        val reversesFirst = ledger.append(
            EntryType.CREDIT, Money.aed("620.00"), Day(2), EventId("E9"), reversalOf = first.entryId,
        )
        val reversesSecond = ledger.append(
            EntryType.CREDIT, Money.aed("620.00"), Day(2), EventId("E9"), reversalOf = second.entryId,
        )

        assertNotEquals(reversesFirst.reversalOf, reversesSecond.reversalOf)
    }

    @Test
    fun `a reversal must offset an entry that exists on this account`() {
        assertFailsWith<IllegalArgumentException> {
            ledger().append(
                EntryType.CREDIT, Money.aed("620.00"), Day(2), reversalOf = EntryId(999),
            )
        }
    }

    @Test
    fun `an entry renders with its id, type, amount and value date`() {
        val entry = ledger().append(
            EntryType.DEBIT, Money.aed("620.00"), Day(2), sourceEventId = EventId("E7"),
        )
        assertEquals("#1 DEBIT AED 620.00 value-dated Day 2 from E7", entry.toString())
    }

    @Test
    fun `an entry in the wrong currency for the account is rejected`() {
        assertFailsWith<EntryCurrencyMismatchException> {
            ledger().append(EntryType.CREDIT, Money.bhd("1.000"), Day(1))
        }
    }
}
