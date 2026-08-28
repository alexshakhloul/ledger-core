package ledger.report

import ledger.domain.AccountId
import ledger.domain.Day
import ledger.domain.EntryType
import ledger.domain.Ledger
import ledger.replay.*

object ConsoleReporter {

    private const val LABEL_WIDTH = 24
    private const val NONE = "none"

    fun render(result: ReplayResult): String = buildString {
        result.ledgers.forEach { ledger -> renderAccount(this, result, ledger) }
    }

    private fun renderAccount(out: StringBuilder, result: ReplayResult, ledger: Ledger) {
        val account = ledger.account
        out.append("=== ${account.id} ${account.currency.code} ===\n\n")

        Day.WINDOW.forEach { day ->
            out.append("$day\n")
            out.line("Events", events(result, account.id, day))
            out.line("Closing ledger balance", ledger.closingBalance(day).toString())
            out.line("Fees", fees(ledger, day))
            capitalization(result, account.id, day)?.let { out.line("Interest capitalization", it) }
            out.line("Authorizations", authorizations(result, account.id, day))
            out.line("Errors", errors(result, account.id, day))
            out.append('\n')
        }
    }

    private fun StringBuilder.line(label: String, value: String) {
        append("  ").append(label.padEnd(LABEL_WIDTH)).append(": ").append(value).append('\n')
    }

    private fun events(result: ReplayResult, accountId: AccountId, day: Day): String =
        result.events
            .filter { it.accountId == accountId && it.bookedOn == day }
            .joinToString("; ") { describe(it) }
            .ifEmpty { NONE }

    private fun describe(event: Event): String = when (event) {
        is CreditEvent ->
            "${event.id} CREDIT ${event.amount}${backdated(event.valueDate, event.bookedOn)}"

        is DebitEvent ->
            "${event.id} DEBIT ${event.amount}${backdated(event.valueDate, event.bookedOn)}"

        is AuthorizationEvent ->
            "${event.id} AUTHORIZATION ${event.authorizationId} ${event.amount}"

        is SettlementEvent ->
            "${event.id} SETTLEMENT ${event.authorizationId} ${event.amount}" +
                    backdated(event.valueDate, event.bookedOn)

        is CompensationEvent ->
            "${event.id} REVERSAL of ${event.reverses}"

        is InstalmentCreditEvent ->
            "${event.id} CREDIT ${event.total} in ${event.instalments} instalments" +
                    backdated(event.valueDate, event.bookedOn)
    }

    private fun backdated(valueDate: Day, bookedOn: Day): String =
        if (valueDate == bookedOn) "" else ", value-dated $valueDate"


    private fun fees(ledger: Ledger, day: Day): String =
        ledger.entriesOfType(EntryType.OVERDRAFT_FEE)
            .filter { it.valueDate == day }
            .joinToString("; ") { "${it.amount} overdraft" }
            .ifEmpty { NONE }

    private fun capitalization(result: ReplayResult, accountId: AccountId, day: Day): String? {
        if (day.value != Day.LAST) return null
        return result.interest[accountId]?.capitalized?.toString()
    }

    private fun authorizations(result: ReplayResult, accountId: AccountId, day: Day): String =
        result.authorizationActivity
            .filter { it.accountId == accountId && it.day == day }
            .joinToString("; ") { it.toString() }
            .ifEmpty { NONE }

    private fun errors(result: ReplayResult, accountId: AccountId, day: Day): String =
        result.errors
            .filter { it.accountId == accountId && it.bookedOn == day }
            .joinToString("; ") { it.toString() }
            .ifEmpty { NONE }
}
