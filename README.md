# ledger-core

An in-memory account ledger core. It replays an event stream over Day 1 to Day 6 and
prints a daily report of closing ledger balance, fee assessments, authorization states and errors.

No web layer, no persistence, no UI, no database. Kotlin on the JVM, Gradle, and nothing at runtime (as per specs).

The design and the reasoning behind every contested rule are in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Running it

Make sure you have JDK 21 installed and java_home is pointing to it.

```bash
./gradlew run     # replays the stream and prints the daily report
./gradlew test    # the full suite, must be GREEN
```

```bash
./gradlew knownFailureTest   # expected to be RED
```

## Reading the output

The report prints one block per account, then one section per day:

```
Day 5
  Events                  : E7 DEBIT AED 620.00, value-dated Day 2; E8 AUTHORIZATION Auth-B AED 90.00
  Closing ledger balance  : AED 440.00
  Fees                    : none
  Authorizations          : Auth-B REJECTED
  Errors                  : E8 insufficient available balance
```

Three things are worth knowing.

**The Events line is what arrived that day, and the lines below it are the consequences.** An event
whose value date differs from its booking day says so, as E7 does above. That note is the one to
watch, because it is why a balance can move on a day when nothing appears to happen.

**Balances are value-dated, not booking-dated.** A day's closing balance is the sum of every entry
whose value date falls on or before that day, recomputed after the whole stream has replayed. E7 is
booked on Day 5 but value-dated Day 2, so it changes what Day 2 closed at, and Day 2 carries a fee
for a debit that arrived three days later.

**Within one day block, the lines are dated differently on purpose.** The balance and fee lines are
value-dated. The events, authorization and error lines are booking-dated. This is intentional
rather than a bug.

## Build requirements

Kotlin 2.0.21 on a JDK 21 toolchain, built with Gradle 8.14.3. The wrapper is checked in, so
`./gradlew` is the only entry point you need.

## Layout

Gradle requires `src/main/kotlin` and `src/test/kotlin`, which is where the architecture's
`domain / replay / report` packages and the tests live:

```
src/main/kotlin/ledger/domain/   Money, Ledger, entries, accounts, holds
src/main/kotlin/ledger/replay/   the event stream, engine, fee and interest policies
src/main/kotlin/ledger/report/   the console report
src/test/kotlin/ledger/          unit, invariant, acceptance and known-failure tests
```

There is no `resources` directory. The supplied stream is declared as Kotlin literals in
`EventStream` rather than loaded from JSON, so a malformed event fails to compile instead of
failing at run time.

## The other documents

| File             | What it holds                                                                  |
|------------------|--------------------------------------------------------------------------------|
| `NUMBERS.md`     | Every constant, and why that value rather than half it                         |
| `AMBIGUITIES.md` | Every ambiguity found in the brief and how it was resolved                     |
| `REJECTED.md`    | Acceptance criteria refused, with reasons, plus approaches abandoned mid-build |
| `WORKLOG.md`     | Timestamped record of the work as it happened                                  |
