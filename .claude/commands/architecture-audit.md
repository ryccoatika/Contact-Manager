---
description: Run the mechanical architecture rules (layering, seams, budgets) against docs/ARCHITECTURE.md and report ratchet status
argument-hint: "[optional: 'fix' to also fix the easiest new violations]"
---

Audit the codebase against the architecture contract:

1. Read `docs/ARCHITECTURE.md` (the rules R1–R14 and the migration plan).
2. Run `python3 scripts/architecture-audit.py`.
3. Interpret the result:
   - **New violations** → these block. For each, name the rule, the file, and
     the concrete fix per the contract. **Never** add entries to
     `scripts/architecture-baseline.txt` to make it pass — the baseline only
     shrinks.
   - **Stale baseline entries** → debt that got fixed; delete exactly those
     lines from the baseline file and celebrate the ratchet progress.
   - **Baselined count** → report which migration steps (docs/ARCHITECTURE.md
     §Migration) the remaining entries belong to, and which step is the
     smallest next win.
4. Beyond the script, spot-check what greps can't see:
   - fully-qualified references used to dodge import rules;
   - new duplication between ViewModels (the move-all flow was duplicated
     once — grep for freshly copied blocks);
   - screens added to `AppNav.kt` but not the tablet shells, or vice versa
     (navigation is registered in both composition roots);
   - split-tripwire status (docs/ARCHITECTURE.md §Split tripwires): measure
     any that plausibly moved (file counts, line counts, committer count).
5. If the argument is `fix`, fix new violations (and any one baselined item
   that is a quick, safe win), rerun the script, run
   `./gradlew compileDebugKotlin :app:testDebugUnitTest spotlessCheck`, and
   update the baseline by deleting the fixed lines.

Report: ratchet status (new/baselined/stale), findings table, and the single
most valuable next migration step.

Mode: **$ARGUMENTS**
