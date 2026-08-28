package ledger.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ledger.domain.AccountId
import ledger.domain.AuthorizationId
import ledger.domain.AuthorizationStatus
import ledger.domain.Day
import ledger.domain.EntryType
import ledger.domain.EventId
import ledger.domain.Money
import ledger.report.ConsoleReporter

class AcceptanceCriteriaTest {

    private val result = ReplayEngine(EventStream.ACCOUNTS).replay(EventStream.EVENTS)
    private val acc001 = AccountId("ACC-001")
    private val acc002 = AccountId("ACC-002")

    @Test
    fun `criterion 1 - the backdated debit drives Day 2 to AED -370_00 before any fee`() {
        val beforeReversal = EventStream.EVENTS.filterNot { it.id == EventId("E9") }
        val partial = ReplayEngine(EventStream.ACCOUNTS).replay(beforeReversal)

        assertEquals(
            Money.aed("-370.00"),
            partial.ledgerFor(acc001)
                .closingBalance(Day(2), excluding = setOf(EntryType.OVERDRAFT_FEE)),
        )
    }

    @Test
    fun `criterion 2 - E7 causes exactly one overdraft fee, on Day 2`() {
        val fees = result.ledgerFor(acc001).entriesOfType(EntryType.OVERDRAFT_FEE)

        assertEquals(1, fees.size)
        assertEquals(Day(2), fees.single().valueDate)
        assertEquals(Money.aed("25.00"), fees.single().amount)
    }

    @Test
    fun `no fee is charged on the days E7 made negative in passing`() {
        // Days 4, 5 and 6 were all overdrawn between E7 and E9, and none is charged, because no
        // activity was value-dated to them. Re-scanning the window would have charged four fees.
        assertEquals(1, result.ledgerFor(acc001).entriesOfType(EntryType.OVERDRAFT_FEE).size)
        assertEquals(0, result.ledgerFor(acc002).entriesOfType(EntryType.OVERDRAFT_FEE).size)
    }

    @Test
    fun `criterion 3 - the Day 4 settlement of Auth-A is accepted`() {
        assertEquals(
            AuthorizationStatus.SETTLED,
            result.authorization(AuthorizationId("Auth-A")).status,
        )
        val settlement = result.ledgerFor(acc001).entries()
            .single { it.sourceEventId == EventId("E5") }
        assertEquals(EntryType.DEBIT, settlement.type)
        assertEquals(Money.aed("185.00"), settlement.amount)
    }

    @Test
    fun `criterion 4 - a settlement against an unknown authorization takes no money`() {
        val refusal = result.errors.single { it.eventId == EventId("E6") }
        assertTrue(refusal.reason.contains("unknown authorization"))

        // Nothing was posted for E6, so the AED 180.00 never left the account.
        assertTrue(result.ledgerFor(acc001).entries().none { it.sourceEventId == EventId("E6") })
    }

    @Test
    fun `criterion 5 - Auth-B is rejected, so its premise never arises`() {
        // The rule it states is right: a hold moves available balance, not ledger balance. But
        // Auth-B is refused, so no hold is created and the criterion asserts nothing here.
        assertEquals(
            AuthorizationStatus.REJECTED,
            result.authorization(AuthorizationId("Auth-B")).status,
        )
        assertTrue(result.errors.any { it.eventId == EventId("E8") })
        assertTrue(result.ledgerFor(acc001).entries().none { it.sourceEventId == EventId("E8") })
    }

    @Test
    fun `criterion 6 refused - the fee outlives the reversal`() {
        // E9 gives back the AED 620.00 but not the AED 25.00 it caused. Day 2 lands at 225.00,
        // not the 250.00 it held before E7. Append-only returns the amount, never the consequence.
        assertEquals(Money.aed("225.00"), result.ledgerFor(acc001).closingBalance(Day(2)))
        assertEquals(1, result.ledgerFor(acc001).entriesOfType(EntryType.OVERDRAFT_FEE).size)
    }

    @Test
    fun `the reversal is a new entry and the original is untouched`() {
        val entries = result.ledgerFor(acc001).entries()
        val original = entries.single { it.sourceEventId == EventId("E7") }
        val reversal = entries.single { it.sourceEventId == EventId("E9") }

        assertEquals(EntryType.DEBIT, original.type)
        assertEquals(EntryType.CREDIT, reversal.type)
        assertEquals(original.entryId, reversal.reversalOf)
        assertEquals(original.valueDate, reversal.valueDate)
        assertEquals(Money.aed("620.00"), original.amount)
    }

    @Test
    fun `criterion 7 refused - the instalments are 3_333 3_333 3_334, not 3_334 each`() {
        val instalments = result.ledgerFor(acc002).entries()
            .filter { it.sourceEventId == EventId("E10") }
            .map { it.amount }

        assertEquals(listOf(Money.bhd("3.333"), Money.bhd("3.333"), Money.bhd("3.334")), instalments)
        assertEquals(Money.bhd("10.000"), result.ledgerFor(acc002).closingBalance(Day(5)))
    }

    @Test
    fun `criterion 8 refused - no rounding remainder is discarded`() {
        result.interest.forEach { (accountId, accrual) ->
            val currency = result.ledgerFor(accountId).account.currency
            assertEquals(
                accrual.daily.values.fold(Money.zero(currency)) { a, b -> a + b },
                accrual.capitalized,
                "$accountId dailies must sum to the capitalized total",
            )
        }
    }

    @Test
    fun `every closing balance matches the expected outcome`() {
        val aed = result.ledgerFor(acc001)
        assertEquals(Money.aed("250.00"), aed.closingBalance(Day(1)))
        assertEquals(Money.aed("225.00"), aed.closingBalance(Day(2)))
        assertEquals(Money.aed("625.00"), aed.closingBalance(Day(3)))
        assertEquals(Money.aed("440.00"), aed.closingBalance(Day(4)))
        assertEquals(Money.aed("440.00"), aed.closingBalance(Day(5)))
        assertEquals(Money.aed("440.98"), aed.closingBalance(Day(6)))

        val bhd = result.ledgerFor(acc002)
        (1..4).forEach { assertEquals(Money.bhd("0.000"), bhd.closingBalance(Day(it))) }
        assertEquals(Money.bhd("10.000"), bhd.closingBalance(Day(5)))
        assertEquals(Money.bhd("10.008"), bhd.closingBalance(Day(6)))
    }

    @Test
    fun `the replay is deterministic`() {
        val again = ReplayEngine(EventStream.ACCOUNTS).replay(EventStream.EVENTS)
        assertEquals(ConsoleReporter.render(result), ConsoleReporter.render(again))
    }

    @Test
    fun `the report reads exactly as specified`() {
        assertEquals(EXPECTED_REPORT, ConsoleReporter.render(result))
    }

    private companion object {
        val EXPECTED_REPORT = """
            === ACC-001 AED ===

            Day 1
              Events                  : E1 CREDIT AED 1200.00; E2 DEBIT AED 950.00
              Closing ledger balance  : AED 250.00
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 2
              Events                  : E3 AUTHORIZATION Auth-A AED 200.00
              Closing ledger balance  : AED 225.00
              Fees                    : AED 25.00 overdraft
              Authorizations          : Auth-A APPROVED
              Errors                  : none

            Day 3
              Events                  : E4 CREDIT AED 400.00
              Closing ledger balance  : AED 625.00
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 4
              Events                  : E5 SETTLEMENT Auth-A AED 185.00; E6 SETTLEMENT Auth-Z AED 180.00
              Closing ledger balance  : AED 440.00
              Fees                    : none
              Authorizations          : Auth-A SETTLED
              Errors                  : E6 unknown authorization Auth-Z

            Day 5
              Events                  : E7 DEBIT AED 620.00, value-dated Day 2; E8 AUTHORIZATION Auth-B AED 90.00
              Closing ledger balance  : AED 440.00
              Fees                    : none
              Authorizations          : Auth-B REJECTED
              Errors                  : E8 insufficient available balance

            Day 6
              Events                  : E9 REVERSAL of E7
              Closing ledger balance  : AED 440.98
              Fees                    : none
              Interest capitalization : AED 0.98
              Authorizations          : none
              Errors                  : none

            === ACC-002 BHD ===

            Day 1
              Events                  : none
              Closing ledger balance  : BHD 0.000
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 2
              Events                  : none
              Closing ledger balance  : BHD 0.000
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 3
              Events                  : none
              Closing ledger balance  : BHD 0.000
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 4
              Events                  : none
              Closing ledger balance  : BHD 0.000
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 5
              Events                  : E10 CREDIT BHD 10.000 in 3 instalments
              Closing ledger balance  : BHD 10.000
              Fees                    : none
              Authorizations          : none
              Errors                  : none

            Day 6
              Events                  : none
              Closing ledger balance  : BHD 10.008
              Fees                    : none
              Interest capitalization : BHD 0.008
              Authorizations          : none
              Errors                  : none
        """.trimIndent() + "\n\n"
    }
}
