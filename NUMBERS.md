# Numbers

Every constant this implementation depends on, and what breaks if you change it.

Why each value and not half it, so each entry says what halving actually costs
rather than just asserting the value is required.

## Currency precision

| Constant                       | Value                     | Where          |
|--------------------------------|---------------------------|----------------|
| AED minor units per major unit | 100, so 2 decimal places  | `Currency.AED` |
| BHD minor units per major unit | 1000, so 3 decimal places | `Currency.BHD` |

AED at 1 decimal place cannot express the AED 25.00 fee's cents, and every amount in the stream
carries two decimals. BHD at 2 decimals is worse than imprecise: the installment split depends on
10.000 dividing into thousandths, and at 2 decimals 10.00 / 3 leaves a remainder that has nowhere
sensible to go, so the three instalments stop summing to the total.

Precision belongs to the currency, not to the formatting. `Money` stores a `Long` count of minor
units, so AED 25.00 is 2500 and BHD 10.000 is 10000. Binary floating point is never used for a
balance, because it cannot represent 0.1 exactly and a ledger that loses a fraction of a fils is
simply wrong. `DummyTest` pins that distinction.

## Overdraft fee

| Constant                    | Value     | Where                         |
|-----------------------------|-----------|-------------------------------|
| Fee amount                  | AED 25.00 | `OverdraftFeePolicy.fee`      |
| Charges per account per day | at most 1 | `OverdraftFeePolicy.assessed` |

The amount is a business rule, not a tuning parameter. Halving it to AED 12.50 moves the Day 2
closing balance from AED 225.00 to AED 237.50 and the final Day 6 balance from AED 440.98 to
AED 453.49, because the fee also reduces the balance every later day accrues interest on.

The "at most one" is doing more work than it looks. The fee is value-dated to the day it charges,
so posting it leaves that day at AED -395.00, still overdrawn. Without the idempotency key the
policy would see a negative day, charge again, and never stop.

## Daily interest rate

| Constant | Value                         | Where                      |
|----------|-------------------------------|----------------------------|
| Rate     | 0.0004, meaning 0.04% per day | `InterestPolicy.dailyRate` |

Halving it to 0.02% halves every accrual. ACC-001 would capitalize at AED 0.50 instead of AED 0.98.
ACC-002 is the more interesting case: BHD 10.000 at 0.04% is 0.004 a day, and at 0.02% it is 0.002,
which is still representable at three decimals but half a step from rounding to zero. Small
balances sit close enough to the boundary that the rate is not a free dial to turn.

## Rounding

| Constant              | Value                                              | Where                      |
|-----------------------|----------------------------------------------------|----------------------------|
| Rounding mode         | `RoundingMode.HALF_UP`                             | `Money.of`                 |
| Raw accrual precision | the product's exact scale, 6 for AED and 7 for BHD | `InterestPolicy.accrualOn` |

Specs never name a rounding mode, but it does constrain one. AED 440.00 at 0.04% is exactly
0.1760, and Days 4, 5 and 6 all turn on how that is rounded. All rounding modes agree
on 0.18 here, because the digit after the cent is 6 rather than a tie. 
Truncation does not: it gives 0.17 three times, a capitalized total of AED 0.95 instead of AED 0.98, and a Day 6 balance of
AED 440.95. So the mode is not free, and HALF_UP is the ordinary financial choice.

The raw accrual is deliberately not truncated before rounding. `base.toBigDecimal()` carries the
currency's scale and the rate carries 4, so their product is exact at scale 6 for AED and 7 for
BHD, and `Money.of` rounds that exact product once.

--> Rounding twice, or capping the intermediate precision first, would introduce error no later step could recover.

## Interest total

| Constant           | Value                                 | Where                       |
|--------------------|---------------------------------------|-----------------------------|
| Capitalized total  | the sum of the rounded daily accruals | `InterestPolicy.capitalize` |
| Capitalization day | Day 6                                 | `InterestPolicy.capitalize` |

The total is defined as the sum of the rounded dailies, so no remainder can exist to be discarded.
Rounding the raw total instead is not equivalent: ACC-001's raw accruals sum to 0.968000, which
rounds to AED 0.97, while the rounded dailies sum to AED 0.98. Specs require the dailies to
sum exactly to the total, and only one of those two definitions satisfies that by construction.

The accrual base excludes `INTEREST_CAPITALIZATION` entries. Day 6's accrual is taken from Day 6's
closing balance, so if that balance already contained the capitalization the accrual would be
defined in terms of itself. Excluding the type keeps the base at AED 440.00 rather than AED 440.98.

## Instalments

| Constant                 | Value                | Where                      |
|--------------------------|----------------------|----------------------------|
| Instalment count for E10 | 3                    | `EventStream`              |
| Remainder allocation     | the final instalment | `InstalmentSplitter.split` |

BHD 10.000 in three parts gives 3.333, 3.333, 3.334. Sending the remainder to the last instalment
is arbitrary in that first or middle would total correctly too, but it is deterministic and keeps
the arithmetic obvious: floor the division, put what is left on the end. Three instalments of 3.334
total 10.002, would be wrong.

## Replay window

| Constant  | Value | Where       |
|-----------|-------|-------------|
| First day | 1     | `Day.FIRST` |
| Last day  | 6     | `Day.LAST`  |

The window is fixed by the brief, so `Day` validates it at construction. `Day(7)` cannot be built,
which means `closingBalance(Day(7))` cannot be written either. A wider window would not fail
loudly, it would quietly report days the run never covered.

## Identity

| Constant          | Value                         | Where                |
|-------------------|-------------------------------|----------------------|
| First entry id    | 1, per account                | `Ledger.nextEntryId` |
| Entry id sequence | monotonic, assigned at append | `Ledger.append`      |

Numbering restarts for each account so ACC-001's entry numbers do not shift when ACC-002 posts
something. A global counter would be arithmetically fine but would couple the two accounts'
output together, and the report has to be identical between runs, which might be error-prone.

## Accounts

| Constant | Value                      | Where            |
|----------|----------------------------|------------------|
| ACC-001  | AED, opening balance 0.00  | `Account.acc001` |
| ACC-002  | BHD, opening balance 0.000 | `Account.acc002` |

Both open at zero, as stated. The opening balance is part of `closingBalance`, so it is
not decoration: an account opening at anything else would shift every day in the report.
