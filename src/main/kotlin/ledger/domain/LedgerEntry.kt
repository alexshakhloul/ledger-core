package ledger.domain

class LedgerEntry private constructor(
    val entryId: EntryId,
    val accountId: AccountId,
    val type: EntryType,
    val amount: Money,
    val valueDate: Day,
    val sourceEventId: EventId?,
    val reversalOf: EntryId?,
) {
    companion object {
        internal fun create(
            entryId: EntryId,
            accountId: AccountId,
            type: EntryType,
            amount: Money,
            valueDate: Day,
            sourceEventId: EventId?,
            reversalOf: EntryId?,
        ): LedgerEntry = LedgerEntry(
            entryId = entryId,
            accountId = accountId,
            type = type,
            amount = amount,
            valueDate = valueDate,
            sourceEventId = sourceEventId,
            reversalOf = reversalOf,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
                other is LedgerEntry &&
                        entryId == other.entryId &&
                        accountId == other.accountId &&
                        type == other.type &&
                        amount == other.amount &&
                        valueDate == other.valueDate &&
                        sourceEventId == other.sourceEventId &&
                        reversalOf == other.reversalOf
                )

    override fun hashCode(): Int {
        var result = entryId.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + valueDate.hashCode()
        result = 31 * result + (sourceEventId?.hashCode() ?: 0)
        result = 31 * result + (reversalOf?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = buildString {
        append("$entryId $type $amount value-dated $valueDate")
        sourceEventId?.let { append(" from $it") }
        reversalOf?.let { append(" reversing $it") }
    }
}

val LedgerEntry.signedAmount: Money
    get() = when (type) {
        EntryType.CREDIT, EntryType.INTEREST_CAPITALIZATION -> amount
        EntryType.DEBIT, EntryType.OVERDRAFT_FEE -> -amount
    }
