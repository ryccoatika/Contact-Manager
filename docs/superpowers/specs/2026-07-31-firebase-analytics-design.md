# Firebase Analytics — Design

**Date:** 2026-07-31
**Status:** Approved

## Goal

Instrument the app with Firebase Analytics so we can see **every user action**
and **every screen / dialog / bottom-sheet that appears**. Collection is always
on (no consent gate, no opt-out toggle). **No PII is ever logged** — only action
types, counts, category enums, and screen names.

## Constraints & principles

- **Guarded Firebase.** `google-services.json` lives in the gitignored `release/`
  folder (next to the keystores). The app **must still build and pass all 154
  unit tests without it** — analytics degrades to a no-op.
- **No PII.** Never log contact display names, phone numbers, emails, account
  names, or note text. Allowed params: counts, capability enums
  (`FULL_CRUD`/`READ_ONLY`/`SIM`), booleans, and `query_length` (int).
- **Wrap the boundary.** Follow the existing pattern (`StringProvider`,
  repositories): an `Analytics` interface injected via Hilt, real impl over
  `FirebaseAnalytics`, `NoOp` fallback, `Fake` for tests.
- **Golden rules hold.** `domain/` stays pure (no analytics there). All provider
  I/O stays off the main thread (analytics calls are cheap/non-blocking anyway).

## Architecture

```
data/analytics/
  Analytics.kt            interface: logEvent(AnalyticsEvent), logScreenView(name)
  AnalyticsEvent.kt       typed events → (name: String, params: Map<String,Any>)
  FirebaseAnalyticsImpl   @Inject; wraps FirebaseAnalytics, maps event → Bundle
  NoOpAnalytics           does nothing (json absent, and tests)
di/AnalyticsModule.kt     @Provides Analytics — Firebase impl IF FirebaseApp is
                          initialised, else NoOp
ui/analytics/
  LocalAnalytics.kt       CompositionLocal<Analytics> + TrackScreenView(name)
test/…/FakeAnalytics.kt   records logged events for assertions
```

### `Analytics` interface

```kotlin
interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun logScreenView(screenName: String)
}
```

`logScreenView` emits Firebase's reserved `screen_view` event with
`SCREEN_NAME` (and `SCREEN_CLASS = "ContactManager"`), so screens **and**
dialogs land in Firebase's screen reports.

### `AnalyticsEvent`

A sealed hierarchy (or small data holder) whose only job is to produce a
stable `name` + a non-PII `params` map. Keeping keys in one file prevents PII
leaking in by accident at call sites. Example:

```kotlin
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    data class ContactMove(val count: Int, val targetCapability: String) :
        AnalyticsEvent("contact_move", mapOf("count" to count, "target_capability" to targetCapability))
    // … one per action below
}
```

### Guard (`AnalyticsModule`)

```kotlin
@Provides @Singleton
fun analytics(@ApplicationContext ctx: Context): Analytics =
    if (FirebaseApp.getApps(ctx).isNotEmpty()) FirebaseAnalyticsImpl(Firebase.analytics)
    else NoOpAnalytics
```

`FirebaseApp` auto-initialises via the `google-services` plugin's manifest
`ContentProvider` **only when the json is present**. No json → no `FirebaseApp`
→ `NoOpAnalytics`. This is the single switch that keeps CI/dev/test builds green.

## Gradle / Firebase plumbing (guarded)

- `gradle/libs.versions.toml`: add `firebase-bom`, `firebase-analytics`, and the
  `google-services` plugin (as a version-catalog plugin, `apply false` at root).
- Root `build.gradle.kts`: declare the plugin `apply false`.
- `app/build.gradle.kts`:
  - Guarded copy: if `rootProject.file("release/google-services.json")` exists,
    copy it to `app/google-services.json` (the plugin only scans `app/…`).
  - Conditionally `apply(plugin = "com.google.gms.google-services")` **only when**
    that json exists.
  - Add `implementation(platform(firebase-bom))` + `implementation(firebase-analytics)`.
- `.gitignore`: add `app/google-services.json` (release copy already ignored).
- R8: Firebase ships its own consumer keep rules — nothing to add to
  `proguard-rules.pro`.

## Instrumentation map

### Screen views (`TrackScreenView` in each destination composable)
`home`, `contact_detail`, `editor_new`, `editor_edit`, `duplicates`,
`accounts`, `onboarding`.

### Dialog / sheet views (`TrackScreenView` inside each `if (show…)`)
`delete_contact_confirm`, `delete_selected_confirm`, `move_fields_lost`,
`discard_edit`, `merge_confirm`, `link_confirm`, `account_move_confirm`.
(The app currently has no bottom sheets; if one is added later it logs the same
way.)

### Action events (logged from the ViewModel that owns the action)
| Event | Params | Source |
|---|---|---|
| `contact_move` | count, target_capability | HomeViewModel.moveSelectedTo |
| `contact_delete` | count | HomeViewModel.deleteSelected / deleteContact |
| `contacts_merge` | count | HomeViewModel.mergeSelected |
| `contacts_link` | count | DuplicatesViewModel.link |
| `contacts_merge` | count | DuplicatesViewModel.merge |
| `contact_create` | account_capability | EditorViewModel.save (new) |
| `contact_update` | — | EditorViewModel.save (edit) |
| `contact_copy` | — | (if surfaced) |
| `account_hide` / `account_show` | — | AccountsViewModel.setAccountHidden / HomeViewModel |
| `account_move_all` | count | AccountsViewModel.moveAllContacts |
| `search` | query_length | HomeViewModel.setQuery (on non-empty, debounced) |
| `duplicate_dismiss` | — | DuplicatesViewModel.dismiss |
| `call_contact` / `message_contact` | — | DetailScreen quick actions |
| `onboarding_complete` / `onboarding_skip` | — | OnboardingViewModel |
| `sim_permission_grant` | — | PhonePermissionPrompt onGranted |

Only success paths for mutating actions are logged (log after
`ContactOpResult.Success`), matching where the VMs emit their snackbars.

### Getting `Analytics` into composables
`LocalAnalytics` is provided once in `MainActivity` (via a Hilt `EntryPoint`),
so any screen or dialog calls `TrackScreenView("name")` without threading it
through call sites. Action events go through the VMs (already injected).

## Testing

- `FakeAnalytics` records `logEvent`/`logScreenView` calls.
- VM tests that exercise an action assert the expected event name + params were
  logged (e.g. `contact_move` with the right count).
- All VM test constructors gain a `FakeAnalytics()` argument (named args, as with
  `FakeStringProvider`).
- No Firebase dependency in unit tests — they run against `FakeAnalytics`.
- Target: existing 154 tests stay green; new assertions added on top.

## Out of scope (YAGNI)

- Consent UI / opt-out toggle / Settings screen.
- Crashlytics, Performance, Remote Config.
- User properties, funnels, custom audiences.
- Per-screen dwell-time / scroll-depth.
