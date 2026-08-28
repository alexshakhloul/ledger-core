package ledger.replay

import ledger.domain.*

data class ReplayError(
    val eventId: EventId,
    val bookedOn: Day,
    val accountId: AccountId,
    val reason: String,
) {
    override fun toString(): String = "$eventId $reason"
}

data class AuthorizationActivity(
    val authorizationId: AuthorizationId,
    val accountId: AccountId,
    val day: Day,
    val status: AuthorizationStatus,
) {
    override fun toString(): String = "$authorizationId $status"
}

data class ReplayResult(
    val events: List<Event>,
    val ledgers: List<Ledger>,
    val authorizations: List<Authorization>,
    val authorizationActivity: List<AuthorizationActivity>,
    val errors: List<ReplayError>,
    val interest: Map<AccountId, InterestAccrual>,
) {
    fun ledgerFor(accountId: AccountId): Ledger = ledgers.first { it.account.id == accountId }

    fun authorization(id: AuthorizationId): Authorization = authorizations.first { it.id == id }
}

class ReplayEngine(accounts: List<Account>) {

    private val ledgers: Map<AccountId, Ledger> = accounts.associate { it.id to Ledger(it) }
    private val authorizations = LinkedHashMap<AuthorizationId, Authorization>()
    private val activity = mutableListOf<AuthorizationActivity>()
    private val errors = mutableListOf<ReplayError>()

    private val entryByEvent = mutableMapOf<EventId, EntryId>()

    private val feePolicy = OverdraftFeePolicy()
    private val interestPolicy = InterestPolicy()

    fun replay(events: List<Event>): ReplayResult {
        events.forEach(::apply)

        val interest = ledgers.values.associate { it.account.id to interestPolicy.capitalize(it) }
        return ReplayResult(
            events = events,
            ledgers = ledgers.values.toList(),
            authorizations = authorizations.values.toList(),
            authorizationActivity = activity.toList(),
            errors = errors.toList(),
            interest = interest,
        )
    }

    private fun apply(event: Event) = when (event) {
        is CreditEvent -> post(event, EntryType.CREDIT, event.amount, event.valueDate)
        is DebitEvent -> post(event, EntryType.DEBIT, event.amount, event.valueDate)
        is AuthorizationEvent -> authorize(event)
        is SettlementEvent -> settle(event)
        is CompensationEvent -> reverse(event)
        is InstalmentCreditEvent -> postInstalments(event)
    }

    private fun post(event: Event, type: EntryType, amount: Money, valueDate: Day) {
        val ledger = ledgerFor(event.accountId)
        val entry = ledger.append(type, amount, valueDate, sourceEventId = event.id)
        entryByEvent[event.id] = entry.entryId
        feePolicy.assess(ledger, valueDate)
    }

    private fun postInstalments(event: InstalmentCreditEvent) {
        val ledger = ledgerFor(event.accountId)
        InstalmentSplitter.split(event.total, event.instalments).forEach { instalment ->
            ledger.append(EntryType.CREDIT, instalment, event.valueDate, sourceEventId = event.id)
        }
        feePolicy.assess(ledger, event.valueDate)
    }

    private fun authorize(event: AuthorizationEvent) {
        val available = availableBalance(event.accountId, event.bookedOn)
        val approved = !(available - event.amount).isNegative
        val status = if (approved) AuthorizationStatus.APPROVED else AuthorizationStatus.REJECTED

        authorizations[event.authorizationId] = Authorization(
            id = event.authorizationId,
            accountId = event.accountId,
            amount = event.amount,
            bookedOn = event.bookedOn,
            status = status,
        )
        record(event.authorizationId, event.accountId, event.bookedOn, status)
        if (!approved) refuse(event, "insufficient available balance")
    }

    private fun availableBalance(accountId: AccountId, on: Day): Money {
        val ledger = ledgerFor(accountId)
        val holds = authorizations.values
            .filter { it.accountId == accountId && it.isActive }
            .map { it.amount }
            .sum(ledger.account.currency)
        return ledger.closingBalance(on) - holds
    }

    private fun settle(event: SettlementEvent) {
        val authorization = authorizations[event.authorizationId]
        val refusal = when {
            authorization == null -> "unknown authorization ${event.authorizationId}"
            authorization.accountId != event.accountId -> "authorization belongs to another account"
            authorization.amount.currency != event.amount.currency -> "currency mismatch"
            authorization.status == AuthorizationStatus.SETTLED -> "authorization already settled"
            authorization.status == AuthorizationStatus.REJECTED -> "authorization was rejected"
            event.amount > authorization.amount -> "settlement exceeds authorized amount"
            else -> null
        }
        if (authorization == null || refusal != null) {
            refuse(event, refusal ?: "unknown authorization")
            return
        }

        post(event, EntryType.DEBIT, event.amount, event.valueDate)
        authorizations[event.authorizationId] =
            authorization.copy(status = AuthorizationStatus.SETTLED)
        record(event.authorizationId, event.accountId, event.bookedOn, AuthorizationStatus.SETTLED)
    }

    private fun reverse(event: CompensationEvent) {
        val ledger = ledgerFor(event.accountId)
        val original = entryByEvent[event.reverses]?.let(ledger::entry)
        if (original == null) {
            refuse(event, "unknown entry to reverse ${event.reverses}")
            return
        }

        val opposite = when (original.type) {
            EntryType.DEBIT -> EntryType.CREDIT
            EntryType.CREDIT -> EntryType.DEBIT
            else -> {
                refuse(event, "cannot reverse a ${original.type} entry")
                return
            }
        }

        ledger.append(
            opposite, original.amount, original.valueDate,
            sourceEventId = event.id, reversalOf = original.entryId,
        )
        feePolicy.assess(ledger, original.valueDate)
    }

    private fun ledgerFor(accountId: AccountId): Ledger =
        ledgers[accountId] ?: error("no ledger for $accountId")

    private fun refuse(event: Event, reason: String) {
        errors += ReplayError(event.id, event.bookedOn, event.accountId, reason)
    }

    private fun record(id: AuthorizationId, accountId: AccountId, day: Day, status: AuthorizationStatus) {
        activity += AuthorizationActivity(id, accountId, day, status)
    }
}
