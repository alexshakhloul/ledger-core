package ledger

import ledger.replay.EventStream
import ledger.replay.ReplayEngine
import ledger.report.ConsoleReporter

/**
 * Entry point for the ledger core.
 */
fun main() {
    val result = ReplayEngine(EventStream.ACCOUNTS).replay(EventStream.EVENTS)
    print(ConsoleReporter.render(result))
}
