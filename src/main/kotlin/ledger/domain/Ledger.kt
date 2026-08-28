package ledger.domain


class Ledger(val account: Account) {

    private val postedEntries = mutableListOf<LedgerEntry>()
    private val entriesById = mutableMapOf<EntryId, LedgerEntry>()
    private var nextEntryId = 1

    private val dailyTotals: Array<Array<Money>> =
        Array(Day.LAST) { Array(EntryType.entries.size) { Money.zero(account.currency) } }

    fun append(
        type: EntryType,
        amount: Money,
        valueDate: Day,
        sourceEventId: EventId? = null,
        reversalOf: EntryId? = null,
    ): LedgerEntry {
        if (amount.currency != account.currency) {
            throw EntryCurrencyMismatchException(account.id, account.currency, amount.currency)
        }
        require(!amount.isNegative) {
            "entry amount is a magnitude and must not be negative, was $amount"
        }

        if (reversalOf != null) {
            require(entriesById.containsKey(reversalOf)) {
                "cannot reverse $reversalOf: no such entry on ${account.id}"
            }
        }

        val entry = LedgerEntry.create(
            entryId = EntryId(nextEntryId),
            accountId = account.id,
            type = type,
            amount = amount,
            valueDate = valueDate,
            sourceEventId = sourceEventId,
            reversalOf = reversalOf,
        )
        nextEntryId += 1
        postedEntries += entry
        entriesById[entry.entryId] = entry

        val totalsForDay = dailyTotals[valueDate.value - Day.FIRST]
        totalsForDay[type.ordinal] = totalsForDay[type.ordinal] + entry.signedAmount

        return entry
    }

    fun closingBalance(day: Day, excluding: Set<EntryType> = emptySet()): Money {
        var balance = account.openingBalance
        for (index in 0 until day.value - Day.FIRST + 1) {
            val totalsForDay = dailyTotals[index]
            for (type in EntryType.entries) {
                if (type !in excluding) balance += totalsForDay[type.ordinal]
            }
        }
        return balance
    }

    fun entries(): List<LedgerEntry> = postedEntries.toList()

    fun entry(entryId: EntryId): LedgerEntry? = entriesById[entryId]

    fun entriesOfType(type: EntryType): List<LedgerEntry> =
        postedEntries.filter { it.type == type }
}

class EntryCurrencyMismatchException(
    accountId: AccountId,
    accountCurrency: Currency,
    entryCurrency: Currency,
) : IllegalArgumentException(
    "entry in ${entryCurrency.code} does not belong on $accountId, " +
            "which is a ${accountCurrency.code} account",
)

