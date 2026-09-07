---
description: Audit Compose/runtime performance — recomposition, list scroll, startup, provider I/O, APK size
argument-hint: "[optional focus, e.g. 'home list scroll' or 'startup']"
---

Run a performance audit. Report findings as `impact — file:line — issue — fix`,
verified against the actual code. Known past regressions in this repo (fixed
once, don't let them return): per-row shared-element registration, per-row
`Crossfade`, per-delta coroutine launches in drag handlers, always-on
`basicMarquee(repeatDelayMillis = 0)`.

1. **Composition costs in lists** (`ui/home`, `ui/duplicates`, `ui/accounts`)
   - Any per-item `remember` of heavy objects, per-item `Transition`/`Crossfade`/
     `AnimatedContent`, or per-item `rememberSwipeToDismissBoxState`-class state?
   - Work inside item lambdas that belongs in the ViewModel (`flatMap`, `distinctBy`,
     sorting, string building)? UI models should arrive precomputed (`rowExtras` pattern).
   - `items(key = ...)` present on every LazyColumn/LazyRow? `derivedStateOf` used
     for values read during scroll?
   - Unstable lambda captures causing whole-list recomposition (lambdas capturing
     `state` object instead of stable values)?

2. **Infinite/always-running animations** — grep `basicMarquee|rememberInfiniteTransition|animateFloat` and confirm each either pauses (`repeatDelayMillis > 0`) or is only composed when visible.

3. **Main-thread I/O**
   - `grep -rn "contentResolver\.\|appPrefsDataStore\|dataStore" app/src/main/java/ | grep -v "Dispatchers.IO\|ioDispatcher"` then verify each call site's dispatcher by reading the enclosing flow/`withContext`.
   - Any `runBlocking` or `.first()` on the main thread? Any provider call reachable
     from a composable body or ViewModel init without `flowOn(io)`?

4. **Startup**
   - `ContactManagerApp.onCreate` + `MainActivity.onCreate`: everything there must be
     cheap (Firebase guard, handler install, splash). Nothing synchronous on
     DataStore/provider before first frame.
   - Baseline profile absent — note as an improvement, not a bug.

5. **Navigation & transitions** — screens gated behind `SharedTransitionLayout`
   pay a lookahead pass; heavy new screens should defer expensive content until
   the enter transition settles (editor's `entering` gate is the pattern).

6. **Build/runtime footprint**
   - `./gradlew :app:assembleRelease` then report APK size
     (`ls -lh app/build/outputs/apk/release/`); flag jumps vs the last known size.
   - R8 + resource shrinking on; no `debugImplementation` leaking into release.

7. **Measure, don't guess** (when a device is attached and the finding warrants it)
   - Build release, install, and use `adb shell dumpsys gfxinfo com.ryccoatika.contactmanager`
     after scrolling Home to capture jank stats; compare P90/P99 frame times before/after
     a proposed fix.

Finish with a ranked list (highest user-visible impact first) and, for each, the
concrete fix and its risk.

Focus (if given): **$ARGUMENTS**
