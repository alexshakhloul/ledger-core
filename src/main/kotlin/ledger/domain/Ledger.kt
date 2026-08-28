package ledger.domain

class Ledger(private val account: Account) {
    private val postedEntries = mutableListOf<LedgerEntry>()
    private var nextEntryId = 1

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
        // A reversal must offset something real on this account. EntryId is only unique within
        // one ledger, so an id borrowed from another account would resolve to the wrong entry.
        // Catching it here fails at the mistake rather than leaving a dangling reference for
        // the report to trip over later.
        if (reversalOf != null) {
            require(postedEntries.any { it.entryId == reversalOf }) {
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
        return entry
    }

    /**
     * The account's closing balance on [day]: everything value-dated on or before it.
     *
     * This is the value-dating rule, and the central query of the whole design. It recomputes
     * from full history every call and pays no attention to the order entries were appended in,
     * which is exactly what lets E7 (posted on booking Day 5, value-dated Day 2) change what
     * Day 2 closed at.
     *
     * [excluding] leaves out entries of the given types. We'll need it for the interest base:
     * an accrual computed from a balance that already contains the capitalization would be
     * defined in terms of itself.
     */
    fun closingBalance(day: Day, excluding: Set<EntryType> = emptySet()): Money =
        account.openingBalance + postedEntries
            .filter { it.valueDate <= day && it.type !in excluding }
            .map { it.signedAmount }
            .sum(account.currency)

    //ordered entries by post
    fun entries(): List<LedgerEntry> = postedEntries.toList()

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
