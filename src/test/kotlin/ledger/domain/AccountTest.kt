package ledger.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountTest {

    @Test
    fun `ACC-001 is an AED account opening at zero`() {
        val account = Account.acc001()
        assertEquals(AccountId("ACC-001"), account.id)
        assertEquals(Currency.AED, account.currency)
        assertEquals(Money.aed("0.00"), account.openingBalance)
    }

    @Test
    fun `ACC-002 is a BHD account opening at zero`() {
        val account = Account.acc002()
        assertEquals(AccountId("ACC-002"), account.id)
        assertEquals(Currency.BHD, account.currency)
        assertEquals(Money.bhd("0.000"), account.openingBalance)
    }

    @Test
    fun `an opening balance in the wrong currency is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Account(AccountId("ACC-001"), Currency.AED, Money.bhd("1.000"))
        }
    }

    @Test
    fun `an account id must not be blank`() {
        assertFailsWith<IllegalArgumentException> { AccountId("  ") }
    }
}
