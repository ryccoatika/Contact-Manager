# Architecture

The architecture contract for Contact Manager. `AGENTS.md` covers day-to-day
conventions; this file owns structure: layers, dependency rules, enforcement,
and the migration plan. Decided 2026-09-05 after a full codebase audit and a
judged comparison of three candidate architectures (single-module fenced
monolith vs. split-ready package layout vs. Now-in-Android-style multi-module).

## Decision: the fenced monolith

**One Gradle module (`:app`), three strictly layered packages, rules enforced
by script — not by discipline.**

Why not multi-module: a solo developer with second-level incremental compiles,
a working configuration cache, and an AGP 9 build that has already killed two
Gradle plugins (oss-licenses, AboutLibraries — both rely on removed variant APIs)
gains little from compiler-walled module boundaries and pays for them on every
AGP major bump and every new build script. The audit showed the layering is
already ~90 % clean — the gap is enforcement, not structure. Module extraction
stays available and gets cheaper the closer the packages track the rules below;
the **split tripwires** at the bottom define exactly when to revisit.

## Layers

```
com.ryccoatika.contactmanager/
├── ContactManagerApp.kt, MainActivity.kt      entry points
├── domain/            pure Kotlin: models + logic (classify, match, plan,
│   └── model/         validate). Zero android/androidx/data/ui/di imports.
├── data/              Android adapters: ContactsContract, icc/adn SIM stack,
│   ├── sim/           DataStore prefs, Play Billing, Firebase analytics,
│   ├── billing/       batch operations. Imports domain; NEVER ui.
│   └── analytics/     Typed results (ContactOpResult); guarded provider calls.
├── di/                Hilt modules — the only place impls are named and bound.
└── ui/                Compose presentation.
    ├── <feature>/     home | detail | editor | accounts | duplicates |
    │                  settings | support | about | onboarding | crash
    │                  — each: Screen(s) + ViewModel, self-contained
    │                  (about is stateless; crash is a bare Activity by design).
    ├── common/        shared composables + AccountVisuals
    ├── theme/         Porcelain & Pine design system (imports nothing local)
    ├── analytics/     LocalAnalytics + TrackScreenView
    ├── permission/    permission gates
    ├── review/ update/  Play in-app review / update helpers
    ├── adaptive/      tablet shells — composition root (may import features)
    └── AppNav.kt      phone nav graph — composition root (may import features)
```

Dependency direction: **ui → domain ← data**, with `di/` composing everything
and `ui/AppNav.kt` + `ui/adaptive/` as the only cross-feature composition
points. ViewModels are the ui↔data seam; composables see domain types only.

There is deliberately **no use-case layer**. Logic shared by two ViewModels is
extracted into `data/ops/` (orchestration over repositories) or `domain/`
(pure) — created on the second occurrence, never speculatively.

## Rules (enforced by `scripts/architecture-audit.py`)

Run via `/architecture-audit` or `python3 scripts/architecture-audit.py`.
CI runs it on every PR (`check.yml`). Pre-existing debt lives in
`scripts/architecture-baseline.txt`, which may **only shrink** — new
violations are fixed, never baselined.

| Rule | Contract |
|------|----------|
| R1  | `domain/` imports no android/androidx/data/ui/di — pure Kotlin, JVM-testable |
| R2  | `data/` and `di/` never import `ui` |
| R3  | ui files that are not `*ViewModel.kt` never import `data.` — composables consume domain types; the ViewModel is the data seam |
| R4  | No feature package imports another feature package; only `ui/AppNav.kt` and `ui/adaptive/` compose features (the feature list is derived from `ls ui/` at audit time) |
| R5  | Every project-local ViewModel constructor dependency is an `interface` (fakeable seam); concrete injectables are findings |
| R6  | No Kotlin file over 500 lines (treat failures as decomposition prompts, not mechanical-split mandates) |
| R7  | `Color(0x…)` only under `ui/theme/` (+ the sanctioned `AccountVisuals` provenance palette) |
| R8  | `Dispatchers.*` referenced only in `di/` — everywhere else dispatchers are injected qualifiers |
| R9  | `ContactsContract`/`ContentResolver`/`content://icc` only under `data/` (di may wire the resolver) |
| R10 | One event convention: no `SharedFlow<String>` in ui — ViewModels emit typed events |
| R11 | `ui/theme/` imports nothing project-local (the design system stays liftable) |
| R12 | Exactly one `Context.findActivity()` helper |
| R13 | OEM local-account-type strings live only in `domain/` (one source of truth) |
| R14 | No `observeContacts()/observeAccounts()` + `.first()` snapshots — one-shot reads use dedicated suspend `snapshot()` APIs |

Known limitations: the checks match import lines and simple patterns — they
can be evaded with fully-qualified names (don't; reviewers and the
`architecture-reviewer` agent treat evasion as a violation). R9 matches the
`contentResolver` property and `ContactsContract`/`content://icc` tokens; R13
uses `vnd.sec.contact.phone` as the sentinel for the whole OEM table. The
ratchet is machine-enforced on PRs: `check.yml` rejects any baseline edit that
is not a pure deletion (set-subset check), and the audit script rejects
malformed baseline lines.

## Migration plan (current baseline → zero)

Each step ships alone, app releasable after every one, compile + all unit
tests + `spotlessCheck` green in between. Mechanical-move commits get added to
`.git-blame-ignore-revs`.

1. **Kill the cross-feature edge + its duplication together** — move
   `PendingMoveAll` to `domain/model/`; extract the duplicated move-all flow
   (Home + Accounts ViewModels) into `data/ops/MoveAllContacts.kt`; both VMs
   consume it. Clears R4 + the biggest duplication.
2. **Interface the three concrete injectables** — `BatchRunner` ←
   BatchOperationManager, `SimStore` ← SimRepository, `Billing` ←
   BillingManager (+ `FakeBilling` and the missing SupportViewModel test).
   Keep the interfaces minimal — no speculative methods. Clears R5.
3. **Pure-type moves** — `Analytics`, `AnalyticsEvent`, **and the pure
   `NoOpAnalytics`** → `domain/analytics/` (the Firebase impl stays in data);
   `SimRouting` → `domain/sim/`; `BillingEvent` → `domain/` (or the ViewModel
   maps it to a UI event); re-key `AccountVisuals` on plain account-type
   strings. Single-source the OEM account-type table in
   `domain/AccountClassifier` (including the `AccountVisuals` usage).
   `StringProvider` stays in `data/` — the data layer resolves SIM error
   messages through it, and ViewModels importing data interfaces is the
   sanctioned seam (R3 exempts `*ViewModel.kt`). Clears R3 + R13.
4. **Dedupe small helpers** — one `findActivity()` in `ui/common/ActivityExt.kt`;
   one `contentChangesFlow(uri)` in `data/ContentChanges.kt`. Clears R12.
5. **Split the over-budget files** — `HomeScreen.kt` along its existing
   seams (ContactRow + swipe, chips row, sheets, dialogs, gestures, top bars);
   promote the existing `AlphabetRail` and `EmptyState`, and *extract new*
   shared `ConfirmDialog` + `AccountPickerSheet` composables (today five inline
   AlertDialogs and three hand-built sheets) into `ui/common/`. Then
   `EditorScreen.kt` (extract the form field kit) and
   `data/ContactsWriteRepository.kt` (split the batch-op builders into
   focused files under `data/`). Clears R6.
6. **Unify events** — sealed `UiEvent` + a shared snackbar collector in
   `ui/common/`; migrate Home/Accounts/Duplicates VMs off
   `SharedFlow<String>`. Clears R10.
7. **Hot read flows, shipped last and alone** — `shareIn(appScope,
   WhileSubscribed(5s), replay = 1)` inside the read repositories + suspend
   `snapshot()` one-shots; migrate all `.first()` callers. This is the only
   step that changes runtime semantics (staleness across permission grants /
   SIM swaps) — its own PR, device-tested, independently revertible.
   Clears R14.

After step 7 the baseline file is empty. Delete nothing from the audit script.
(Every baseline entry maps to a step: R4/dup → 1, R5 → 2, R3/R13 → 3,
R12 → 4, R6 → 5, R10 → 6, R14 → 7.)

## Split tripwires — when to revisit modularization

Escalate a boundary to a real Gradle module only when one of these fires
(measure, don't vibe):

- **T1 build pain**: clean `:app:compileDebugKotlin` > 120 s or warm
  incremental > 30 s (`gradlew --profile`).
- **T2 team growth**: ≥ 2 distinct human committers in 90 days
  (`git shortlog -sn --since='90 days'`).
- **T3 second consumer**: a Wear/widget/second-APK target, or a flavor that
  must strip Firebase/Billing (e.g. F-Droid).
- **T4 convention failure**: the same audit rule fails in ≥ 3 separate PRs
  within 90 days — that specific boundary has proven grep-unenforceable;
  promote exactly it, not the whole tree.
- **T5 sheer size**: main source > 30 k Kotlin lines or > 15 feature packages.

When one fires, extract in this order: `core:model`+`core:domain` (pure JVM),
then `core:designsystem`, then `core:data`, then features smallest-first — the
package layout above is already shaped so each extraction is `git mv` plus a
build-script stub.

## Accepted residuals (watched, not fixed)

- Navigation is registered twice (AppNav + tablet shells). Both are sanctioned
  composition roots; a screen wired into one but not the other still compiles.
  Mitigation: reviewers check both when routes change. Escalate via T4.
- ViewModels of 300–400 lines with 7–9 deps are within tolerance; extraction
  into `data/ops/` happens on duplication, not size.
