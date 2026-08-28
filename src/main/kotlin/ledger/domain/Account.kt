package ledger.domain

data class Account(
    val id: AccountId,
    val currency: Currency,
    val openingBalance: Money
) {
    init {
        require(openingBalance.currency == currency) {
            "$id opens in ${openingBalance.currency.code} but is an ${currency.code} account"
        }
    }

    companion object {
        //The two accounts in the supplied stream, both opening at zero just for the demo
        fun acc001(): Account =
            Account(AccountId("ACC-001"), Currency.AED, Money.aed("0.00"))

        fun acc002(): Account =
            Account(AccountId("ACC-002"), Currency.BHD, Money.bhd("0.000"))
    }
}
