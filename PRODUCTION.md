# Production notes

These notes describe the code in this repository, not a hypothetical system.

## Append-only at scale

### What breaks first

Memory. Everything the replay has seen stays resident, and nothing is ever released.

The balance query is not the wall. `Ledger` keeps signed totals per value date and entry type,
updated on append, and it keys entries by id, so `closingBalance` costs the same whether an account
holds ten entries or ten million. Without that index the query would filter the whole entry list on
every call, and the fee policy calls it once for every entry posted, so a replay would be quadratic
in the number of entries.

`Ledger.entriesOfType` does still scan, and the reporter calls it once per account per day. That
cost is linear and the report runs once, so it is not the next thing to fix, though it is the next
thing a profile would show.

### Where state accumulates without bound

| State                         | Grows with                     | Bounded by                               |
|-------------------------------|--------------------------------|------------------------------------------|
| `Ledger.postedEntries`        | every entry, forever           | nothing                                  |
| `Ledger.entriesById`          | every entry again, as a lookup | nothing                                  |
| `Ledger.dailyTotals`          | one row per day per account    | nothing, once the window is not six days |
| `ReplayEngine.entryByEvent`   | every posted event, forever    | nothing                                  |
| `ReplayEngine.authorizations` | every hold ever requested      | nothing                                  |
| `ReplayEngine.activity`       | every hold state change        | nothing                                  |
| `ReplayEngine.errors`         | every refused event            | nothing                                  |
| `OverdraftFeePolicy.assessed` | accounts times days            | the window, which itself grows           |

The entry list growing is correct, since that is what append-only means. The other seven are
bookkeeping that exists only to serve the current run and has no reason to outlive it. Four of them
sit on the engine and are never read again once the report is produced.

### The cheapest change that defers it

Close the books. Materialise a balance per account at the end of each accounting period, and let
queries read that checkpoint plus only the entries value-dated after it.

Nothing is deleted and no answer changes. What changes is that entries before the last checkpoint
stop being needed in memory, so they can move to cold storage, and the daily index only has to
cover the open period. It also bounds the engine's bookkeeping, since a closed period's
authorizations, activity and refusals can be archived alongside it.

This is the cheapest option because it defers every row in the table above at once, and it is the
only one that does. The balance index costs less to build, but it covers the query alone.

The catch is that checkpointing is not purely a code change. It needs a defined period-close, and a
rule for what happens when a back-valued entry lands before the last checkpoint, which is the
control in the next section. Build that control first.

## Value-dated entries in production

A back-valued entry changes a number that has already been reported. That is the whole of the
operational surface, and most of the regulatory one.

Inside the bank it means statements to reissue and customers to tell, interest accrued on a balance
that no longer holds, and fees raised on a position that has since changed. This implementation
recomputes interest from final balances at the end of the run, so it restates periods that have
already closed. It also leaves standing a fee whose cause was later reversed, which a customer
would reasonably dispute.

Outside the bank, a UAE-licensed institution reports prudential and liquidity positions to the
Central Bank as at a date. An entry that moves a historical position after the return has been
filed turns a bookkeeping convenience into a resubmission. IFRS reporting behaves the same way once
an accounting period is closed. Transaction monitoring is affected as well, because thresholds and
velocity rules evaluate a window that a backdated entry retrospectively changes, and a rule that
never re-evaluates will not notice the window moved.

There is also an audit surface this design cannot serve at all. Nothing records who posted an entry
or why, so "who back-dated this, and on whose authority" has no answer.

### The control to add before go-live

A hard back-value cutoff at the last closed accounting period.

Anything value-dated on or after the cutoff posts normally. Anything earlier is refused at the API
and has to be raised instead as an explicit adjustment carrying a reason code and a second
approver, which is itself an auditable event.

The point is not to prevent back-valuing, which is legitimate and necessary. It is to make crossing
a closed period a decision somebody made and signed, instead of a side effect of a value date in a
payload. It is also the precondition for checkpointing, which cannot be safe while anything may be
posted arbitrarily far into the past.

## Authorization lifecycle

This model gives an authorization three states, so it can only end by being rejected or settled.
Every row below except the first is either missing or, in two cases, a defect in the code as it
stands.

| Ending                             | Real-world scenario                                                               | Mandated behaviour                                                                                                                                                                                                    |
|------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Rejected at request                | Card declined at the terminal because available balance will not carry the hold   | No hold, no ledger entry, decline returned with a reason. Implemented                                                                                                                                                 |
| Approved, then expires uncaptured  | Hotel or fuel pre-authorization the merchant never completes                      | Auto-release at a scheme-defined expiry. Not implemented, so an approved hold pins funds forever. The most customer-visible gap here                                                                                  |
| Captured for less than authorized  | Partial shipment, or a pump releasing the final amount                            | Decide explicitly between releasing the remainder and holding it until expiry. This model releases the whole hold, which is a defensible choice but still a choice                                                    |
| Captured for more than authorized  | Restaurant gratuity added after the pre-authorization                             | Permit within a scheme tolerance band, refuse beyond it, and route the refusal to an exception queue instead of dropping it. Currently refused at any excess                                                          |
| Voided before capture              | Order cancelled, or the customer walks away                                       | Release the hold, post nothing, keep the record. There is no void event in this model                                                                                                                                 |
| Account frozen while a hold stands | Sanctions hit or court order lands mid-authorization                              | The hold must survive the freeze and escalate, never release unnoticed                                                                                                                                                |
| Superseded by a duplicate request  | Retry after a timed-out response, or at-least-once delivery from a payments queue | Reject the duplicate and return the original outcome. The engine currently assigns into the authorization map without checking whether the id already exists, so the first hold is overwritten and lost with no error |
| Orphaned by a failed process       | The engine restarts mid-replay                                                    | Recover the hold from durable state. Nothing is persisted, so every hold is lost on restart                                                                                                                           |

The last two rows generalise past authorizations. Nothing in this engine is idempotent at the event
level, so a redelivered event posts its entries a second time, and nothing survives a restart. In a
fixed ten-event stream neither can happen. Behind any real transport both will, and both would move
money.

## What was cut, and what that defers

| Cut                                                   | Production risk deferred                                                                                                  |
|-------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Persistence. Everything is in memory                  | No durability and no recovery. A restart loses the ledger, which is the one thing a ledger may not do                     |
| Concurrency. Nothing locks and nothing is thread-safe | Two concurrent posts to one account interleave a read-modify-write and lose a fee assessment or an authorization decision |
| Event-level idempotency                               | Duplicate delivery double-posts. The most likely production incident on this list                                         |
| Authorization expiry                                  | Approved holds never release, pinning a customer's funds indefinitely                                                     |
| Partial and over-capture handling                     | Two common card flows are refused or resolved by assumption, both surfacing as exceptions somebody clears by hand         |
| Fee reversal                                          | A fee raised on a fact later reversed cannot be undone, so it becomes a dispute queue instead of a ledger entry           |
| Period close and archival                             | All history stays resident. See the first section                                                                         |
| Multi-currency and FX                                 | No rate source, no revaluation, no position. The fix touches `Money`, which everything depends on                         |
| An AED-only overdraft fee                             | A non-AED account going overdrawn raises an error instead of charging. Correct as a guard, unusable as a product          |
| Attribution and audit trail                           | No record of who posted what, so the back-value control above cannot actually be enforced                                 |
| The event stream is code, not data                    | Changing the stream means changing and redeploying code. Fine for a fixed exercise, wrong for anything operated           |
| Observability. No metrics and no structured logs      | A skipped fee or a lost hold would leave no trace to find it by                                                           |

Two are worth closing before anything else. Event idempotency, because duplicate delivery will
happen sooner or later and the failure moves money. Authorization expiry, because a permanently
pinned hold is the failure a customer notices and calls about.
