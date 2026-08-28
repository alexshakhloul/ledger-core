package ledger.replay

import ledger.domain.*

class OverdraftFeePolicy(private val fee: Money = Money.aed("25.00")) {
    private val assessed = mutableSetOf<Pair<AccountId, Day>>()

    fun assess(ledger: Ledger, valueDate: Day) {
        val key = ledger.account.id to valueDate
        if (key in assessed) return
        if (!ledger.closingBalance(valueDate).isNegative) return
        if (ledger.account.currency != Currency.AED) {
            throw FeeNotDefinedForCurrency(ledger.account.currency)
        }

        assessed += key
        ledger.append(EntryType.OVERDRAFT_FEE, fee, valueDate)
    }
}

class FeeNotDefinedForCurrency(currency: Currency) :
    IllegalStateException("no overdraft fee is defined for ${currency.code}")
