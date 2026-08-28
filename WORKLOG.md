# Worklog

Timestamped

---

## 2026-08-27T19:09:13+0400

- Created the initial skeleton of the project, with dependencies
- Verified from clean: `./gradlew clean test run` compiles, passes both smoke tests, and runs.
- Initiated ARCHITECTURE.md and README.md.
- Committed `chore: initialize ledger core project`.

## 2026-08-27T21:39:32+0400

- Added `Currency` (AED at 2 dp, BHD at 3 dp) and `Money`.
- `Money` holds a `Long` count of minor units and a currency. Two invariants hold by
  construction rather than by convention: the constructor is private so the only decimal entry
  point is `of`, which rounds HALF_UP to the currency scale, and every binary operation rejects
  a differing currency instead of converting.
- Kept rounding out of arithmetic. `plus` and `minus` are exact integer operations using
  `Math.addExact`, so an overflow fails loudly rather than wrapping. Rounding happens only where
  a decimal enters the domain. That separation is what lets interest accrue in BigDecimal and
  convert exactly once.
- Tests are green, covering all uses cases for precision

## 2026-08-28T11:27:36+0400

- Added the append-only ledger: `EntryType`, `Day`, `AccountId`/`EventId`/`EntryId`, `Account`,
  `LedgerEntry`, `Ledger`.

## 2026-08-28T12:58:42+0400

- Built the rest of the engine in one pass: authorizations and holds, settlement validation,
  reversal, the overdraft fee policy, interest, the instalment split, the replay engine, and the
  console report.
- `Ledger` stays a plain record of history. Posting and fee assessment are paired in the replay
  engine instead, so no policy is baked into the data structure.
- The fee rule is the one real decision. Assess the value date of the entry just posted, and no
  other day.
- The idempotency key is claimed before the fee is posted. `a day already charged is never
  charged again`, the fee is value-dated to the day it charges, so re-entry would
  otherwise charge forever.
- Authorization decisions read the balance as of the booking day; reporting, fees and interest
  read value-dated closing balances.
- 87 tests green, and `./gradlew knownFailureTest` fails as designed.
