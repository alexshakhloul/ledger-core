# Ambiguities

## 1. When is the overdraft fee assessed, and over which days?

Spec say the fee is charged "once per day per account when that day's closing ledger balance
is negative". It defines which day is eligible. It never says when eligibility is evaluated, nor
whether one event can trigger a charge on more than one day.

That gap decides the answer, because E7 is backdated. At the instant E7 posts, before E9 reverses
it, four days are overdrawn at once:

| Day     |      1 |       2 |     3 |       4 |       5 |       6 |
|---------|-------:|--------:|------:|--------:|--------:|--------:|
| Closing | 250.00 | -370.00 | 30.00 | -155.00 | -155.00 | -155.00 |

Four readings are possible, but they do not agree:

| Reading                                        | Fees charged                             | Day 6 closing      |
|------------------------------------------------|------------------------------------------|--------------------|
| Re-scan all six days after every event         | Days 2, 4, 5, 6, so AED 100.00           | AED 365.92         |
| Re-scan days 1 to today at each day's close    | Days 2, 4, 5, so AED 75.00               | AED 390.94         |
| Sweep once at the end of the replay            | none, since E9 leaves every day positive | AED 440.98, no fee |
| Assess the value date of the entry just posted | Day 2 only, AED 25.00                    | AED 440.98         |

Only the last produces the single Day 2 fee specs's own acceptance criteria describe.

**Decided:** after posting a credit or debit value-dated to day D, evaluate day D and no other day.
Charge if that day is overdrawn and has not been charged before.

**Why:** a day is charged when activity value-dated to it leaves it overdrawn. Days 4 to 6 saw no
such activity. Their overdraft was a side effect of a backdated entry that E9 later reversed, and
none of those days ever closed negative on its own terms.

The end of replay sweep deserves a note, because it is the reading most reached at first. It
does not merely give a different answer, it is not even self-consistent: with the fee present Day 2
closes at AED 225.00, which is positive, so a rule that re-derived fees from final balances would
want to remove the fee it had just created.

## 2. The stream is not in booking order

Specs list E9 as booked on Day 6 but places it before E10, which is booked on Day 5.

**Decided:** replay in the listed order, because Specs say "replayed in this order".

**Why:** it makes no difference here, since E9 and E10 touch different accounts. The order that
does matter is E8 before E9, and that one is undeclared. If E9 ran first, the
available balance at E8 would be AED 440.00 rather than AED -180.00, and Auth-B would be approved
instead of rejected. Sorting the stream by booking day would silently produce that outcome.

## 3. Which balance gates an authorization?

Specs say an authorization is approved if available balance, meaning ledger balance minus
active holds, stays at or above zero. It does not say whether "ledger balance" means the running
total of everything posted so far or the value-dated balance as of the booking day.

**Decided:** the value-dated closing balance as of the booking day.

**Why:** the two are identical for this stream, because nothing is forward dated. They would
diverge the moment an event carried a value date later than its booking day, and the value-dated
reading is the one consistent with how the fee and the report treat balances. Recorded rather than
left implicit, since the stream itself cannot distinguish them.

## 4. Settlement below the authorized amount

Auth-A holds AED 200.00 and settles for AED 185.00. Specs don't say what happens to the
remaining AED 15.00.

**Decided:** accept the settlement, post a debit for AED 185.00, and release the whole hold.

**Why:** the hold reserved spending capacity, and the event says the authorization settles. There
is no partial capture in Specs, and inventing one would mean holding AED 15.00 indefinitely
against an authorization that's finished.

## 5. What is the interest base on Day 6?

Interest accrues on each day's closing balance, and the total is capitalized on Day 6. Read
literally, Day 6's accrual depends on Day 6's closing balance, which depends on the capitalization,
which depends on Day 6's accrual.

**Decided:** the base excludes `INTEREST_CAPITALIZATION` entries, putting Day 6's base at
AED 440.00 rather than AED 440.98.

**Why:** it breaks the circularity and makes the calculation a single pass. The alternative reading
happens to converge here, since 440.98 at 0.04% still rounds to 0.18, but a definition that is
circular and only accidentally stable is not a definition.

## 6. Rounding mode

Never stated. See NUMBERS.md for the arithmetic: HALF_UP, and truncation would give AED 0.95
instead of AED 0.98.

## 7. Three equal instalments that cannot be equal

BHD 10.000 in three equal parts is 3.333... at a currency with three decimals.

**Decided:** 3.333, 3.333, 3.334, with the remainder on the final instalment.

**Why:** it totals exactly 10.000. Any allocation of the remainder would, but putting it last keeps
the rule trivially describable.

## 8. Do authorizations have value dates?

E3 and E8 carry value dates in Specs, but an authorization posts nothing to the ledger.

**Decided:** the field is not modelled on `AuthorizationEvent`.

**Why:** carrying a value date that nothing reads would suggest it affects something. Holds move
available balance, never ledger balance, so there is no entry for a value date to apply to.

## 9. Is a rejected authorization an error?

E8 is refused for insufficient funds. It appears on the report's authorization line as
`Auth-B REJECTED` and on the error line as `E8 insufficient available balance`.

**Decided:** both.

**Why:** they answer different questions. First, the authorization line tracks what happened to each hold.
The error line lists events that were refused and changed nothing. E8 is genuinely both, and
showing it once would leave one of the two views incomplete.

## 10. Which status does the report show for a hold that changed twice?

Auth-A is approved on Day 2 and settled on Day 4.

**Decided:** the report shows each transition on the day it happened, so Auth-A appears twice.

**Why:** this one was found by running the report rather than by reasoning about it. The first
version rendered each authorization's final status against its booking day, which printed
`Auth-A SETTLED` on Day 2 and claimed the account had settled two days before it did.

## 11. Value-dated and booking-dated lines in the same block

Within one day of the report, the balance and fee lines are value-dated while the authorization and
error lines are booking-dated.

**Decided:** keep the mixture, and say so in the reporter.

**Why:** the Day 2 fee was assessed on booking Day 5, because E7 arrived backdated. Filing it under
Day 5 would misstate when the account was overdrawn. Filing the E6 refusal under a value date would
be worse, since a refused event has no value date at all. 

## 12. The report is produced after the replay, not during it

Day 3 reads AED 625.00. During the run it stood at AED 650.00, because the Day 2 fee did not exist
until E7 was booked two days later.

**Decided:** generate the report once, at the end, from final balances.

**Why:** Specs ask for closing balances, and a day's closing balance is whatever history
finally says it is. 

## 13. What happens if a non-AED account goes overdrawn?

The fee is stated only in AED. ACC-002 is a BHD account.

**Decided:** raise `FeeNotDefinedForCurrency`.

**Why:** ACC-002 never goes overdrawn, so this is unreachable in the supplied stream.

## 14. Are zero-amount entries legal?

Specs says an amount is "always a positive magnitude", which leaves AED 0.00 unaddressed.

**Decided:** allow zero, reject negative.

**Why:** the sign belongs to the entry type, never to the amount, and `Money.isZero` is already a
valid state. Nothing in the stream posts a zero entry, so this is a choice about where the guard sits.
