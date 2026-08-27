# ledger-core

An in-memory account ledger core. It replays an event stream over Day 1 to Day 6 and
prints a daily report of closing ledger balance, fee assessments, authorization states and errors.

No web layer, no persistence, no UI, no database. Kotlin on the JVM, Gradle, and nothing at runtime (as per specs).

The design and the reasoning behind every contested rule are in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Running it

```bash
./gradlew run     # replays the stream and prints the daily report
./gradlew test    # the full suite, must be GREEN
```

## The other documents

| File | What it holds |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | The design, expected numbers, and verdicts on all eight acceptance criteria |
| `NUMBERS.md` | Every constant, and why that value rather than half it |
| `AMBIGUITIES.md` | Every ambiguity found in the brief and how it was resolved |
| `REJECTED.md` | Acceptance criteria refused, with reasons, plus approaches abandoned mid-build |
| `WORKLOG.md` | Timestamped record of the work as it happened |
