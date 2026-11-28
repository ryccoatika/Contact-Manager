# Duplicates: explain the actions, make them undoable

Date: 2026-11-28 · Branch: `feat/duplicates-clarity` · Target: 1.3

## Intent

A user reported that the Duplicates screen feels dangerous: three buttons
(Not duplicate, Link, Merge) with no explanation of what they do, no statement
of which accounts are covered, and a home-screen counter he was not sure meant
"duplicates". He avoided the feature because of it.

The buttons should explain themselves at the moment they are used, and the two
that are meant to be harmless should actually be reversible.

## How it works today

- Duplicates are found across all accounts together (`DuplicateFinder` over the
  whole contact list).
- **Not duplicate** hides the group (stored in `DuplicatePrefs`) and sets
  `KEEP_SEPARATE` between every pair of raw contacts in the group. Instant, no
  confirmation, no way to bring the group back.
- **Link** sets `KEEP_TOGETHER` between every pair. Instant, no confirmation,
  no unlink in the app.
- **Merge** opens a "Merge into" dialog, copies missing details into the chosen
  entry and deletes the others (app-managed entries are linked instead).
- Home shows an icon with a number badge; the icon has no label.

### Defect found while reading the code

`keepSeparate` receives every raw contact of the group, including raw contacts
that already belong to the **same** contact (e.g. one person's Google and
WhatsApp entries). Setting `KEEP_SEPARATE` between those tells Android to split
a contact the user never asked to split — and the halves then show up as a new
duplicate group. Not verified on a device; it follows from
`ContactOps.aggregationOps` pairing every id with every other.

## Goals

1. The first time **Link** or **Not duplicate** is tapped, a sheet explains the
   action in plain words and asks to confirm. After that the action runs
   straight away. Remembered per action.
2. After **Link** and after **Not duplicate**, the snackbar offers **Undo**.
3. The **Merge** dialog says plainly what merge does and that it cannot be
   undone.
4. A help icon in the Duplicates top bar opens the explanations for all three
   actions, plus one line saying duplicates are found across all accounts.
5. Home: when duplicates exist, a row at the top of the contact list reads
   "N possible duplicates · Review" and opens the Duplicates screen. Shown only
   on "All", with an empty search, outside selection mode. The top-bar icon and
   badge stay.
6. Fix the defect above: **Not duplicate** separates only entries that belong
   to different contacts.

## Non-goals

- Duplicates per account (filter on the Duplicates screen) — separate feature.
- Undo for Merge.
- A list of previously hidden groups. Groups hidden before this version stay
  hidden.
- Any change to how duplicates are detected.

## Anti-goals

- A confirmation on every tap (53 groups would mean 53 extra confirmations).
- Text that promises an undo the app does not offer.

## Approaches considered

- **A. Confirm every time.** Clearest, but slow when working through many
  groups, and redundant once Undo exists.
- **B. Confirm the first time only, then Undo.** Explains at the moment of
  first use, stays fast afterwards, and Merge — the only destructive action —
  always confirms. **Chosen** (the owner left the choice to the designer).
- **C. Confirm every time with "Don't ask again".** Works, but adds a setting
  people have to notice.

## Decisions

- **First-use memory**: two booleans in `DuplicatePrefs` (link explained,
  dismiss explained). Cancelling the sheet does not mark it explained.
- **Undo Link**: reset the aggregation between entries that belonged to
  different contacts before the link back to automatic. Entries that were
  already one contact are left alone.
- **Undo Not duplicate**: remove the group key from `DuplicatePrefs` and reset
  the same cross-contact pairs to automatic.
- **Known limit of both undos**: "automatic" is what Android does by default.
  If the user had manually separated or joined those entries in another app
  beforehand, undo does not restore that exact earlier choice.
- **Undo surface**: the snackbar. `UiEvent.ShowSnackbar` gains an optional
  action (label + callback); `CollectUiEvents` runs the callback when the
  action is tapped. No second event convention.
- **SIM entries** read straight from the card cannot be linked or separated
  (existing failure path); nothing to undo there.
- **Home row** is presentation only: it derives from fields already in
  `HomeUiState` (`duplicateCount`, `query`, `selectedAccountKey`,
  `selectedContactIds`).
- **Analytics**: one new event for undo (which action, group size) — counts and
  enum labels only.

## Constraints

- Architecture rules R1–R14; ViewModel dependencies stay interfaces, every
  grown interface gets its fake updated (`ContactsWriter` has several fakes).
- All copy in `strings_duplicates.xml` / `strings_home.xml`, written by the
  `ux-writer` agent; plurals for counts.
- Sheets on the design system (`ui-designer`), `TrackScreenView` on each,
  light and dark previews.
- User-facing → `CHANGELOG.md` under 1.3 (not yet promoted to Production, so
  this ships in the same release as the crash and SIM fixes).

## Left to the builder

- Exact method shapes on `ContactsWriter` / `DuplicatePrefs` for "separate
  across contacts", "reset aggregation" and "restore group".
- Sheet versus dialog component, layout, and final wording.
- Test list: ViewModel tests for first-use gating, undo of each action, the
  cross-contact pairing, and the home row visibility rule.

## Risks

- Aggregation behaviour is Android's; undo and the defect fix are reasoned
  from the provider contract and unit-tested with fakes, not device-verified.
- It joins 1.3 without any device run of its own; the 1.3 build must be tested
  again from Internal testing before promotion.
