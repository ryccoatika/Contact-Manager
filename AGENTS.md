# AGENTS.md

Guide for AI agents (Claude Code, Codex, Cursor, Gemini, …) working in this repo.
Read this first, then `README.md` for the deep architecture/device caveats.
`CLAUDE.md` imports this file.

## What this is

A native **Android** contact manager (Kotlin, Jetpack Compose, MVVM + Hilt) that
surfaces every contact source on the device — Google, Samsung, device-local, SIM,
and app-managed accounts (WhatsApp/Telegram/…) — in one list, and moves/merges/
dedupes across them. It talks **directly to `ContactsContract`** — there is no
cache database, no backend.

## Golden rules

1. **All provider I/O stays off the main thread** — repositories already force
   `Dispatchers.IO`. Never add blocking `ContentResolver` calls in a composable
   or ViewModel body.
2. **Pure logic is unit-tested; keep it green.** `domain/` (classification,
   matching, planning, validation) and the data helpers have JVM tests. After any
   change: `./gradlew :app:testDebugUnitTest` must pass (146 tests).
3. **UI-only changes must not touch ViewModels/domain/data.** A restyle is a
   presentation-layer edit. If a "styling" change needs a ViewModel edit, stop and
   reconsider.
4. **Respect account capabilities.** Every write path checks
   `AccountCapability` (`FULL_CRUD` / `READ_ONLY` / `SIM`). Read-only accounts
   (WhatsApp etc.) are never written or deleted — they link/merge-by-copy only.
5. **Wrap every provider call.** OEM `icc/adn` and `applyBatch` behavior varies;
   errors surface as snackbars via typed results, never crashes. Don't remove the
   try/catch envelopes.

## Build · test · verify

```bash
./gradlew compileDebugKotlin        # fast compile check (use after every edit)
./gradlew :app:testDebugUnitTest    # 146 unit tests
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:lintDebug            # Android lint
```

- JDK 17+ (repo currently builds on 21). Android SDK 36. minSdk 24, targetSdk 36.
- Compose BOM `2026.02.01`, Kotlin `2.2.10`, Hilt `2.60.1`, KSP. Deps live in
  `gradle/libs.versions.toml` (version catalog) — add libraries there, not inline.
- **Compile after every non-trivial edit** — Compose errors are cheap to catch and
  the whole module compiles in seconds.

### Running it on a device (on demand only)

Device verification is **not automatic** — compile + unit tests are the default
gate, even for UI changes. Run the app on a device only when the user asks, or
when they invoke the **`/verify-ui`** command (`.claude/commands/verify-ui.md`),
which drives the build → install → launch → screenshot loop (see the
`run-on-device` skill, `.claude/skills/run-on-device/SKILL.md`). Manual version:

```bash
./gradlew :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g grants runtime perms
adb shell monkey -p com.ryccoatika.contactmanager -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > shot.png
```

## Where things live

See `README.md` for the full package map. Orientation:

```
ui/theme/    Design system (see below)
ui/common/   Shared composables + AccountVisuals + permission gates
ui/<screen>/ home | detail | editor | accounts | duplicates — each: Screen + ViewModel
domain/      Pure logic: AccountClassifier, MovePlanner, DuplicateFinder, SimContactValidator
data/        Repositories over ContactsContract; BatchOperationManager; DataStore prefs
data/sim/    SIM sources, capability probe/cache, subscriptions, pseudo-account merge
di/          Hilt modules
```

Each screen file keeps its private composables local; shared UI is promoted to
`ui/common`. Screens read state from a Hilt `ViewModel` via
`collectAsStateWithLifecycle`.

## Design system — "Porcelain & Pine"

Defined in `ui/theme/`. **Always style through tokens; never hard-code hex.**
Full rationale in `docs/superpowers/specs/2026-07-30-premium-redesign-design.md`.

- **`Color.kt`** — the palette (light + dark), incl. the M3 `surfaceContainer`
  ramp. If you need a new color, add it here and wire it into the scheme in
  `Theme.kt`; don't inline `Color(0x…)` in screens.
- **`Theme.kt`** — `ContactManagerTheme`. Signature: pine `#0E5A51` on porcelain
  `#F4F6F3`. **Dynamic color (Monet) defaults OFF** so the brand identity is
  consistent; it's opt-in via `dynamicColor = true`.
- **`Type.kt`** — Manrope (bundled variable TTF, `res/font/manrope.ttf`) via
  `AppFontFamily`. Use `TabularNums` (`fontFeatureSettings = "tnum"`) for phone
  numbers, counts, and codes so digits align.
- **`Shape.kt`** — `AppShapes`: cards `medium` (20dp), sheets `large` (28dp).
- **`ExtendedColors.kt`** — `MaterialTheme.extendedColors.brass` (accent, used
  **sparingly**: SIM tags, duplicate match reason). `AvatarPalette.brushFor(name)`
  gives the deterministic gradient for identity avatars.

Shared components (`ui/common/`):

- `ContactUi.kt`: `ContactAvatar` (gradient monogram / photo), `SelectedAvatar`,
  `AccountDot`, `CapabilityTag`, `SectionCard`, `QuickActionPill`, `SearchField`.
- `Shimmer.kt`: `ShimmerBox`, `ContactListSkeleton`, `CardsSkeleton`,
  `DetailSkeleton`. **Loading states use shimmer skeletons, not spinners** (the
  small in-button save spinner is the one exception).
- `AccountVisuals`: `color(type,name)` (muted provenance dot palette) and
  `label(type,name)` (human account name). Change colors only in the `palette`
  list; keep `label()` logic intact.

UI conventions:

- Top bars set `containerColor = background` so they blend into porcelain; the
  selection top bar uses `primaryContainer`.
- Add a `@Preview` (light **and** dark, `uiMode = UI_MODE_NIGHT_YES`) for new
  screen composables so they're verifiable without a device.
- Keep every `contentDescription` and the 48dp min touch targets.

## Conventions

- Kotlin + Compose idioms; match the surrounding file's style, import ordering,
  and comment density. Prefer small, focused composables.
- No new dependencies without a reason; add via `libs.versions.toml`.
- Don't leave unused imports — this repo keeps lint clean.

## Commits & PRs

- **Conventional Commits**: `feat(ui): …`, `fix(ui): …`, `chore: …`, `docs: …`.
  Subject in the imperative, ≤ ~72 chars; body explains the *why* when non-obvious.
- Branch off `main`; don't commit straight to `main`.
- When an agent authors a commit, add the trailer the tool requires (Claude Code:
  `Co-Authored-By: …`). PR bodies get the generated-with trailer.

## Planning workflow

Non-trivial features go **spec → plan → implement**, saved under
`docs/superpowers/`:

- `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` — the approved design.
- `docs/superpowers/plans/YYYY-MM-DD-<topic>.md` — bite-sized task plan.

The premium-redesign spec + plan are the current reference examples.

## Gotchas (read before editing these areas)

- **SIM** stores name + one number only; the editor enforces name length +
  single number when the target is SIM. `SimAwareContactsWriter` routes CRUD
  between `ContactsContract` and the `icc/adn` provider transparently — don't
  bypass it.
- **`READ_PHONE_STATE`** is optional, prompted lazily, and only labels dual-SIM
  subscriptions. Don't make it required.
- **Capability probe** is cached in DataStore and can go stale after a SIM swap;
  Accounts screen pull-to-refresh re-probes.
- **minSdk 24**: the Manrope variable-font `wght` axis needs API 26; below that it
  falls back to the default instance — expected, don't "fix".
- **Dynamic-color flip**: turning `dynamicColor` back on changes the whole look on
  Android 12+ — only do it deliberately.
