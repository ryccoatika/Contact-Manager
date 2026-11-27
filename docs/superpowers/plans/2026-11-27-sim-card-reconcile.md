# Plan — SIM card reconcile

Spec: `docs/superpowers/specs/2026-11-27-sim-card-reconcile-design.md`.
Tests first for every task; all gates green at the end.

## 1. Pure matching — `domain/sim/SimCardReconciler.kt`

`object SimCardReconciler { fun cardOnly(card: List<SimContact>, mirrorNumbers: Collection<String>, mirrorNames: Collection<String>): List<SimContact> }`
— rules in the spec ("Matching"). Order of `card` preserved.

`SimCardReconcilerTest`: identical number; formatted vs plain; `+33612345678`
vs `0033612345678`/`612345678` (last nine); short numbers (`3179`) must match
exactly; number-less entry matched by name (case/space-insensitive); number-less
entry with unknown name is card-only; empty mirror → everything card-only;
empty card → empty; two card entries sharing a number absent from the mirror →
both returned.

## 2. `SimRouting.reconcilesWithCard(accountType: String?)`

False for null and for types starting with `vnd.sec.contact.sim`
(case-insensitive); true otherwise. Extend `SimRoutingTest` (or add one).

## 3. `SimSubscriptionsSource.presentSimCount()`

Add to the interface; implement in `DefaultSimSubscriptionsSource` by counting
slots (reuse the slot loop of `anySimPresent`; pre-O: 1 if `tm.simState` is
present else 0); any exception → 0. Update `FakeSimSubscriptionsSource`
(constructor parameter, default = `subscriptions.size`) and any other
implementer.

## 4. `IccSimAccountsIntegration`

- Interface: `fun cardOnlyCount(accountType: String?, accountName: String?): Int`
  and `val cardOnlyChanges: Flow<Unit>`.
- `simContacts()`: after computing the uncovered subscriptions, apply the
  spec's pairing rules, read each paired card with `simSource.readAll`, run
  `SimCardReconciler.cardOnly`, and append the results to the same sorted,
  index-id pipeline as today's entries — attributed to the native account's
  type/name. The early `return emptyList()` when nothing is uncovered must no
  longer skip reconciling. Store the per-account counts; `tryEmit` on
  `cardOnlyChanges` (a `MutableSharedFlow<Unit>(extraBufferCapacity = 1)`) only
  when the map changed.
- No call to `simRepository.capabilities` / `probe` on the reconcile path.
- Update the `SimAccountsIntegration` fake in `SimAwareContactsWriterTest`.

`SimAccountsIntegrationTest` additions:
- unmappable native type (`"USIM Account"`), one legacy subscription, one SIM
  present, card = mirror entries + one new → exactly one contact, native
  type/name, negative id, `resolveSimContact` returns it with the right
  subscription id; `cardOnlyCount == 1`; `simPseudoAccounts` still empty;
  `source.probes == 0`;
- same but `presentSimCount = 2` → no contacts;
- two unmappable native SIM accounts → no contacts;
- mappable type (`"com.android.contacts.sim"` → slot 0) with a real
  subscription on slot 0 → reconciles against that subscription id;
- Samsung type → unchanged (existing tests stay green, add one asserting no
  card read result is surfaced);
- partial native coverage (existing scenario): the uncovered SIM is still an
  `icc/` pseudo-account and is not reconciled into the native account;
- `cardOnlyChanges` emits when the count goes 0 → 1 and not on an identical
  re-read.

## 5. `AccountRepository`

Add `simIntegration.cardOnlyCount(type, name)` to each provider account's
`contactCount`; trigger = `merge(contentChanges().debounce(300), simIntegration.cardOnlyChanges)`.
Keep the `accounts_load` trace.

## 6. Docs + changelog

- `README.md` device caveats: one bullet on native SIM mirrors and the
  reconcile (and what is not covered).
- `AGENTS.md` Gotchas: one bullet.
- `CHANGELOG.md` via the `update-changelog` skill (user-facing fix).

## Gates

`compileDebugKotlin`, `spotlessApply`, `spotlessCheck`,
`:app:testDebugUnitTest`, `:app:lintDebug`.
