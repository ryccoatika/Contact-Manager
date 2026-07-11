# ContactManager — Design Spec

Date: 2026-07-12
Status: Approved by user

## Goal

Android app that shows contacts from **all** signed-in accounts on the device (Google, Samsung, device/phone, SIM, WhatsApp, etc.), lets the user CRUD contacts per account, move contacts between accounts, and manage duplicates. Primary user motivation: separate work-related contacts into a specific account/SIM and clean up duplicates.

Non-goals: cloud backup, own sync service, contact sharing, call/SMS features.

## Decisions (user-confirmed)

1. **Third-party derived accounts (WhatsApp, Telegram, …): read-only.** Shown with full data, usable in duplicate detection and linking, but create/edit/move-into disabled with an explanatory UI hint.
2. **Duplicates: both Link and Merge, user picks per group.** Link = ContactsContract aggregation (reversible). Merge = physical merge into a chosen target account (destructive, confirmed).
3. **SIM: full CRUD including write-to-SIM.** Per-vendor code paths + capability probing + SIM field-limit UI.
4. **Main UI: unified list + account filter chips + per-contact account badges.**
5. **Architecture: MVVM + Repository directly over ContactsContract. No Room cache.**

## Architecture

Single module (`app`). Layers:

```
ui/ (Compose screens + ViewModels)
  └── domain/ (use-case level: DuplicateFinder, MoveOperationPlanner, validators)
        └── data/ (ContactsRepository, AccountRepository, SimContactSource, ContentObserver flows)
              └── ContactsContract / IccProvider / AccountManager / SubscriptionManager
```

DI: Hilt. Navigation: Navigation-Compose. Images: Coil.

### Data layer

**Source of truth = `ContactsContract`.** No mirror DB; the provider is already a local SQLite DB with change notifications.

`ContactsRepository`
- `observeContacts(): Flow<List<Contact>>` — callbackFlow wrapping a `ContentObserver` on `ContactsContract.Contacts.CONTENT_URI`; each notification triggers a requery (debounced ~300 ms).
- Query strategy: **bulk queries + in-memory group-by**, never per-contact queries. One query over `Data` table with minimal projection (raw_contact_id, contact_id, mimetype, data1..4, account_type, account_name, photo_thumb_uri, display_name, starred), grouped into `Contact` aggregates keyed by `contact_id` with nested `RawContact` per account.
- All methods `withContext(Dispatchers.IO)` internally — main-thread safety is enforced inside the repository, not trusted to callers.
- CRUD via `ContentProviderOperation.applyBatch`, chunked at ≤400 ops (binder 1 MB transaction limit).
- Photo copy via `openAssetFileDescriptor` streams.

`AccountRepository`
- Union of: `AccountManager.getAccounts()` (filtered to types that own raw contacts) + `DISTINCT account_type, account_name` from `RawContacts` (catches WhatsApp-style accounts invisible or restricted in AccountManager).
- Classification per account:
  - `FULL_CRUD` — com.google, com.osp.app.signin (Samsung), vnd.sec.contact.phone, null/device-local, and default.
  - `READ_ONLY` — known derived-app list (com.whatsapp, org.telegram.*, com.viber.*, org.thoughtcrime.securesms, …).
  - `SIM` — vendor SIM account types (vnd.sec.contact.sim, com.android.contacts.sim, …) or IccProvider-backed pseudo-account.
- Contact counts per account from one grouped query.

`SimContactSource` (interface)
- `IccSimSource` — `content://icc/adn` (and `content://icc/adn/subId/#` for dual SIM). Read/insert/update/delete with the undocumented-but-stable `tag`/`number` content-values contract.
- `SamsungSimSource` — SIM contacts surfaced as a RawContacts account (`vnd.sec.contact.sim`); CRUD through ContactsContract like a normal account.
- **Capability probe** on first SIM access per boot: read attempt, then reversible test-write (insert + immediate delete of a marker entry). Result cached in DataStore keyed by subscription id. Probe failure ⇒ SIM degrades to read-only (or hidden if read also fails), UI reflects it.
- SIM constraints enforced in the editor: max name length (probe-detected, default 14 chars), one phone number, no email/photo (unless EF_ANR/EF_EMAIL detected as supported — out of scope v1: name+number only), slot capacity surfaced when insert fails with full-SIM error.
- Dual SIM: `SubscriptionManager.activeSubscriptionInfoList` (needs `READ_PHONE_STATE`); each active subscription is presented as its own account entry ("SIM 1 · Telkomsel").

### Domain layer

`MoveOperationPlanner` — pure Kotlin, unit-tested. Given source raw contacts + target account, emits an ordered plan: (1) insert batch into target, (2) verify insert results, (3) delete source raw contacts. **Copy-then-delete: a mid-flight failure can leave a duplicate, never data loss.** Target-specific down-conversion (e.g. move-to-SIM drops email/photo) is computed in the plan and shown to the user before execution ("These fields will be lost: …").

`DuplicateFinder` — pure Kotlin, runs on `Dispatchers.Default`.
- Match keys: normalized phone (strip formatting, compare last-9-digits + country-code-aware equality via `PhoneNumberUtils.compare`), normalized email (lowercase/trim), name (case- and diacritic-folded exact match + token-set equality "Budi Santoso" == "Santoso Budi").
- Output: `DuplicateGroup(confidence: HIGH|MEDIUM, contacts)`. HIGH = shared phone/email. MEDIUM = name-only match.
- Excludes pairs already linked into the same aggregate.

`Linker` — writes `AggregationExceptions` (KEEP_TOGETHER / KEEP_SEPARATE).

`Merger` — physical merge: union all data rows into chosen target raw contact (dedup rows by mimetype+data1), delete other raw contacts. Preceded by an explicit confirm listing what is deleted.

### UI

Material3, dynamic color (Monet) with warm fallback palette, dark mode follows system. Typography default M3. Eye-comfort: tonal surfaces, no pure black/white, generous list item height (64 dp), min touch target 48 dp.

Screens (Navigation-Compose):

1. **Contacts (home)** — search field, account filter chips (All + one per account, each with count), alphabet fast-scroll rail, `LazyColumn` with sticky letter headers. Each row: photo/initials avatar, name, account badges (colored dots, color assigned per account deterministically). Long-press ⇒ multi-select with bottom action bar: **Move to…**, **Delete**, **Merge** (enabled when ≥2 selected). FAB = new contact.
2. **Contact detail** — aggregate view; data grouped per raw contact with account chip header. Actions per raw contact: Edit, Delete, Move to…, Copy to…. Read-only accounts show a lock chip + tooltip "Managed by WhatsApp — edit in that app."
3. **Editor** — create/edit. Account selector at top (create mode). Selecting a SIM account switches the form to name+number with live char counters and disabled unsupported fields.
4. **Duplicates** — groups sorted by confidence; each card shows member contacts with account badges and diff-style field comparison. Per-group actions: **Link** (one tap), **Merge into…** (account/raw pick + confirm dialog listing deletions), **Not duplicate** (writes KEEP_SEPARATE, group dismissed permanently).
5. **Accounts** — every detected account: icon, name, type label, contact count, writability tag (Full / Read-only / SIM). Tap ⇒ home filtered to it. Overflow: "Move all contacts to…" (batch move with progress).
6. **Permission gate** — rationale screen when READ/WRITE_CONTACTS not granted; graceful empty state with settings deep-link when permanently denied. READ_PHONE_STATE requested lazily only when SIM features touched; denial ⇒ single-SIM fallback via icc/adn without subscription labels.

### Threading & performance

- Provider access: `Dispatchers.IO` inside repository only.
- Dedupe + list filtering/search: `Dispatchers.Default`; search input debounced 250 ms, combined via `Flow.combine`.
- Long batch ops (move-all, big merges): run in an injected `ApplicationScope` (survives ViewModel/config death), progress exposed as `StateFlow<BatchProgress>`, cancellable; snackbar + inline progress UI.
- List perf: stable `key = contactId`, thumbnail URIs only (no full photos), Coil with memory cache, `derivedStateOf` for fast-scroll index.
- Target: smooth with 5 000+ contacts; bulk query + group-by measured, no per-row cursor loops.

### Compatibility & error handling

- minSdk 24, targetSdk 36.
- Every provider write wrapped in try/catch surfacing typed `ContactOpResult` (Success / PartialFailure(details) / Failure(cause)); UI shows snackbar with retry. No raw exceptions to crash.
- Vendor quirks isolated in `SimContactSource` implementations + account classifier tables (easily extendable).
- `OperationApplicationException` / `TransactionTooLargeException` handled by chunking; `SecurityException` routes to permission gate.

### Testing

- Unit (JVM): `DuplicateFinder` (match matrix incl. formatted numbers, diacritics, token-swap names), `MoveOperationPlanner` (op ordering, chunking, down-conversion), SIM field validators, account classifier.
- Instrumented (emulator): `ContactsRepository` CRUD against a local test account; move round-trip; linker.

### Dependencies added

Hilt, Navigation-Compose, Coil (compose), kotlinx-coroutines (bundled), DataStore (preferences), lifecycle-viewmodel-compose. Phone matching starts with platform `PhoneNumberUtils`; libphonenumber only if matching proves too weak.

## Rollout order (implementation phases)

1. Permissions gate + data layer read path (accounts + contacts flow) + home list UI.
2. Contact detail + editor + CRUD for ContactsContract accounts.
3. Move/copy engine + multi-select + Accounts screen.
4. SIM sources + probe + SIM editor limits.
5. Duplicate finder + Duplicates screen (link/merge).
6. Polish: theming, empty/error states, batch progress, tests hardening.
