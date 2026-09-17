# Import / Export Contacts — Design

**Date:** 2026-09-17
**Status:** Approved design, pre-implementation
**Branch:** `feat/import-export`

## Goal

Let users move contacts in and out of the app as standard vCard (`.vcf`)
files:

- Export per account or across several accounts (Accounts settings).
- Export the current multi-selection (Home selection bar).
- Import a `.vcf` file into one or several accounts (Accounts settings).

The account is always chosen **before** the file dialog: it is the
source of an export and the destination of an import.

## Decisions (locked with the user)

| Decision | Choice |
|---|---|
| Format | vCard 3.0 writer; tolerant 2.1/3.0/4.0 parser. No JSON. |
| Photos | Embedded (base64 `PHOTO`) on export and import. |
| Import dedupe | None — import everything; the Duplicates screen handles merges afterward. |
| Multi-account export | Ask at export time: one combined file **or** one file per account. |
| SIM | In scope both directions. Import to SIM down-converts (name + first number) with the existing field-loss warning pattern. |
| vCard engine | Hand-rolled in `domain/vcard` (no new dependency). |

## Architecture

```
domain/vcard/          pure Kotlin, no Android imports
  VCard.kt             VCardContact model (+ parse-result types)
  VCardWriter.kt       List<VCardContact> -> String   (vCard 3.0)
  VCardParser.kt       String -> VCardParseResult     (2.1/3.0/4.0 tolerant)

data/transfer/
  ContactTransfer.kt   interface + DefaultContactTransfer (Hilt singleton)

ui/accounts/           row menu + new global top-bar menu, sheets/dialogs
ui/home/               Export action in the selection bottom bar
```

Layering unchanged: ui → data → domain. No new routes — every new
surface is a sheet or dialog, so `AppNav.kt` and `TabletShell.kt` are
untouched.

### domain/vcard

`VCardContact` mirrors what the app can store — nothing more:

```kotlin
data class VCardContact(
    val displayName: String,          // FN (fallback: joined N, else "")
    val givenName: String? = null,    // N pieces kept for export fidelity
    val familyName: String? = null,
    val phones: List<Pair<String, String?>> = emptyList(),  // value to typeLabel
    val emails: List<Pair<String, String?>> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val nickname: String? = null,
    val websites: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),   // formatted, one string each
    val birthday: String? = null,                // raw provider/vCard string
    val anniversary: String? = null,
    val note: String? = null,
    val photo: ByteArray? = null,
)
```

**Writer (3.0):** `BEGIN:VCARD`/`VERSION:3.0`/`FN`/`N`/`TEL;TYPE=`/
`EMAIL`/`ORG`/`TITLE`/`NICKNAME`/`URL`/`ADR`/`BDAY`/`X-ANNIVERSARY`/
`NOTE`/`PHOTO;ENCODING=b;TYPE=JPEG`/`END:VCARD`. CRLF line ends,
75-octet folding, `\` `,` `;` newline escaping. Deterministic output
(stable property order) so tests can assert exact strings.

**Parser:** line unfolding first, then property parse
(`NAME;PARAM=..:value`). Supported inputs: vCard 2.1 (including
`ENCODING=QUOTED-PRINTABLE` with soft line breaks and `CHARSET=`),
3.0, and 4.0 basics. Base64 photos in both `ENCODING=b` (3.0) and
`ENCODING=BASE64` (2.1, with continuation lines) forms. Unknown
properties are skipped silently. A card missing `END:VCARD` or
otherwise malformed is skipped and **counted**:

```kotlin
data class VCardParseResult(
    val contacts: List<VCardContact>,
    val skippedCards: Int,
)
```

`ANNIVERSARY` (4.0) and `X-ANNIVERSARY` both map to `anniversary`.
`ITEM1.TEL`-style grouped properties (Apple) are read by stripping the
group prefix.

### data/transfer

```kotlin
interface ContactTransfer {
    /** All contacts of [accounts] into one .vcf at [uri]. */
    suspend fun exportAccounts(accounts: List<ContactAccount>, uri: Uri): TransferResult
    /** One .vcf per account inside the SAF folder [treeUri]. */
    suspend fun exportAccountsToFolder(accounts: List<ContactAccount>, treeUri: Uri): TransferResult
    /** The given raw contacts (Home selection) into one .vcf at [uri]. */
    suspend fun exportRawContacts(rawContactIds: List<Long>, uri: Uri): TransferResult
    /** Parses [uri]; does not write anything yet. */
    suspend fun parseFile(uri: Uri): ParseOutcome
    /** Starts the batched insert of [contacts] into every [targets] account; false when a batch is already running. */
    fun startImport(contacts: List<VCardContact>, targets: List<ContactAccount>): Boolean
}

sealed interface TransferResult {
    data class Success(val contactCount: Int, val fileCount: Int) : TransferResult
    data class Failure(val message: String) : TransferResult
}

sealed interface ParseOutcome {
    data class Parsed(val contacts: List<VCardContact>, val skippedCards: Int) : ParseOutcome
    data class Failure(val message: String) : ParseOutcome
}
```

- **Export**: `ContactsSource.snapshot()` → filter raw contacts by
  account (or by ids for the Home selection) → map `RawContact` →
  `VCardContact` → fetch the photo blob per raw contact
  (`ContactsContract.CommonDataKinds.Photo.PHOTO`, same source the
  copy path uses) → `VCardWriter` → `contentResolver.openOutputStream`.
  All on `Dispatchers.IO`, wrapped; `TransferResult` carries exported
  count or a user-facing failure message. Per-account mode creates
  `<sanitized-account-label>.vcf` files via `DocumentFile`, appending
  ` (2)` etc. on name collision.
- **Import**: `parseFile` reads + parses off the main thread and
  returns `ParseOutcome` (contacts + skipped count, or failure) so the
  UI can show the confirm dialog. `startImport` runs through
  `BatchRunner` so the existing Home progress bar and cancel work
  unchanged: per (target × contact), SIM targets get the down-convert
  + `SimContactValidator` route, provider targets go through
  `ContactsWriter.createContact`. Failures are counted; the finish
  snackbar reads "Imported X of Y" on partial failure.
- **SIM export** needs no special code — SIM entries are already raw
  contacts in the snapshot.

### BatchRunner generalization

`BatchOperationManager` already has a private
`start(total, label, finishedMessage, operation)`. That becomes the
public interface method:

```kotlin
interface BatchRunner {
    fun run(total: Int, label: String, finishedMessage: String,
            operation: suspend (onProgress: (Int, Int) -> Unit) -> ContactOpResult): Boolean
    // moveContacts / copyContacts stay as thin wrappers over run(...)
}
```

`ContactTransfer.startImport` uses `run`. One batch at a time —
unchanged.

### EditableContact photo

`EditableContact` gains `val photo: ByteArray? = null`.
`ContactsWriteRepository.createContact` writes a `Photo` data row when
present. The editor never sets it — no editor changes.

**Known v1 limits (accepted):** phone/email type labels are parsed and
exported, but the existing `createContact` write path drops type
labels (pre-existing v1 write behavior); imported given/family names
are joined into the display name the same way the editor does.

## UI flows

### Accounts screen

- **Row three-dot menu** (existing) gains two items below Move/Copy:
  - *Export contacts…* — source = that account.
  - *Import contacts…* — target = that account.
- **New top-bar three-dot menu** with the same two items; each opens an
  **account multi-select sheet** first (checkbox-sheet pattern reused
  from the delete chooser: `AccountDot` + label + contact count,
  confirm button with count). For import the sheet's confirm reads
  "Import into N accounts".

### Export flow

1. Account(s) known (row menu = one; global sheet = many).
2. If **more than one** account: dialog *"One file / One file per
   account"*. Single account skips it.
3. One file → SAF `CreateDocument("text/x-vcard")`, suggested name
   `contacts-YYYY-MM-DD.vcf` (or `<account>-YYYY-MM-DD.vcf` for a
   single account). Per-account → SAF `OpenDocumentTree`.
4. `ContactTransfer` export on IO; snackbar with the exported count or
   the failure message. Fast enough to run inside the ViewModel scope —
   no batch bar; a running export shows the standard busy snackbar if
   the user re-triggers.

### Import flow

1. Target account(s) known (row menu = one; global sheet = many).
2. SAF `OpenDocument` (`text/x-vcard`, `text/vcard`, `*/*` fallback —
   many file managers mislabel `.vcf`).
3. `parseFile` → confirm dialog: "Import N contacts into
   <account / N accounts>?" plus, when any target is a SIM, the
   existing field-loss style warning (name + first number only). Zero
   parsed contacts → error snackbar, no dialog. Skipped-card count
   shown in the dialog when nonzero.
4. Confirm → `startImport` (BatchRunner). Snackbar "Import started";
   progress + cancel appear on Home exactly like move/copy batches.
   `false` (batch busy) → busy snackbar.

### Home selection bar

- New *Export* action (with Move/Copy/Delete/Merge — the bar's
  TextButtons stay, five fit with the existing 4dp spacing; if a small
  screen clips, the bar scrolls horizontally).
- Whole selection is exported, read-only raw contacts included —
  export never mutates.
- `CreateDocument` → `exportRawContacts` → snackbar.

### SAF plumbing

`rememberLauncherForActivityResult` in the screens; the picked `Uri`
is handed to the ViewModel together with the remembered pending
request (accounts + mode), mirroring how sheets already park pending
state. No runtime permissions involved — SAF grants per-Uri access.

## Analytics

Counts and enum labels only, never contact data:

- `ContactsExport(count: Int, accountCount: Int, perAccountFiles: Boolean)`
- `ContactsImport(count: Int, accountCount: Int, simTarget: Boolean)`
- `TrackScreenView` on: `export_account_picker`, `import_account_picker`,
  `export_mode_dialog`, `import_confirm`.

## Strings

New ids in `strings_accounts.xml` / `strings_home.xml` (+ `_messages`
where snackbars live); plurals for all counts; ux-writer pass before
merge.

## Error handling

- Every stream/provider call wrapped (`runCatching` envelope style);
  failures surface as snackbars via typed results — never crashes.
- Malformed cards skipped + counted; empty parse → clear error.
- Partial import failure → "Imported X of Y".
- Export file write failure → message; a partially written single file
  is deleted best-effort on failure.

## Testing

JVM unit tests (no mocking libraries, `Fake*` doubles as usual):

- **VCardWriterTest** — exact-output assertions: escaping, folding,
  photo base64, empty-field omission, CRLF.
- **VCardParserTest** — round-trip with the writer; tolerance suite:
  2.1 QP with soft breaks + charset, 2.1 BASE64 photo continuation,
  3.0 Google export sample, 4.0 basics, Apple `item1.` groups,
  unknown properties, malformed card skipping/counting.
- **DefaultContactTransferTest** — export filtering per account /
  per ids; photo attach; import routing (provider vs SIM), multi-target
  fan-out, partial-failure counting; busy-batch refusal. Fake
  ContentResolver seam kept thin (stream provider interface).
- **ViewModel tests** — Accounts: request → sheet state → uri →
  result snackbar; Home: export-selection path.
- **BatchOperationManagerTest** — `run` generalization keeps move/copy
  behavior (existing tests keep passing).

## Out of scope (YAGNI)

- Automatic/scheduled backups; share-sheet integration; import
  previews per contact; group/label (`CATEGORIES`) support; IM
  properties; contact-app "account" X-properties; dedupe-on-import.
