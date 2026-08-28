package ledger.domain

data class Day(val value: Int) : Comparable<Day> {

    init {
        require(value in FIRST..LAST) {
            "day $value is outside the replay window $FIRST..$LAST"
        }
    }

    override fun compareTo(other: Day): Int = value.compareTo(other.value)

    override fun toString(): String = "Day $value"

    companion object {
        const val FIRST = 1
        const val LAST = 6
        val WINDOW: List<Day> = (FIRST..LAST).map(::Day)
    }
}
