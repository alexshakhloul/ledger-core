# Rejected

Two kinds of things are recorded here: acceptance criteria that are wrong and are refused rather
than implemented, and approaches that were tried during the build and then abandoned.

## Acceptance criteria refused

Three of the eight criteria in specs are incorrect. Each is refused below with the reason that refutes it.

### Criterion 6: after E9, all balances and fees return to their pre-E7 values

Refused.

E9 reverses E7 and nothing else. The AED 620.00 does come back, posted as a compensating credit
value-dated Day 2. The AED 25.00 overdraft fee does not.

```
pre-E7   Day 2 closing = AED 250.00
post-E9  Day 2 closing = AED 225.00
```

The fee was correctly raised at the moment Day 2 stood at AED -370.00. Nothing in specs
reverse a fee, and removing it would break the append-only rule specs itself call
non-negotiable. A reversal/compensation returns the amount, never the consequences.

This criterion is also the required failing test. `KnownFailureTest` asserts AED 250.00, fails, and
carries the reasoning inline. Run it with `./gradlew knownFailureTest`.

The limitation it exposes is real and worth stating : this engine cannot express "a fee was
raised on a fact later found to be wrong". A production ledger would post a compensating
fee-reversal event. Specs define no such event, so inventing one would be adding scope that
was not asked for, and the asymmetry is left visible instead.

### Criterion 7: the three BHD instalments must each be BHD 3.334

Refused.

```
3.334 + 3.334 + 3.334 = 10.002
```

The credit is BHD 10.000. Three instalments of 3.334 create BHD 0.002 that was never credited, on
an account that would then report 10.002 on Day 5 and earn interest on money it never received.

At three decimal places the deterministic split is 3.333, 3.333, 3.334. Those total exactly 10.000.

### Criterion 8: if the rounded daily accruals do not sum to the capitalized total, discard the remainder

Refused.

It contradicts the spec's own non-negotiable rule that the rounded daily accruals must sum exactly
to the capitalized total. A rule cannot require exactness and then permit the difference to be
thrown away.

It is also unnecessary here. The capitalized total is defined as the sum of the rounded dailies, so
the remainder is structurally zero and there is nothing to discard. The alternative definition,
rounding the raw total, does produce a discrepancy: ACC-001's raw accruals sum to 0.968000, which
rounds to AED 0.97 against the dailies' AED 0.98. Discarding a cent there would mean the ledger
credits less interest than it reports having earned.

### A note on criterion 5

Not refused, but worth flagging. Criterion 5 says that if Auth-B is approved, its hold reduces
available balance but not ledger balance. The rule is correct and the implementation follows it.

The premise is false. Auth-B is rejected: available balance at E8 is AED -180.00, and a AED 90.00
hold would take it to AED -270.00. So no hold is ever created and the criterion asserts nothing
about this stream. `AcceptanceCriteriaTest` tests that.

## Approaches abandoned mid-build

### Re-scanning the whole window for overdraft fees

Tried first, because it is the most literal reading of "once per day per account when that day's
closing balance is negative".

It charges four fees. When E7 posts, Days 2, 4, 5 and 6 are all overdrawn at once, and each fee
deepens the days after it, so the charges cascade. The result is AED 100.00 in fees and a Day 6
balance of AED 365.92 instead of AED 440.98.

Replaced by assessing only the value date of the entry just posted. AMBIGUITIES.md section 1 has
the full comparison.

### Sweeping for fees once at the end of the replay

Also tried. It charges nothing at all, because by the end E9 has left every day positive.

Abandoned for a second reason beyond the wrong answer: the rule is not self-consistent. With the
Day 2 fee present, Day 2 closes at AED 225.00, so a rule that re-derives fees from final balances
would want to delete the fee it had just created. There is no stable state.

### Capitalizing the rounded sum of the raw accruals

Tried while implementing interest, since it reads as the more natural "add it all up, then round".

It gives AED 0.97 rather than AED 0.98, and then needs remainder-allocation machinery to reconcile
the dailies against the total. Defining the total as the sum of the rounded dailies removes the
discrepancy instead of managing it, and no remainder can arise.

### Making LedgerEntry a data class

The architecture sketch originally showed `data class LedgerEntry`, and it was written that way
first.

A data class synthesises a public `copy()`. That means `entry.copy(amount = ...)` produces a second
entry carrying the original's `entryId` and an amount nobody validated. `LedgerEntry` cannot check
that itself, because it holds an `AccountId` rather than an `Account` and does not know the
account's currency. The check lives only in `Ledger.append`, and `copy()` walks straight past it.

Replaced with a plain class, a private constructor and hand-written `equals`. In an append-only
ledger, "a posted entry cannot be altered" should be something the compiler enforces rather than
something contributors remember.

### Reporting each authorization's final status

The first reporter showed each hold's end state against its booking day. Auth-A is approved on
Day 2 and settled on Day 4, so the report printed `Auth-A SETTLED` under Day 2 and claimed the
account had settled two days before it did.

Replaced with a log of transitions, so each change appears on the day it happened. This was caught
by running the report and comparing it against the expected output, not by reading the code.
