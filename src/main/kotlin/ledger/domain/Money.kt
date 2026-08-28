package ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode

class Money private constructor(val currency: Currency, val amount: Long) : Comparable<Money> {

    companion object {
        fun of(currency: Currency, amount: BigDecimal): Money {
            val scaled = amount.setScale(currency.scale, RoundingMode.HALF_UP)
            return Money(currency, scaled.movePointRight(currency.scale).longValueExact())
        }

        fun of(currency: Currency, amount: String): Money = of(currency, BigDecimal(amount))

        fun ofMinor(currency: Currency, minor: Long): Money = Money(currency, minor)

        fun zero(currency: Currency): Money = Money(currency, 0)

        fun aed(amount: String): Money = of(Currency.AED, amount)

        fun bhd(amount: String): Money = of(Currency.BHD, amount)
    }

    val isNegative: Boolean get() = amount < 0
    val isPositive: Boolean get() = amount > 0
    val isZero: Boolean get() = amount == 0L
    operator fun plus(other: Money): Money =
        Money(currency, Math.addExact(amount, sameCurrency(other).amount))

    operator fun minus(other: Money): Money =
        Money(currency, Math.subtractExact(amount, sameCurrency(other).amount))

    operator fun unaryMinus(): Money = Money(currency, Math.negateExact(amount))

    override fun compareTo(other: Money): Int = amount.compareTo(sameCurrency(other).amount)

    /** The exact decimal value, at the currency's scale. */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amount, currency.scale)

    private fun sameCurrency(other: Money): Money =
        if (other.currency == currency) other
        else throw CurrencyMismatchException(currency, other.currency)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Money && currency == other.currency && amount == other.amount)

    override fun hashCode(): Int = 31 * currency.hashCode() + amount.hashCode()

    override fun toString(): String = "${currency.code} ${toBigDecimal().toPlainString()}"
}

fun Iterable<Money>.sum(currency: Currency): Money =
    fold(Money.zero(currency)) { running, next -> running + next }


class CurrencyMismatchException(left: Currency, right: Currency) :
    IllegalArgumentException("cannot combine ${left.code} with ${right.code}")
