# AGENTS.md

Guide for AI agents (Claude Code, Codex, Cursor, Gemini, …) working in this repo.
Read this first; `docs/ARCHITECTURE.md` is the structure contract; `README.md`
has the deep device caveats. `CLAUDE.md` imports this file.

## What this is

A native **Android** contact manager (Kotlin, Jetpack Compose, MVVM + Hilt) that
surfaces every contact source on the device — Google, Samsung, device-local, SIM,
and app-managed accounts (WhatsApp/Telegram/…) — in one list, and moves/merges/
dedupes across them. It talks **directly to `ContactsContract`** — there is no
cache database, no backend. Ships on Google Play; contacts never leave the
device (the privacy policy promises this — treat violations as critical).

## Golden rules

1. **All provider I/O stays off the main thread** — repositories already force
   injected `Dispatchers.IO`. Never add blocking `ContentResolver` calls in a
   composable or ViewModel body.
2. **Pure logic is unit-tested; keep it green.** `domain/` (classification,
   matching, planning, validation) and the data helpers have JVM tests. After
   any change: `./gradlew :app:testDebugUnitTest` must pass — all of it (never
   trust a hard-coded test count; they drift).
3. **UI-only changes must not touch ViewModels/domain/data.** A restyle is a
   presentation-layer edit. If a "styling" change needs a ViewModel edit, stop
   and reconsider.
4. **Respect account capabilities.** Every write path checks
   `AccountCapability` (`FULL_CRUD` / `READ_ONLY` / `SIM`). Read-only accounts
   (WhatsApp etc.) are never written or deleted — they link/merge-by-copy only.
5. **Wrap every provider call.** OEM `icc/adn` and `applyBatch` behavior varies;
   errors surface as snackbars via typed results (`ContactOpResult`), never
   crashes. Don't remove the try/catch envelopes — including the `runCatching`
   around every `registerContentObserver` (revoked-permission crash class).
6. **Follow the architecture rules.** `docs/ARCHITECTURE.md` defines the layer
   fences (ui → domain ← data) and rules R1–R14, checked via the
   `/architecture-audit` command and enforced in review. Known debt is zero —
   any violation is new and must be fixed, not baselined.

## Build · test · verify

```bash
./gradlew compileDebugKotlin        # fast compile check (after every edit)
./gradlew :app:testDebugUnitTest    # all JVM unit tests
./gradlew spotlessApply             # format (ktlint via Spotless)
./gradlew spotlessCheck             # format gate
./gradlew :app:lintDebug            # Android lint (0 errors enforced)
./gradlew :app:assembleDebug        # debug APK
```

CI (`.github/workflows/check.yml`) runs spotless + compile + tests + lint on
every PR. `deploy.yml` publishes (develop → Internal App Sharing,
main → closed alpha + GitHub release from `CHANGELOG.md`).

- JDK 17+ (repo builds on 21). Android SDK 36. minSdk 24, targetSdk 36.
- Compose BOM `2026.02.01`, Kotlin `2.2.10`, Hilt `2.60.1`, AGP `9.x`, KSP.
  Dependencies live in `gradle/libs.versions.toml` — add there, never inline.
- **AGP 9 warning**: two Gradle plugins have already died on AGP 9's removed
  variant APIs (oss-licenses, AboutLibraries — unusable). Verify any new plugin
  actually applies before building features on it. Separately, google-services
  + crashlytics plugins are applied conditionally on
  `release/google-services.json` because Firebase is optional at build time —
  and the Crashlytics plugin is mandatory whenever the SDK ships (its absence
  is a startup crash, not a no-op).
- **Kotlin warnings are errors** (`allWarningsAsErrors` in app/build.gradle.kts):
  fix deprecations properly — never `@Suppress` them to get green without asking.
- ktlint rules are tuned in `.editorconfig`; formatting-only changes are
  committed separately (`style:`), and mechanical commits go in
  `.git-blame-ignore-revs`.

### Running it on a device (on demand only)

Device verification is **not automatic** — compile + unit tests are the default
gate, even for UI changes. Run on a device only when asked, or via the
**`/verify-ui`** command (which drives the `run-on-device` skill). Manual:

```bash
./gradlew :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g grants runtime perms
adb shell monkey -p com.ryccoatika.contactmanager -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > shot.png
```

## Where things live

```
ContactManagerApp.kt   entry: Crashlytics guard + global crash handler
MainActivity.kt        splash, theme-mode wiring, in-app-update snackbar host
domain/                pure Kotlin: AccountClassifier, MovePlanner,
  └ model/             DuplicateFinder, SimContactValidator; Contact,
                       ContactAccount, AccountCapability, ThemeMode, …
data/                  ContactsContract repositories (reads + decorated writer),
  ├ sim/               icc/adn SIM sources, capability probe/cache, subscriptions
  ├ billing/           Play Billing donations (Support screen)
  └ analytics/         Analytics interface, AnalyticsEvent, Firebase/NoOp impls
di/                    Hilt modules (the only place impls are named/bound)
ui/
  ├ home | detail | editor | accounts | duplicates |     feature packages:
  │ settings | support | about | onboarding | crash      Screen + ViewModel
  │   (about is stateless; crash is a bare Activity — no VM by design)
  ├ common/            shared composables, AccountVisuals, PhonePermissionPrompt
  ├ theme/             design system (see below) — imports nothing project-local
  ├ analytics/         LocalAnalytics + TrackScreenView (screen-view logging)
  ├ permission/        PermissionGate (contacts permission rationale flow)
  ├ review/ update/    Play in-app review + in-app update helpers
  ├ adaptive/          tablet rail + two-pane shells (composition root)
  └ AppNav.kt          phone nav graph + transitions (composition root)
```

Feature packages never import each other; only `AppNav.kt` and `ui/adaptive/`
compose features. Shared UI used by 2+ features gets promoted to `ui/common/`.
Full rules + migration plan: `docs/ARCHITECTURE.md`.

## Design system — "Porcelain & Pine"

Defined in `ui/theme/`. **Always style through tokens; never hard-code hex.**
Full rationale in `docs/superpowers/specs/2026-07-30-premium-redesign-design.md`.

- **`Color.kt`** — palette (light + dark), incl. the M3 `surfaceContainer`
  ramp. New colors go here and into `Theme.kt`; never `Color(0x…)` in screens.
  `res/values*/colors.xml` mirrors the ground colors for splash/window — keep
  them in sync when the palette changes.
- **`Theme.kt`** — `ContactManagerTheme`. Pine `#0E5A51` on porcelain `#F4F6F3`.
  **Dynamic color defaults OFF.** The user's Light/Dark/System choice
  (`AppPrefs.themeMode`) is applied in `MainActivity` — the splash can only
  follow the *system* theme (drawn before the process starts).
- **`Type.kt`** — Manrope (bundled variable TTF). Use `TabularNums` for phone
  numbers, counts, codes.
- **`Shape.kt`** — cards `medium` (20dp), sheets `large` (28dp).
- **`ExtendedColors.kt`** — `extendedColors.brass` accent (sparingly: SIM tags,
  match reasons). `AvatarPalette.brushFor(name)` for identity avatars.

UI conventions:

- Loading = shimmer skeletons (`ui/common/Shimmer.kt`), never spinners (sole
  exception: in-button save spinner).
- Top bars: `containerColor = background`; selection bars `primaryContainer`.
- Screens take `embedded: Boolean = false` to hide their top bar in tablet panes.
- `@Preview` light **and** dark (`uiMode = UI_MODE_NIGHT_YES`) for new screens.
- Every `contentDescription`, 48dp min touch targets; wrap full-screen content
  in a themed `Surface` (bare `Text` outside one is invisible in dark mode).
- New routes must be wired in **both** `AppNav.kt` and the tablet shells —
  navigation is registered twice by design; missing one still compiles.

## Conventions

- Kotlin + Compose idioms; match the surrounding file's style and comment
  density. Prefer small, focused composables. **File budget: target ≤ 500
  lines; 750 is the hard cap** (architecture-audit enforced), reserved for
  files that genuinely cannot be split — decompose before you get there.
- **Strings**: everything user-visible in `res/values/strings_<screen>.xml`;
  plurals for counts; ViewModels use `StringProvider`. No hardcoded text.
- ViewModel constructor deps are **interfaces** with `Fake*` test doubles
  (hand-written, no mocking libraries). Growing an interface means updating
  its fake in the same change.
- One-shot UI events: the sealed `UiEvent` convention (`ui/common/UiEvent.kt`)
  on a `SharedFlow`; snackbar events go through `CollectUiEvents`.
- Analytics: log via `AnalyticsEvent` (counts + enum labels only — **never**
  contact data); `TrackScreenView("name")` on every screen/sheet/dialog.
- User-facing change ⇒ update `CHANGELOG.md` in the same commit (the
  `update-changelog` skill; release notes are generated from it).
- No new dependencies without a reason; add via `libs.versions.toml`.
- Lint stays clean: no unused imports/resources, `lintDebug` zero errors.

## Agents & commands

Subagents in `.claude/agents/` — delegate matching work to them:

| Agent | Use for |
|-------|---------|
| `ui-designer` | building/restyling Compose UI on the design system |
| `ux-writer` | any user-facing copy, strings files, store listing, changelog |
| `code-reviewer` | reviewing diffs/PRs against repo rules |
| `architecture-reviewer` | structural review: layering, placement, seams |
| `test-writer` | JVM unit tests + maintaining `Fake*` doubles |

Commands in `.claude/commands/`:

| Command | Does |
|---------|------|
| `/lint` | spotlessApply → spotlessCheck → lintDebug |
| `/architecture-audit` | rules R1–R14 + ratchet status (+ `fix` mode) |
| `/security-audit` | secrets, manifest/IPC, data-egress, deps |
| `/performance-audit` | recomposition, scroll, startup, main-thread I/O |
| `/verify-ui` | build → install → launch → screenshot on a device |

## Commits & PRs

- **Conventional Commits**: `feat(ui): …`, `fix(data): …`, `perf:`, `style:`,
  `ci:`, `docs:`. Imperative subject ≤ ~72 chars; body explains the why.
- Branch off `develop` (features) — never commit straight to `main`.
  develop → main promotes a release.
- Before committing: compile, tests, `spotlessCheck` green (and
  `/architecture-audit` clean for structural changes);
  guard against staging secrets — run
  `git diff --cached --name-only | grep -iE '\.jks|\.json|private|google-services' | grep -vE '\.gpg$|\.claude/|release/app-debug\.jks'`
  and investigate any match. Secret plaintext (release keystore,
  google-services.json, play-account.json) lives only gitignored under
  `release/`; committed are their `.gpg` blobs plus the deliberately-public
  shared debug keystore (`release/app-debug.jks`).
- Agent-authored commits carry the trailer the tool requires; PR bodies get the
  generated-with trailer.

## Planning workflow

Non-trivial features go **spec → plan → implement**, saved under
`docs/superpowers/` (`specs/YYYY-MM-DD-<topic>-design.md`,
`plans/YYYY-MM-DD-<topic>.md`). The premium-redesign spec + plan are the
reference examples. Don't skip this for multi-screen features.

## Gotchas (read before editing these areas)

- **SIM** stores name + one number only; the editor enforces name length +
  single number when the target is SIM. `SimAwareContactsWriter` routes CRUD
  between `ContactsContract` and `icc/adn` transparently — never bypass it.
- **`READ_PHONE_STATE`/`READ_PHONE_NUMBERS`** are optional, offered lazily via
  an AssistChip when a SIM account is visible; they only add dual-SIM labels +
  numbers. Don't make them required; everything degrades without them.
- **Capability probe** is cached in DataStore and can go stale after a SIM
  swap; Accounts pull-to-refresh re-probes.
- **Crash handling**: `ContactManagerApp` installs an uncaught-exception
  handler that shows `ui/crash/CrashActivity` (own `:crash` process, Hilt-free
  by design) and chains to Crashlytics. Don't make CrashActivity depend on DI.
- **Firebase is optional at build time**: everything guards on
  `release/google-services.json` existing (google-services + crashlytics
  plugins applied conditionally; `AnalyticsModule` falls back to NoOp). Builds
  must stay green without the file.
- **Play Billing** (Support donations): consumables, consumed immediately, no
  entitlements. Product ids `support_*` must exist in Play Console; billing is
  a no-op on debug/sideload builds.
- **In-app update/review** (`ui/update`, `ui/review`): Play-installed builds
  only; both are best-effort and silent on failure. Update flow picks
  immediate vs flexible from the release's `inAppUpdatePriority`
  (gradle.properties, stamped at upload by fastlane).
- **Versioning**: `appVersionName` lives in `gradle.properties` (required —
  missing fails the build) and feeds the manifest, the changelog plugin
  (`./gradlew -q changelogs`), and the release tag. versionCode =
  `100 + github.run_number` in CI.
- **minSdk 24**: Manrope's variable `wght` axis needs API 26; below that it
  falls back — expected, don't "fix".
- **Dynamic-color flip**: turning `dynamicColor` on changes the whole look on
  Android 12+ — only deliberately.
- **Shared-element transitions**: the whole nav graph sits in a
  `SharedTransitionLayout` (lookahead pass tax). Only the tapped row attaches
  a shared element; heavy screens defer content until the enter transition
  settles (see EditorScreen's `entering` gate). Keep new screens cheap during
  transitions.
