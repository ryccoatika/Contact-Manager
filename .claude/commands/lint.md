---
description: Format with Spotless/ktlint, then gate on spotlessCheck + Android lint
argument-hint: "[optional: 'check' to only verify without reformatting]"
---

Run the lint pipeline:

1. Unless the argument is `check`, run `./gradlew spotlessApply` first.
2. `./gradlew spotlessCheck` — must pass.
3. `./gradlew :app:lintDebug` — must pass; read the report at
   `app/build/reports/lint-results-debug.html` (or the `.txt` sibling) if it fails.
4. If ktlint reports errors it cannot auto-fix (naming, wildcard imports),
   fix them properly in the source — never suppress with `@Suppress` or
   `suppressLintsFor` without asking the user first.
5. If `spotlessApply` changed files, show `git diff --stat` so the user sees the
   scope of the reformat before anything is committed. Formatting-only changes
   are committed separately from behavior changes (`style:` prefix).

Rule config lives in `.editorconfig` (ktlint) — adjust rules there, not in
`build.gradle.kts`.

Mode: **$ARGUMENTS**
