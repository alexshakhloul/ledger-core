package ledger.replay

import ledger.domain.*

//Use case scenario
object EventStream {

    val ACCOUNTS: List<Account> = listOf(Account.acc001(), Account.acc002())

    private val acc001 = Account.acc001().id
    private val acc002 = Account.acc002().id

    val EVENTS: List<Event> = listOf(
        CreditEvent(EventId("E1"), Day(1), acc001, Money.aed("1200.00"), Day(1)),
        DebitEvent(EventId("E2"), Day(1), acc001, Money.aed("950.00"), Day(1)),
        AuthorizationEvent(EventId("E3"), Day(2), acc001, AuthorizationId("Auth-A"), Money.aed("200.00")),
        CreditEvent(EventId("E4"), Day(3), acc001, Money.aed("400.00"), Day(3)),
        SettlementEvent(EventId("E5"), Day(4), acc001, AuthorizationId("Auth-A"), Money.aed("185.00"), Day(4)),
        // Auth-Z was never authorized. Rejected, and no money leaves the account
        SettlementEvent(EventId("E6"), Day(4), acc001, AuthorizationId("Auth-Z"), Money.aed("180.00"), Day(4)),
        // Booked Day 5, value-dated Day 2. This drives Day 2 negative and triggers a fee
        DebitEvent(EventId("E7"), Day(5), acc001, Money.aed("620.00"), Day(2)),
        AuthorizationEvent(EventId("E8"), Day(5), acc001, AuthorizationId("Auth-B"), Money.aed("90.00")),
        CompensationEvent(EventId("E9"), Day(6), acc001, reverses = EventId("E7")),
        InstalmentCreditEvent(
            EventId("E10"), Day(5), acc002, Money.bhd("10.000"), instalments = 3, valueDate = Day(5),
        ),
    )
}
