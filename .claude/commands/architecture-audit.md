---
description: Check the architecture rules (layering, seams, budgets) from docs/ARCHITECTURE.md and report violations
argument-hint: "[optional: 'fix' to also fix the easiest violations]"
---

Audit the codebase against the architecture contract in `docs/ARCHITECTURE.md`
(rules R1–R14; known debt is zero). There is no checked-in script — perform
the checks yourself with grep/find over
`app/src/main/java/com/ryccoatika/contactmanager/`:

1. **R1 domain purity** — `grep -rE '^import (android|androidx)\.' domain/` and
   project imports of `data|ui|di` in `domain/` → must be empty.
2. **R2** — `grep -rn 'contactmanager\.ui' data/ di/` (imports only) → empty.
3. **R3** — ui files **not** named `*ViewModel.kt` importing
   `contactmanager.data.` → composables must consume domain types.
4. **R4 cross-feature** — for each feature dir under `ui/` (everything except
   common/theme/analytics/review/update/permission/adaptive), no import of
   another feature's package; only `ui/AppNav.kt` and `ui/adaptive/` may.
5. **R5 seams** — every project-local type in a ViewModel constructor is an
   `interface` (check declarations); concrete injectables are findings.
6. **R6 file budget** — `find app/src/main -name '*.kt' | xargs wc -l`:
   target ≤ 500 lines (warn), **750 hard cap** (violation).
7. **R7** — `Color(0x` only under `ui/theme/` + `ui/common/AccountVisuals.kt`.
8. **R8** — `Dispatchers\.(IO|Default|Main)` only under `di/` (ignore comments).
9. **R9** — `ContactsContract|contentResolver|content://icc` only under
   `data/` (di may wire the resolver).
10. **R10** — `SharedFlow<String>` in ui/ → typed events only.
11. **R11** — `ui/theme/` imports nothing project-local (own package + `R` ok).
12. **R12** — exactly one `Context.findActivity` definition.
13. **R13** — `vnd.sec.contact.phone` (OEM-table sentinel) only in `domain/`.
14. **R14** — no `observeContacts()/observeAccounts()` followed by
    `.first(...)` snapshots.

Then:
- The known-debt list is empty (migration completed 2026-09-07), so **every
  finding is a new violation — it blocks**; report rule, file, and the
  concrete fix. Never start a debt list to make work pass.
- Spot-check what greps can't see: fully-qualified-name evasions, freshly
  duplicated ViewModel flows, screens wired into `AppNav.kt` but not the
  tablet shells (or vice versa), and the split tripwires (§Split tripwires).
- If the argument is `fix`, fix the violations,
  then run `./gradlew compileDebugKotlin :app:testDebugUnitTest spotlessCheck`.

Report: findings table (rule, file, fix) — or a clean bill of health.

Mode: **$ARGUMENTS**
