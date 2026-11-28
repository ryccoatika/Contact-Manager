# SIM card reconcile — show card entries the phone's SIM copy is missing

Date: 2026-11-27 · Branch: `fix/sim-mirror-reconcile`

## Problem

Report (Umidigi A15C, Android 13, one SIM): a contact written to the SIM by
another app did not appear in Contact Manager until the phone was restarted.

Some OEM contact providers expose SIM contacts as a normal `ContactsContract`
account (a *mirror* of the card) and rebuild that mirror only when the SIM is
loaded (boot). When such a native SIM account exists,
`IccSimAccountsIntegration` deliberately skips the direct `icc/adn` read so the
same SIM is not listed twice. The app therefore shows the mirror; a write that
goes straight to the card (third-party SIM tools) stays invisible until reboot.

Evidence: single SIM (rules out the unread second SIM), the entry appears after
a reboot (the mirror was rebuilt), and the phone-permission prompt — shown only
with an `icc/` pseudo-account — was absent. The native account type on that
device is unknown; the design below does not depend on it.

## Design

Keep the mirror as the primary source. Additionally read the card and surface
only the entries the mirror does not hold ("card-only" entries).

### Pairing a native SIM account with a card

A native SIM account is a `(accountType, accountName)` of raw contacts whose
type classifies as `AccountCapability.SIM` and is **not** an `icc/` type.

Reconciled accounts must satisfy `SimRouting.reconcilesWithCard(type)`: false
for Samsung (`vnd.sec.contact.sim…`), whose behaviour stays exactly as today
(largest population, works, not verifiable here); true otherwise.

For each reconcilable native account pick one subscription, or skip:

1. `SimRouting.nativeSimSlot(type)` is non-null and an active subscription has
   that `slotIndex` → that subscription.
2. Otherwise, only when the device unambiguously has one SIM: exactly one
   native SIM account exists (counting every native SIM account, Samsung
   included), `presentSimCount() == 1`, and `activeSubscriptions().size == 1`
   → that single subscription (this is also the no-permission legacy path,
   `content://icc/adn`).
3. Anything else → skip (today's behaviour).

A subscription that is already surfaced as an `icc/` pseudo-account (the
existing "uncovered" set) is never reconciled. If two native accounts pair
with the same subscription, neither is reconciled: a card-only entry cannot be
attributed to one of them, and listing it under both would double it.

### Reading

`simSource.readAll(subscriptionId)` only. No capability lookup: the probe
performs a test write, and reconciling must never write to a SIM. `readAll`
already degrades to an empty list on any provider failure.

### Matching (pure, `domain/sim/SimCardReconciler`)

Lenient on purpose: a false "already present" merely leaves today's behaviour;
a false "missing" would double-list a contact.

- Number key = digits, `*` and `#` of the number (formatting and a leading `+`
  dropped).
- A card entry with a non-empty key is **present** if a mirror phone has the
  same key, or both keys have ≥ 9 digits and share their last nine.
- A card entry with an empty key is present if its trimmed, lower-cased name
  equals a mirror name (contact display name, or the raw contact's
  given + family name).
- Everything else is card-only.

Mirror numbers and names come from the raw contacts of that native account
only.

### Surfacing

Card-only entries become synthetic contacts exactly like `icc/` entries
(negative synthetic id, registered in the resolve snapshot with their
subscription id), but attributed to the **native** account's type and name so
they land in the existing SIM chip and account filter. Edits, deletes, copies
and moves of them already route to the card because routing is by negative id.

### Account count

`SimAccountsIntegration` exposes `cardOnlyCount(accountType, accountName)`
(from the last `simContacts()` read) and `cardOnlyChanges: Flow<Unit>` (emits
when any count changes). `AccountRepository` adds the count to the native
account's `contactCount` and merges `cardOnlyChanges` into its reload trigger,
so the chip count matches the list.

### `SimSubscriptionsSource.presentSimCount()`

Number of slots holding a usable SIM/eSIM, from `TelephonyManager.getSimState`
(no permission). Any failure → 0 ("unknown"), which disables rule 2.

## Not covered

- Entries removed from the card by another app stay visible (from the mirror)
  until reboot.
- Samsung native SIM accounts.
- Multi-SIM devices whose native SIM types cannot be mapped to a slot.

## Risks

- Cannot be verified on the reporting device from here; unit tests cover the
  pairing and matching rules with fakes.
- On affected devices every contact load now also reads `icc/adn` (as devices
  without a native SIM account already do).
