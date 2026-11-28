# Duplicates Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Duplicates actions explain themselves on first use, make Link and Not duplicate undoable, fix Not duplicate splitting already-joined contacts, and add a readable duplicates row on Home.

**Architecture:** Pure pairing logic in `domain/`, two new writer/prefs operations in `data/`, first-use gating and undo in `DuplicatesViewModel`, sheets and the Home row in `ui/`. Undo travels on the existing `UiEvent.ShowSnackbar`, which gains an optional action.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, DataStore, JUnit4 + kotlinx-coroutines-test, hand-written fakes.

**Spec:** `docs/superpowers/specs/2026-11-28-duplicates-clarity-design.md`

## Global Constraints

- Architecture rules R1–R14 (`docs/ARCHITECTURE.md`): `domain/` pure Kotlin; ViewModel deps are interfaces; one event convention (`UiEvent`); no feature imports another feature.
- Every grown interface gets all its fakes updated in the same task (`ContactsWriter` has 6 implementers in `app/src/test`, plus `SimAwareContactsWriter`; `DuplicatePrefs` has `FakePrefs` in `DuplicatesViewModelTest`).
- Kotlin warnings are errors; no `@Suppress` to get green. Files ≤ 500 lines.
- All user-visible text in `res/values/strings_duplicates.xml` / `strings_home.xml`; copy below is final — use it verbatim.
- Analytics: counts and enum labels only.
- `TrackScreenView` on every sheet; light and dark `@Preview` for new composables.
- No change to `DuplicateFinder`.
- Do not commit; the reviewer commits.
- Gates per task: `./gradlew compileDebugKotlin`, `./gradlew spotlessApply`, `./gradlew spotlessCheck`, `./gradlew :app:testDebugUnitTest`; plus `./gradlew :app:lintDebug` at the end of Tasks 4–6.

## Review Focus

1. Undo tapped after the contacts changed underneath (a member deleted or merged elsewhere) → no crash; a failed reset is swallowed, the group simply reflects current data. (Task 3)
2. Link or Not duplicate tapped twice quickly before the first-use lookup returns → one sheet, one action. (Task 3)
3. First-use sheet cancelled → not remembered; the next tap shows it again. (Task 3)
4. Group containing an entry read straight from the SIM card (negative raw id) → Link fails with the existing message and offers no Undo; Not duplicate still hides the group and its Undo still restores it. (Task 3)
5. Home row shown → the fast-scroll rail and drag-to-select still land on the right contacts (the row shifts list indices by one). (Task 5)

---

### Task 1: Cross-contact aggregation in the data layer

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/AggregationPairs.kt`, `domain/DuplicateAction.kt`
- Modify: `data/ContactOps.kt`, `data/ContactsWriteRepository.kt` (interface `ContactsWriter` + impl), `data/SimAwareContactsWriter.kt`, `data/DuplicatePrefs.kt`, the strings file that holds `cwr_action_separate`
- Test: `app/src/test/.../domain/AggregationPairsTest.kt`; update every `ContactsWriter` fake and `FakePrefs`

**Interfaces — Produces:**
- `object AggregationPairs { fun across(contacts: List<List<Long>>): List<Pair<Long, Long>> }` — every unordered pair of ids taken from two **different** inner lists, each pair once, ids de-duplicated; no pair from within one inner list.
- `enum class DuplicateAction { LINK, DISMISS }`
- `ContactsWriter.keepSeparate(contacts: List<List<Long>>): ContactOpResult` — **replaces** `keepSeparate(rawContactIds: List<Long>)`; each inner list is one contact's raw ids; `KEEP_SEPARATE` across lists only.
- `ContactsWriter.resetAggregation(contacts: List<List<Long>>): ContactOpResult` — `AggregationExceptions.TYPE_AUTOMATIC` across lists only.
- `ContactsWriter.linkContacts(rawContactIds: List<Long>)` — unchanged.
- `DuplicatePrefs.restore(key: String)`, `DuplicatePrefs.isExplained(action: DuplicateAction): Boolean`, `DuplicatePrefs.markExplained(action: DuplicateAction)` (all `suspend`; DataStore-backed, IOException swallowed like `dismiss`).

- [ ] **Step 1: Failing test** `AggregationPairsTest`:
  - `across(listOf(listOf(1, 2), listOf(3)))` == `[(1,3), (2,3)]` (as a set of unordered pairs)
  - a single contact `listOf(listOf(1, 2, 3))` → empty
  - three contacts `[[1],[2],[3]]` → 3 pairs
  - duplicate ids inside a list do not duplicate pairs; empty input → empty
- [ ] **Step 2:** run `./gradlew :app:testDebugUnitTest`, expect unresolved `AggregationPairs`.
- [ ] **Step 3:** implement `AggregationPairs` and `DuplicateAction`.
- [ ] **Step 4:** `ContactOps`: add `fun aggregationOpsAcross(contacts: List<List<Long>>, type: Int): List<ContentProviderOperation>` built from `AggregationPairs.across`; keep `aggregationOps` for `linkContacts`.
- [ ] **Step 5:** change `keepSeparate`, add `resetAggregation` in `ContactsWriter`, `ContactsWriteRepository` (same `runCatchingOp` + `applyChunked` envelope; new action string `cwr_action_undo` = "undo", following the neighbouring `cwr_action_*` entries) and `SimAwareContactsWriter` (same SIM guard as today, applied to the flattened ids). Update every caller of `keepSeparate` (grep the whole `app/src`).
- [ ] **Step 6:** extend `DuplicatePrefs` + `DataStoreDuplicatePrefs` (`booleanPreferencesKey("explained_link")`, `"explained_dismiss"`); update all fakes so the module compiles.
- [ ] **Step 7:** gates green.

### Task 2: Snackbar action on `UiEvent`

**Files:** Modify `ui/common/UiEvent.kt`.

**Interfaces — Produces:** `UiEvent.ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)`.

- [ ] **Step 1:** add the two parameters. In `CollectUiEvents`, pass `actionLabel` to `showSnackbar`, use `SnackbarDuration.Long` when an action is present (default otherwise), and invoke `onAction` on `SnackbarResult.ActionPerformed`.
- [ ] **Step 2:** `./gradlew compileDebugKotlin :app:testDebugUnitTest` — existing `ShowSnackbar("…")` call sites and assertions must still compile and pass unchanged.

### Task 3: First-use gating and undo in `DuplicatesViewModel`

**Files:**
- Modify: `ui/duplicates/DuplicatesViewModel.kt`, `domain/analytics/AnalyticsEvent.kt`, `res/values/strings_duplicates.xml`
- Test: `app/src/test/.../ui/duplicates/DuplicatesViewModelTest.kt`

**Interfaces — Consumes:** Task 1 and Task 2 signatures.
**Interfaces — Produces:**
- `data class PendingExplain(val action: DuplicateAction, val group: DuplicateGroup)`; `DuplicatesUiState.pendingExplain: PendingExplain? = null`
- `fun link(group)`, `fun dismiss(group)` (existing names; now gated), `fun confirmExplained()`, `fun cancelExplain()`
- `AnalyticsEvent.DuplicateUndo(action: String, count: Int)` → name `"duplicate_undo"`, params `action` (`"link"` / `"dismiss"`), `count` (contacts in the group)
- strings: `duplicates_undo` = "Undo", `duplicates_msg_dismissed` = "Group hidden"

Behaviour:
- `link` / `dismiss`: if `prefs.isExplained(action)` is false → set `pendingExplain` and stop; else perform.
- `confirmExplained()`: `markExplained`, clear `pendingExplain`, perform. `cancelExplain()`: clear only.
- A second `link`/`dismiss` call while `pendingExplain != null` or while the lookup is in flight is ignored.
- Perform **link**: `writer.linkContacts(all raw ids)`. Success → existing analytics + `ShowSnackbar(existing linked message, actionLabel = Undo, onAction = { undo })`. Failure → `ShowSnackbar(result.message)` with no action.
- Perform **dismiss**: `prefs.dismiss(key)`, `writer.keepSeparate(group.contacts.map { it.rawContacts.map { r -> r.rawContactId } })` (failure ignored, as today), existing analytics, then `ShowSnackbar("Group hidden", Undo, { undo })`.
- **Undo link**: `writer.resetAggregation(same grouped ids)`; failure ignored.
- **Undo dismiss**: `prefs.restore(key)` then `writer.resetAggregation(grouped ids)`; failure ignored.
- Both undos log `DuplicateUndo`.

- [ ] **Step 1: Failing tests** (extend the existing fakes to record calls):
  - `first link shows the explanation and does not link` — `uiState.pendingExplain == PendingExplain(LINK, group)`, writer saw no link
  - `confirming the explanation links, remembers it, and later links run straight away`
  - `cancelling the explanation is not remembered` — next `link` sets `pendingExplain` again
  - `link snackbar offers undo and undo resets aggregation across contacts` — event has `actionLabel == "Undo"`; invoking `onAction` calls `resetAggregation([[rawIds of contact A], [rawIds of contact B]])`
  - `dismiss separates only across contacts` — group with contact A = raws (10, 11) and contact B = raw (20) → `keepSeparate([[10, 11], [20]])`
  - `dismiss snackbar undo restores the group and resets aggregation` — key removed from prefs, group back in `uiState.groups`
  - `failed link offers no undo`
  - `dismiss still hides the group when keepSeparate fails, and undo restores it` — the SIM-card-entry case: `keepSeparate` and `resetAggregation` both return `Failure`
  - `a failing undo does not crash and emits nothing more` — `resetAggregation` returns `Failure`
  - `second tap while the explanation is pending is ignored`
  - `undo logs duplicate_undo with action and count`
  - update existing tests that compare emitted `ShowSnackbar` values so they assert on `message` / `actionLabel`.
- [ ] **Step 2:** run, expect failures on the new symbols.
- [ ] **Step 3:** implement (a `MutableStateFlow<PendingExplain?>` joined into the existing `combine`).
- [ ] **Step 4:** gates green.

### Task 4: Duplicates screen — first-use sheet, help, merge wording

**Files:**
- Create: `ui/duplicates/DuplicatesSheets.kt`
- Modify: `ui/duplicates/DuplicatesScreen.kt`, `res/values/strings_duplicates.xml`

**Interfaces — Consumes:** `DuplicatesUiState.pendingExplain`, `confirmExplained()`, `cancelExplain()`.

Copy (verbatim):
- `duplicates_explain_link_title` "Link these contacts?" · `duplicates_explain_link_body` "They will show as one contact. Nothing is deleted, and you can undo it right after."
- `duplicates_explain_dismiss_title` "Not duplicates?" · `duplicates_explain_dismiss_body` "This group will be hidden from the list and the contacts stay separate. Nothing is deleted, and you can undo it right after."
- `duplicates_explain_once` "You'll only be asked this the first time."
- `duplicates_scope` "Duplicates are found across all your accounts."
- `duplicates_merge_warning` "Missing details are copied to the contact you keep, then the other entries are deleted. This can't be undone."
- `duplicates_help` "How duplicates work" (content description and sheet title)
- `duplicates_help_dismiss_body` "Hides the group and keeps the contacts separate. Nothing is deleted. You can undo it right after."
- `duplicates_help_link_body` "Shows the contacts as one. Nothing is deleted. You can undo it right after."
- `duplicates_help_merge_body` "Copies missing details into the contact you choose, then deletes the others. Contacts that belong to another app, like WhatsApp, are linked instead. This can't be undone."
- Section titles in the help sheet reuse `duplicates_not_duplicate`, `duplicates_link`, `duplicates_merge`; buttons reuse `duplicates_link`, `duplicates_not_duplicate`, `duplicates_cancel`.

- [ ] **Step 1:** `DuplicatesSheets.kt`: `ExplainActionSheet(pending: PendingExplain, onConfirm, onDismiss)` and `DuplicatesHelpSheet(onDismiss)` as `ModalBottomSheet`s on the design system (shape `large`, tokens only). The explain sheet shows title, body, the scope line, the "first time" line, and Cancel / confirm buttons. `TrackScreenView("duplicates_explain")` and `TrackScreenView("duplicates_help")`. Stateless content composables with light + dark previews.
- [ ] **Step 2:** `DuplicatesScreen`: show `ExplainActionSheet` when `state.pendingExplain != null`; add a help `IconButton` to the top bar actions (`Icons.AutoMirrored.Outlined.HelpOutline`, 48dp target, content description `duplicates_help`). When `embedded` (no top bar), render the same button end-aligned in a slim row above the content so help stays reachable.
- [ ] **Step 3:** add `duplicates_merge_warning` as a line in the existing merge confirmation dialog, above the buttons.
- [ ] **Step 4:** gates green incl. `:app:lintDebug`.

### Task 5: Home — duplicates row

**Files:**
- Modify: `ui/home/HomeViewModel.kt` (`HomeUiState` only), `ui/home/HomeContactList.kt`, `ui/home/HomeScreen.kt`, `res/values/strings_home.xml`
- Test: `app/src/test/.../ui/home/HomeUiStateTest.kt` (new)

**Interfaces — Produces:** `val HomeUiState.showDuplicatesRow: Boolean` = `duplicateCount > 0 && query.isBlank() && selectedAccountKey == null && selectedContactIds.isEmpty()`.

Copy: plurals `home_duplicates_row` — one "%1$d possible duplicate", other "%1$d possible duplicates"; `home_duplicates_review` "Review".

- [ ] **Step 1: Failing test** `HomeUiStateTest`: true for count 3 / blank query / no account / no selection; false for each of: count 0, query "a", an account key selected, a non-empty selection.
- [ ] **Step 2:** run, expect unresolved `showDuplicatesRow`; implement.
- [ ] **Step 3:** `HomeContactList`: new parameters `duplicatesRowCount: Int?` (null = hidden) and `onDuplicatesClick: () -> Unit`; render the row as the first list item (key `"duplicates-row"`): count text + a "Review" text button, whole row clickable, 48dp min height, tokens only. Read `HomeGestures.kt` and the fast-scroll code first: every place that maps list indices to contacts (`letterIndex`, drag-to-select hit testing) must account for the extra leading item.
- [ ] **Step 4:** `HomeScreen`: pass `state.duplicateCount.takeIf { state.showDuplicatesRow }` and the existing duplicates navigation callback. Check the tablet shell call sites compile.
- [ ] **Step 5:** gates green incl. `:app:lintDebug`.

### Task 6: Release notes (ships in 1.3)

Originally planned as 1.4; the owner folded it into 1.3, which was not yet in
Production. `appVersionName` stays `1.3`.

**Files:** `CHANGELOG.md`, `RELEASE-NOTES.md`

- [ ] **Step 1:** `CHANGELOG.md` — add to the existing `## [1.3]` section (follow `.claude/skills/update-changelog/SKILL.md`): the first-use explanation for Link / Not duplicate and the help button; Undo after Link and Not duplicate; the clearer Merge warning; the "possible duplicates" row on the contact list; fix: "Not duplicate" no longer separates entries that already belonged to the same contact.
- [ ] **Step 2:** `RELEASE-NOTES.md` — fold the duplicates changes into the `## 1.3` entry (≤ 500 characters).
- [ ] **Step 3:** `./gradlew -q changelogs` prints the 1.3 section with the new bullets.
