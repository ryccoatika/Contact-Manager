# Tablet Adaptive Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On medium/expanded windows (7"/10" tablets, unfolded foldables), render a Material navigation rail + a Contacts list-detail two-pane (editor in the detail pane); Duplicates/Accounts as centered single panes. Compact width keeps the current app verbatim.

**Architecture:** A runtime `AdaptiveApp` branches on `currentWindowAdaptiveInfo()`: compact → today's `MainNavGraph`; else → `TabletShell`. The shell uses `NavigationSuiteScaffold` (canonical rail). Contacts is a size-aware two-pane (`Row`: the reused `HomeScreen` list + a nested `NavHost` for detail/editor) so `DetailViewModel`/`EditorViewModel` keep getting their ids from nav-arg `SavedStateHandle` — **no ViewModel/domain/data changes**. Screens gain an `embedded` flag that drops their own top-bar nav chrome when hosted in a pane.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 Adaptive (`currentWindowAdaptiveInfo`, `NavigationSuiteScaffold`), Navigation-Compose, Hilt.

> **Mechanism note (deviation from spec):** the two-pane is a size-aware `Row` + nested `NavHost` rather than `NavigableListDetailPaneScaffold`. Same approved UX (rail + list beside detail, editor in the detail pane), but it reuses the existing nav-arg screens with zero VM changes and is far lower-risk to verify. The rail stays canonical (`NavigationSuiteScaffold`). Trade-off: no predictive-back pane slide animation.

## Global Constraints

- **Compact width is byte-for-byte unchanged** — `MainNavGraph` (today's `NavHost`) renders as-is; the tablet shell is a sibling branch.
- **No ViewModel / domain / data edits** — the 164 unit tests must stay green.
- **Design tokens only** — Porcelain & Pine palette + existing components.
- **Compose BOM `2026.02.01`** versions the adaptive libs — add them to `gradle/libs.versions.toml`, never inline.
- **Compile after every task:** `./gradlew :app:compileDebugKotlin`; run `./gradlew :app:testDebugUnitTest` at least once at the end (expect green, unchanged).
- **Commit trailer:** `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Package `com.ryccoatika.contactmanager`.

---

## File Structure

- `gradle/libs.versions.toml`, `app/build.gradle.kts` — adaptive deps (Task 1)
- `ui/home/HomeScreen.kt`, `ui/detail/DetailScreen.kt`, `ui/editor/EditorScreen.kt`, `ui/duplicates/DuplicatesScreen.kt`, `ui/accounts/AccountsScreen.kt` — add `embedded` flag (Task 2)
- `ui/AppNav.kt` — split into `AppNav` + `MainNavGraph` (already separate) + call `AdaptiveApp` (Task 3)
- `ui/adaptive/AdaptiveApp.kt` — size-class branch (Task 3)
- `ui/adaptive/TabletShell.kt` — rail + destinations (Task 4)
- `ui/adaptive/ContactsTwoPane.kt` — list + nested detail NavHost (Task 5)
- `@Preview`s + device verify (Task 6)

---

## Task 1: Add Material 3 Adaptive dependencies

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Add libraries to `gradle/libs.versions.toml`** under `[libraries]` (no version — the Compose BOM supplies it):

```toml
androidx-compose-material3-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive" }
androidx-compose-material3-adaptive-navigation-suite = { group = "androidx.compose.material3", name = "material3-adaptive-navigation-suite" }
```

- [ ] **Step 2: Add to `app/build.gradle.kts` `dependencies { }`** (below the other `androidx.compose.material3` lines):

```kotlin
implementation(libs.androidx.compose.material3.adaptive)
implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
```

- [ ] **Step 3: Verify they resolve + compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If a library is unresolved (not in this BOM), pin an explicit version in the catalog: `adaptive` and the navigation-suite artifact both track the Compose Material3 Adaptive 1.x line — use the latest 1.x that resolves, then re-run.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(tablet): add Material 3 adaptive + navigation-suite deps

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Add an `embedded` flag to the five screens

Each screen renders its own top-bar navigation chrome (title, back arrow, Duplicates/Accounts icons). When hosted in a tablet pane, that chrome is redundant (the rail/pane owns it). Add `embedded: Boolean = false` and hide only the chrome; **everything else is unchanged** so phone behavior is identical (`embedded` defaults false).

**Files:** Modify `ui/home/HomeScreen.kt`, `ui/detail/DetailScreen.kt`, `ui/editor/EditorScreen.kt`, `ui/duplicates/DuplicatesScreen.kt`, `ui/accounts/AccountsScreen.kt`

**Interfaces (produced, used by Tasks 4–5):**
- `HomeScreen(onContactClick, onAddClick, onAccountsClick, onDuplicatesClick, pendingFilterAccountKey, onPendingFilterConsumed, embedded: Boolean = false, viewModel)`
- `DetailScreen(onBack, onEditRawContact, embedded: Boolean = false, viewModel)`
- `EditorScreen(onBack, embedded: Boolean = false, viewModel)`
- `DuplicatesScreen(onBack, embedded: Boolean = false, viewModel)`
- `AccountsScreen(onBack, onAccountClick, embedded: Boolean = false, viewModel)`

- [ ] **Step 1: HomeScreen** — add `embedded: Boolean = false` param. In the top bar, when NOT in selection mode, wrap the normal `TopAppBar` (the "Contacts" title + Duplicates/Accounts `IconButton`s) in `if (!embedded) { … }`. Keep the **selection-mode** top bar always (it's contextual to the list). The FAB, content, dialogs, and `BackHandler` stay unchanged.

- [ ] **Step 2: DetailScreen** — add `embedded: Boolean = false`. Wrap the back-arrow `TopAppBar` in `if (!embedded) { … }` (pass `topBar = { if (!embedded) TopAppBar(...) }`). The `LaunchedEffect` that calls `onBack()` on last-raw-delete stays (the pane host supplies `onBack`).

- [ ] **Step 3: EditorScreen** — add `embedded: Boolean = false`. When `embedded`, replace the Cancel/title/Save `TopAppBar` with a slim inline header row inside the content: a `Row` with the title (`editor_title_new`/`editor_title_edit`) + a `TextButton("Save")` (same `viewModel`/`onBack` wiring). When not embedded, keep the existing `TopAppBar`. The discard `BackHandler`/dialog stay.

- [ ] **Step 4: DuplicatesScreen** — add `embedded: Boolean = false`; wrap the back `TopAppBar` in `if (!embedded)`. Keep the `"Duplicates"` title only when not embedded (rail shows the label).

- [ ] **Step 5: AccountsScreen** — add `embedded: Boolean = false`; wrap the back `TopAppBar` in `if (!embedded)`.

- [ ] **Step 6: Compile (phone path unchanged)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Since `embedded` defaults to false, `AppNav` call sites are unchanged and the phone UI is identical.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui
git commit -m "refactor(ui): add embedded flag to screens for tablet panes

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Adaptive shell branch (compact passthrough)

Introduce `AdaptiveApp`: on compact it renders today's `MainNavGraph` (verbatim); on medium/expanded it renders a stub `TabletShell`. This proves the split without changing phone behavior.

**Files:** Modify `ui/AppNav.kt`; Create `ui/adaptive/AdaptiveApp.kt`

**Interfaces:**
- Consumes: `MainNavGraph()` (existing private composable in `AppNav.kt`) — make it `internal`/callable from `adaptive` package, or keep the branch inside `AppNav.kt` and call `TabletShell()` from there.
- Produces: `@Composable fun AdaptiveApp()`.

- [ ] **Step 1: In `AppNav.kt`, route through `AdaptiveApp`.** Change the `false -> MainNavGraph()` branch to `false -> AdaptiveApp()`. Keep `MainNavGraph` but make it `internal` (callable from the `adaptive` package). Add `import com.ryccoatika.contactmanager.ui.adaptive.AdaptiveApp`.

- [ ] **Step 2: Create `ui/adaptive/AdaptiveApp.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass
import com.ryccoatika.contactmanager.ui.MainNavGraph

/** Compact width keeps the phone NavHost; medium/expanded use the tablet shell. */
@Composable
fun AdaptiveApp() {
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    if (widthClass == WindowWidthSizeClass.COMPACT) {
        MainNavGraph()
    } else {
        TabletShell()
    }
}
```

- [ ] **Step 3: Create a stub `ui/adaptive/TabletShell.kt`** so it compiles (fleshed out in Task 4):

```kotlin
package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.runtime.Composable
import com.ryccoatika.contactmanager.ui.MainNavGraph

@Composable
fun TabletShell() {
    // Temporary: render the phone graph until Task 4 replaces this.
    MainNavGraph()
}
```

- [ ] **Step 4: Compile + run on phone**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL; the phone (compact) app is unchanged. (`MainNavGraph` must be `internal` and in package `com.ryccoatika.contactmanager.ui` so `import com.ryccoatika.contactmanager.ui.MainNavGraph` resolves.)

- [ ] **Step 5: Commit** (`feat(tablet): adaptive shell branch, compact passthrough`).

---

## Task 4: Navigation rail shell (Duplicates/Accounts panes)

Flesh out `TabletShell` with the rail + the two simple destinations. Contacts is stubbed to the single-pane `HomeScreen(embedded = true)` until Task 5.

**Files:** Modify `ui/adaptive/TabletShell.kt`

**Interfaces:**
- Consumes: `HomeScreen`, `DuplicatesScreen`, `AccountsScreen` (embedded flag, Task 2).
- Produces: `TabletShell()` rendering the rail; a `private enum class TabletDestination { CONTACTS, DUPLICATES, ACCOUNTS }`.

- [ ] **Step 1: Implement `TabletShell`**

```kotlin
package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.accounts.AccountsScreen
import com.ryccoatika.contactmanager.ui.duplicates.DuplicatesScreen

private enum class TabletDestination { CONTACTS, DUPLICATES, ACCOUNTS }

@Composable
fun TabletShell() {
    var dest by rememberSaveable { mutableStateOf(TabletDestination.CONTACTS) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = dest == TabletDestination.CONTACTS,
                onClick = { dest = TabletDestination.CONTACTS },
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                label = { Text(stringResource(R.string.home_title)) },
            )
            item(
                selected = dest == TabletDestination.DUPLICATES,
                onClick = { dest = TabletDestination.DUPLICATES },
                icon = { Icon(Icons.Default.Difference, contentDescription = null) },
                label = { Text(stringResource(R.string.duplicates_title)) },
            )
            item(
                selected = dest == TabletDestination.ACCOUNTS,
                onClick = { dest = TabletDestination.ACCOUNTS },
                icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null) },
                label = { Text(stringResource(R.string.accounts_title)) },
            )
        },
    ) {
        when (dest) {
            TabletDestination.CONTACTS -> ContactsTwoPane()
            TabletDestination.DUPLICATES -> CenteredPane { DuplicatesScreen(onBack = {}, embedded = true) }
            TabletDestination.ACCOUNTS -> CenteredPane { AccountsScreen(onBack = {}, onAccountClick = {}, embedded = true) }
        }
    }
}

/** Width-caps a single pane so it isn't stretched edge-to-edge on wide screens. */
@Composable
private fun CenteredPane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 8.dp)) { content() }
    }
}
```

- [ ] **Step 2: Temporary `ContactsTwoPane` stub** (same file or a new file) so it compiles — a single-pane Home for now:

```kotlin
@Composable
private fun ContactsTwoPane() {
    com.ryccoatika.contactmanager.ui.home.HomeScreen(
        onContactClick = {}, onAddClick = {}, onAccountsClick = {}, onDuplicatesClick = {},
        embedded = true,
    )
}
```
(Task 5 replaces this with the real two-pane and moves it to its own file.)

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `Icons.Default.People`/`Difference`/`ManageAccounts` differ, use the icons already imported in `HomeScreen.kt` (`Icons.Default.Difference`, `Icons.Default.ManageAccounts`).

- [ ] **Step 4: Device check (tablet)** — build+install, launch on the 10" emulator; confirm the rail shows Contacts/Duplicates/Accounts and each renders. Commit (`feat(tablet): navigation rail + Duplicates/Accounts panes`).

---

## Task 5: Contacts two-pane (list + nested detail NavHost)

Replace the `ContactsTwoPane` stub with the real split: the reused `HomeScreen` list on the left, and a nested `NavHost` on the right hosting the empty placeholder / detail / editor. The nested NavHost gives `DetailScreen`/`EditorScreen` their ids via nav args — no VM changes.

**Files:** Create `ui/adaptive/ContactsTwoPane.kt`; Modify `ui/adaptive/TabletShell.kt` (call it, remove the stub)

**Interfaces:**
- Consumes: `HomeScreen`, `DetailScreen`, `EditorScreen` (embedded), `Routes` (existing route strings/builders).

- [ ] **Step 1: Create `ui/adaptive/ContactsTwoPane.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.Routes
import com.ryccoatika.contactmanager.ui.common.ContactAvatar
import com.ryccoatika.contactmanager.ui.detail.DetailScreen
import com.ryccoatika.contactmanager.ui.editor.EditorScreen
import com.ryccoatika.contactmanager.ui.home.HomeScreen

private const val PANE_EMPTY = "pane_empty"

@Composable
fun ContactsTwoPane() {
    val detailNav = rememberNavController()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(360.dp)) {
            HomeScreen(
                onContactClick = { id ->
                    detailNav.navigate(Routes.detail(id)) {
                        popUpTo(PANE_EMPTY); launchSingleTop = true
                    }
                },
                onAddClick = { detailNav.navigate(Routes.EDITOR_NEW) { launchSingleTop = true } },
                onAccountsClick = {}, onDuplicatesClick = {},
                embedded = true,
            )
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = detailNav, startDestination = PANE_EMPTY) {
                composable(PANE_EMPTY) { EmptyDetail() }
                composable(
                    Routes.DETAIL,
                    arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
                ) {
                    DetailScreen(
                        onBack = { detailNav.navigate(PANE_EMPTY) { popUpTo(PANE_EMPTY) { inclusive = true } } },
                        onEditRawContact = { rawId -> detailNav.navigate(Routes.editorEdit(rawId)) { launchSingleTop = true } },
                        embedded = true,
                    )
                }
                composable(Routes.EDITOR_NEW) {
                    EditorScreen(onBack = { detailNav.popBackStack() }, embedded = true)
                }
                composable(
                    Routes.EDITOR_EDIT,
                    arguments = listOf(navArgument("rawContactId") { type = NavType.LongType }),
                ) {
                    EditorScreen(onBack = { detailNav.popBackStack() }, embedded = true)
                }
            }
        }
    }
}

/** Friendly placeholder shown in the detail pane before a contact is picked. */
@Composable
private fun EmptyDetail() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContactAvatar(name = "?", photoUri = null, size = 96.dp)
        Text(
            stringResource(R.string.tablet_select_contact),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
```

- [ ] **Step 2: Add the string** to `app/src/main/res/values/strings_common.xml`:

```xml
<string name="tablet_select_contact">Select a contact to see details</string>
```

- [ ] **Step 3: In `TabletShell.kt`, remove the `ContactsTwoPane` stub** and call the new top-level `ContactsTwoPane()` (delete the private stub; the `when` branch already calls `ContactsTwoPane()`).

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `Routes.detail` / `Routes.editorEdit` / `Routes.EDITOR_NEW` names differ, use the exact members from `AppNav.kt` (`Routes.detail(id)`, `Routes.editorEdit(rawId)`, `Routes.EDITOR_NEW`, `Routes.DETAIL`, `Routes.EDITOR_EDIT`).

- [ ] **Step 5: Full unit suite** (unchanged, must be green)

Run: `./gradlew :app:testDebugUnitTest`
Expected: 164 tests pass.

- [ ] **Step 6: Commit** (`feat(tablet): Contacts list-detail two-pane with editor in detail pane`).

---

## Task 6: Previews, polish, device verification

**Files:** Modify `ui/adaptive/*.kt` (add previews); device check.

- [ ] **Step 1: Add `@Preview`s** at expanded width to `TabletShell.kt` and `ContactsTwoPane.kt`:

```kotlin
@Preview(name = "Tablet shell", widthDp = 1280, heightDp = 800)
@Preview(name = "Tablet shell · dark", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TabletShellPreview() { ContactManagerTheme { TabletShell() } }
```
(Previews may show empty data without Hilt VMs — that's fine; they verify layout/compile. If a preview crashes on `hiltViewModel()`, guard the preview to render `EmptyDetail()` + a static list stub instead.)

- [ ] **Step 2: Device verify — 10" tablet.** Build+install; on `Pixel_Tablet` (or the seeded emulator): confirm rail (Contacts/Duplicates/Accounts), list on the left, tapping a contact fills the detail pane, the add FAB opens the editor in the detail pane, Save returns to the contact, Duplicates/Accounts render centered. Use the seeded dummy data (re-seed with the earlier adb script if the emulator is empty).

- [ ] **Step 3: Device verify — 7" tablet + phone regression.** Repeat on a 7" AVD (medium width) — confirm the two panes fit acceptably. Boot a phone AVD and confirm the compact app is unchanged (rail absent, current single-pane nav).

- [ ] **Step 4: Rotation.** Rotate the tablet — layout persists (rail + panes), selected destination survives (`rememberSaveable`).

- [ ] **Step 5: Commit** (`feat(tablet): previews + adaptive-layout polish`).

---

## Self-Review (done)

- **Spec coverage:** rail (T4) ✓; Contacts list-detail two-pane (T5) ✓; editor in detail pane (T5 nested NavHost editor routes) ✓; Duplicates/Accounts centered single pane (T4 `CenteredPane`) ✓; compact unchanged (T3 branch) ✓; reuse via `embedded` flag, no VM changes (T2) ✓; deps (T1) ✓; previews + device verify (T6) ✓.
- **Type consistency:** `embedded: Boolean = false` added to all five screens (T2) and used at every pane call site (T4/T5); `TabletDestination` enum used only in `TabletShell`; `Routes.*` members match `AppNav.kt`.
- **Open risks flagged:** (a) adaptive artifact coordinates/versions vs the BOM — T1 Step 3 has a fallback. (b) `MainNavGraph` must be made `internal` and stay in package `…ui` for `AdaptiveApp` to call it — T3 Step 1/4. (c) previews without Hilt may need a static guard — T6 Step 1. (d) two panes on 7" portrait (medium) may be tight — acceptable for v1, noted in the spec.
