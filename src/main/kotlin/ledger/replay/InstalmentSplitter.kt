package ledger.replay

import ledger.domain.Money

object InstalmentSplitter {

    fun split(total: Money, count: Int): List<Money> {
        require(count > 0) { "instalment count must be positive, was $count" }
        require(!total.isNegative) { "cannot split a negative total, was $total" }

        val base = total.amount / count
        val remainder = total.amount % count
        return List(count) { index ->
            Money.ofMinor(total.currency, if (index == count - 1) base + remainder else base)
        }
    }
}
