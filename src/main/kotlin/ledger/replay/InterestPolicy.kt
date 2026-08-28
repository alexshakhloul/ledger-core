package ledger.replay

import ledger.domain.*
import java.math.BigDecimal

data class InterestAccrual(
    val daily: Map<Day, Money>,
    val capitalized: Money,
)

class InterestPolicy(private val dailyRate: BigDecimal = BigDecimal("0.0004")) {

    fun capitalize(ledger: Ledger): InterestAccrual {
        val currency = ledger.account.currency
        val daily = Day.WINDOW.associateWith { day -> accrualOn(ledger, day) }
        val total = daily.values.sum(currency)

        if (!total.isZero) {
            ledger.append(EntryType.INTEREST_CAPITALIZATION, total, Day(Day.LAST))
        }
        return InterestAccrual(daily, total)
    }

    private fun accrualOn(ledger: Ledger, day: Day): Money {
        val base = ledger.closingBalance(day, excluding = setOf(EntryType.INTEREST_CAPITALIZATION))
        if (!base.isPositive) return Money.zero(ledger.account.currency)

        return Money.of(ledger.account.currency, base.toBigDecimal().multiply(dailyRate))
    }
}
