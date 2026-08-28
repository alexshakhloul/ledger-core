package ledger.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import ledger.domain.AccountId
import ledger.domain.Day
import ledger.domain.Money
import org.junit.jupiter.api.Tag

class KnownFailureTest {

    @Test
    @Tag("known-failure")
    fun `acceptance criterion 6 - after E9 all balances and fees return to their pre-E7 values`() {
        val result = ReplayEngine(EventStream.ACCOUNTS).replay(EventStream.EVENTS)

        assertEquals(
            Money.aed("250.00"),
            result.ledgerFor(AccountId("ACC-001")).closingBalance(Day(2)),
            "criterion 6 expects the pre-E7 Day 2 balance to be restored in full",
        )
    }
}
