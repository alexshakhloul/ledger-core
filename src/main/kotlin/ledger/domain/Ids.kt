package ledger.domain

data class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "account id must not be blank" }
    }

    override fun toString(): String = value
}

data class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "event id must not be blank" }
    }

    override fun toString(): String = value
}

data class EntryId(val value: Int) : Comparable<EntryId> {
    init {
        require(value > 0) { "entry id must be positive, was $value" }
    }

    override fun compareTo(other: EntryId): Int = value.compareTo(other.value)
    override fun toString(): String = "#$value"
}
