# Tablet Adaptive Layout — Design

**Date:** 2026-08-01
**Status:** Approved

## Goal

Make the app look good and feel native on 7" and 10" tablets (and unfolded
foldables) using the Material 3 canonical **navigation rail + list-detail**
layout. Phones and compact widths keep the exact current UX.

## Approach (approved)

- **Rail + two-pane**, driven by window size class — not device type.
- **Editor opens in the detail pane** on tablet (list stays visible).
- **Duplicates & Accounts** render as a single width-capped, centered pane.

## Constraints & principles

- **Compact width is untouched.** The existing `AppNav` / `MainNavGraph` renders
  verbatim on compact; the tablet shell is a sibling branch chosen at runtime.
- **Reuse, don't rewrite.** ViewModels and all logic are unchanged. The work is
  extracting each screen's *content* into a pane-able composable; the phone
  screen wraps it in its `Scaffold`, the tablet pane drops it in bare.
- **Golden rules hold** — no ViewModel/domain/data changes; pure-logic tests stay
  green (146+). This is a UI feature, so it legitimately restructures navigation
  (the "restyle must not touch AppNav" rule is about restyles, not features).
- **Design tokens only** — Porcelain & Pine palette, existing components.

## Dependencies (add to `gradle/libs.versions.toml`, versioned by the Compose BOM)

- `androidx.compose.material3.adaptive:adaptive`
- `androidx.compose.material3.adaptive:adaptive-layout`
- `androidx.compose.material3.adaptive:adaptive-navigation`
- `androidx.compose.material3:material3-adaptive-navigation-suite`

`currentWindowAdaptiveInfo()` (from `adaptive`) yields the `WindowSizeClass`; we
branch on `windowWidthSizeClass == COMPACT`.

## Architecture

```
ui/AppNav.kt
  AppNav()            onboarding gate + PermissionGate (unchanged)
    -> AdaptiveApp()  NEW: compact ? MainNavGraph() : TabletShell()

ui/adaptive/
  AdaptiveApp.kt      reads currentWindowAdaptiveInfo(); routes compact vs tablet
  TabletShell.kt      NavigationSuiteScaffold (rail) with Contacts/Duplicates/Accounts
  ContactsListDetail.kt   NavigableListDetailPaneScaffold: list pane + detail pane
  DetailPaneHost.kt   switches the detail pane between: empty / contact / editor

ui/home/HomeScreen.kt      phone screen = TopAppBar + HomeListContent
ui/home/HomeListContent.kt NEW (extracted): search + chips + list + FAB + dialogs
ui/detail/DetailScreen.kt      phone = Scaffold + ContactDetailContent
ui/detail/ContactDetailContent NEW (extracted): hero + fields + edit/delete
ui/editor/EditorScreen.kt      phone = Scaffold + ContactEditorContent
ui/editor/ContactEditorContent NEW (extracted): groups + fields + account picker
```

### Window routing (`AdaptiveApp`)

```kotlin
val info = currentWindowAdaptiveInfo()
val compact = info.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
if (compact) MainNavGraph() else TabletShell()
```

`MainNavGraph` is today's `NavHost` verbatim (moved out of `AppNav` unchanged).

### Tablet shell (`TabletShell`)

`NavigationSuiteScaffold` with a rail. Rail items (a local enum
`TabletDestination`): **Contacts**, **Duplicates**, **Accounts**, each with an
icon + label from existing strings. Selected item state hoisted in the shell.
Content area renders:
- `Contacts` → `ContactsListDetail()`
- `Duplicates` → centered, width-capped `DuplicatesScreen` content
- `Accounts` → centered, width-capped `AccountsScreen` content

The New-contact FAB is presented inside the Contacts list pane (as today), not
the rail, so it only shows in the Contacts context.

### Contacts list-detail (`ContactsListDetail`)

`NavigableListDetailPaneScaffold` (handles pane state + back/predictive-back):
- **List pane:** `HomeListContent(onContactClick = { navigator.navigateTo(Detail) ; selected = id }, onAddClick = { detailMode = New })`. The existing multi-select, move/merge/delete dialogs live here unchanged.
- **Detail pane:** `DetailPaneHost(state)` where `state` is one of:
  - `Empty` → friendly placeholder (app monogram + "Select a contact").
  - `Viewing(contactId)` → `ContactDetailContent(contactId)`; its Edit → `Editing`, Delete → back to `Empty`.
  - `New` / `Editing(rawContactId)` → `ContactEditorContent(...)`; Save/Cancel → `Viewing`/`Empty`.
  Detail-pane state is hoisted in `ContactsListDetail` (a `rememberSaveable` sealed `DetailPaneState`), so the list drives it.

VMs are obtained per pane via `hiltViewModel(key = contactId/rawContactId)` so
each contact/edit target gets its own scoped ViewModel (same as nav args today).

## Reuse extraction (the bulk of the work)

Each extraction is mechanical: move the inner content out of the `Scaffold`, keep
the phone screen calling it. No behavior change on phone.

- `HomeListContent`: everything Home renders **inside** its `Scaffold` body
  (search field, filter chips, LazyColumn with sticky headers + `ContactRow`,
  swipe-to-delete, the pickers/dialogs). Params: the existing `onContactClick`,
  `onAddClick`, plus the `HomeViewModel` (default `hiltViewModel()`). The
  phone `HomeScreen` keeps its `TopAppBar` (title + Duplicates/Accounts icons +
  selection top bar) and calls `HomeListContent`.
- `ContactDetailContent`: the hero + fields section cards + edit/delete row.
  Phone `DetailScreen` keeps the back `TopAppBar`; the tablet pane supplies no
  top bar (rail/pane back handles it).
- `ContactEditorContent`: the group cards + account picker + fields. Phone
  `EditorScreen` keeps the Cancel/title/Save `TopAppBar`; the tablet pane renders
  a compact header row (title + Save) above the content.

## Edge cases

- **Selection mode** stays in the list pane; the detail pane keeps showing the
  last viewed contact. Back clears selection (already implemented).
- **Move/merge pickers** (ModalBottomSheet/AlertDialog) render over the whole
  window as today — fine on tablet.
- **Rotation / fold** re-evaluates the size class and swaps layouts; pane state
  is `rememberSaveable` so the selected contact survives.
- **Deep state** (a contact opened on phone, then rotated to tablet) — the
  compact NavHost and tablet shell are independent; on switch the tablet shell
  starts at Contacts with an empty detail. Acceptable for v1.

## Testing / gates

- No VM/domain/data edits → the 164 unit tests stay green.
- Add `@Preview`s: `TabletShell` and `ContactsListDetail` at expanded width
  (`widthDp = 1280`), plus the empty-detail placeholder — light + dark.
- Device-verify on the 7"/10" tablet emulators with the seeded data
  (`/verify-ui` flow).

## Out of scope (YAGNI)

- Two-pane Accounts/Duplicates (single centered pane for now).
- `SupportingPaneScaffold` / third "extra" pane.
- Foldable hinge/posture-aware separation, drag-to-resize panes.
- Landscape-phone two-pane (compact stays single-pane).
