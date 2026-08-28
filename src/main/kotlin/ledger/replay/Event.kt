package ledger.replay

import ledger.domain.*

/**
 * One instruction from the supplied stream.
 *
 * Every event carries the day it was booked and the account it touches. Most also carry a value
 * date, which is the day the money is effective on and need not be the booking day: E7 is booked
 * on Day 5 and value-dated Day 2, which is what makes the whole exercise interesting.
 *
 * Authorizations have no value date because they post nothing to the ledger.
 */
sealed interface Event {
    val id: EventId
    val bookedOn: Day
    val accountId: AccountId
}

data class CreditEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val amount: Money,
    val valueDate: Day,
) : Event

data class DebitEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val amount: Money,
    val valueDate: Day,
) : Event

data class AuthorizationEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val authorizationId: AuthorizationId,
    val amount: Money,
) : Event

data class SettlementEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val authorizationId: AuthorizationId,
    val amount: Money,
    val valueDate: Day,
) : Event

data class CompensationEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val reverses: EventId,
) : Event

data class InstalmentCreditEvent(
    override val id: EventId,
    override val bookedOn: Day,
    override val accountId: AccountId,
    val total: Money,
    val instalments: Int,
    val valueDate: Day,
) : Event
