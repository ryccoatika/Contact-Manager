# Firebase Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrument the app with Firebase Analytics — every user action and every screen/dialog view — behind a Hilt-injected `Analytics` abstraction that degrades to a no-op when Firebase isn't configured.

**Architecture:** An `Analytics` interface in `data/analytics/` with `FirebaseAnalyticsImpl`, `NoOpAnalytics`, and a test `FakeAnalytics`. User actions are logged from the ViewModels that own them; screen/dialog views (and a few UI-only actions) are logged from Compose via a `LocalAnalytics` CompositionLocal + `TrackScreenView` composable. A Hilt module provides the real impl only when `FirebaseApp` is initialised (i.e. `google-services.json` present), else `NoOpAnalytics`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Firebase Analytics (via `firebase-bom`), `com.google.gms.google-services` Gradle plugin, JUnit.

## Global Constraints

- **No PII, ever.** Allowed event params: counts (Int), capability enums (`"FULL_CRUD"`/`"READ_ONLY"`/`"SIM"`), booleans, `query_length` (Int). Never names, numbers, emails, account names, or note text.
- **Guarded Firebase.** `google-services.json` lives in gitignored `release/`. The build **and all 154 unit tests must pass without it** — analytics falls back to `NoOpAnalytics`.
- **`domain/` stays pure** — no analytics there.
- **Presentation vs VM:** action events logged from VMs; screen/dialog views logged from Compose via `LocalAnalytics`.
- **Version catalog only** — libraries/plugins go in `gradle/libs.versions.toml`, never inline.
- **Commit trailer:** `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- **Package:** `com.ryccoatika.contactmanager`. After each task: `./gradlew compileDebugKotlin` and (where tests exist) `./gradlew :app:testDebugUnitTest`.

---

## File Structure

- `data/analytics/Analytics.kt` — interface (Task 1)
- `data/analytics/AnalyticsEvent.kt` — typed events → name + params (Task 1)
- `data/analytics/NoOpAnalytics.kt` — no-op impl (Task 1)
- `app/src/test/.../data/analytics/FakeAnalytics.kt` — test double (Task 1)
- `app/src/test/.../data/analytics/AnalyticsEventTest.kt` — event mapping tests (Task 1)
- `gradle/libs.versions.toml`, root & app `build.gradle.kts`, `.gitignore` — Firebase plumbing (Task 2)
- `data/analytics/FirebaseAnalyticsImpl.kt` — real impl (Task 3)
- `di/AnalyticsModule.kt` — guarded Hilt provider (Task 3)
- VMs: `HomeViewModel`, `DuplicatesViewModel`, `EditorViewModel`, `AccountsViewModel` + their tests (Task 4)
- `ui/analytics/LocalAnalytics.kt` — CompositionLocal + `TrackScreenView` (Task 5)
- `MainActivity.kt` + every screen/dialog composable (Task 5)

---

## Task 1: Analytics core types

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/analytics/Analytics.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/analytics/AnalyticsEvent.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/analytics/NoOpAnalytics.kt`
- Create: `app/src/test/java/com/ryccoatika/contactmanager/data/analytics/FakeAnalytics.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/data/analytics/AnalyticsEventTest.kt`

**Interfaces:**
- Produces: `interface Analytics { fun logEvent(event: AnalyticsEvent); fun logScreenView(screenName: String) }`
- Produces: `sealed class AnalyticsEvent(val name: String, val params: Map<String, Any>)` with the subclasses listed below.
- Produces: `object NoOpAnalytics : Analytics`, `class FakeAnalytics : Analytics` (fields `events: List<AnalyticsEvent>`, `screenViews: List<String>`).

- [ ] **Step 1: Write the failing test** `AnalyticsEventTest.kt`

```kotlin
package com.ryccoatika.contactmanager.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventTest {
    @Test fun `contact move carries count and capability, no PII`() {
        val e = AnalyticsEvent.ContactMove(count = 3, targetCapability = "SIM")
        assertEquals("contact_move", e.name)
        assertEquals(mapOf("count" to 3, "target_capability" to "SIM"), e.params)
    }

    @Test fun `account visibility name flips on hidden flag`() {
        assertEquals("account_hide", AnalyticsEvent.AccountVisibility(hidden = true).name)
        assertEquals("account_show", AnalyticsEvent.AccountVisibility(hidden = false).name)
    }

    @Test fun `onboarding name flips on skipped flag`() {
        assertEquals("onboarding_skip", AnalyticsEvent.Onboarding(skipped = true).name)
        assertEquals("onboarding_complete", AnalyticsEvent.Onboarding(skipped = false).name)
    }

    @Test fun `search logs only length`() {
        assertEquals(mapOf("query_length" to 5), AnalyticsEvent.Search(queryLength = 5).params)
    }

    @Test fun `paramless events have empty params`() {
        assertEquals(emptyMap<String, Any>(), AnalyticsEvent.ContactUpdate.params)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AnalyticsEventTest"`
Expected: FAIL — `AnalyticsEvent` unresolved.

- [ ] **Step 3: Create `Analytics.kt`**

```kotlin
package com.ryccoatika.contactmanager.data.analytics

/** App-facing analytics sink. Real impl logs to Firebase; NoOp/Fake for absent-Firebase/tests. */
interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun logScreenView(screenName: String)
}
```

- [ ] **Step 4: Create `AnalyticsEvent.kt`** — the single source of event names + params (keeps PII out by construction)

```kotlin
package com.ryccoatika.contactmanager.data.analytics

/** Every analytics event. Params are non-PII only: counts, enums, booleans, lengths. */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    data class ContactMove(val count: Int, val targetCapability: String) :
        AnalyticsEvent("contact_move", mapOf("count" to count, "target_capability" to targetCapability))
    data class ContactDelete(val count: Int) :
        AnalyticsEvent("contact_delete", mapOf("count" to count))
    data class ContactsMerge(val count: Int) :
        AnalyticsEvent("contacts_merge", mapOf("count" to count))
    data class ContactsLink(val count: Int) :
        AnalyticsEvent("contacts_link", mapOf("count" to count))
    data class ContactCreate(val accountCapability: String) :
        AnalyticsEvent("contact_create", mapOf("account_capability" to accountCapability))
    data object ContactUpdate : AnalyticsEvent("contact_update")
    data class AccountVisibility(val hidden: Boolean) :
        AnalyticsEvent(if (hidden) "account_hide" else "account_show")
    data class AccountMoveAll(val count: Int) :
        AnalyticsEvent("account_move_all", mapOf("count" to count))
    data class Search(val queryLength: Int) :
        AnalyticsEvent("search", mapOf("query_length" to queryLength))
    data object DuplicateDismiss : AnalyticsEvent("duplicate_dismiss")
    data object CallContact : AnalyticsEvent("call_contact")
    data object MessageContact : AnalyticsEvent("message_contact")
    data class Onboarding(val skipped: Boolean) :
        AnalyticsEvent(if (skipped) "onboarding_skip" else "onboarding_complete")
    data object SimPermissionGrant : AnalyticsEvent("sim_permission_grant")
}
```

- [ ] **Step 5: Create `NoOpAnalytics.kt`**

```kotlin
package com.ryccoatika.contactmanager.data.analytics

/** Used when Firebase is not configured, and as the CompositionLocal default. */
object NoOpAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun logScreenView(screenName: String) = Unit
}
```

- [ ] **Step 6: Create `FakeAnalytics.kt`** (test source set)

```kotlin
package com.ryccoatika.contactmanager.data.analytics

/** Records analytics calls for assertions. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val screenViews = mutableListOf<String>()
    override fun logEvent(event: AnalyticsEvent) { events += event }
    override fun logScreenView(screenName: String) { screenViews += screenName }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*AnalyticsEventTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/data/analytics app/src/test/java/com/ryccoatika/contactmanager/data/analytics
git commit -m "feat(analytics): Analytics abstraction + typed events

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Firebase Gradle plumbing (guarded)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`

**Interfaces:**
- Produces: Firebase on the classpath; `FirebaseApp` auto-initialises iff `release/google-services.json` exists.

- [ ] **Step 1: Determine compatible versions**

The project is on **AGP 9.2.1**. Confirm the latest `com.google.gms.google-services` and `firebase-bom` that support AGP 9 (check https://firebase.google.com/support/release-notes/android and the google-services plugin releases). Use those exact versions in Step 2 instead of the placeholders. Do not proceed with a version predating AGP-9 support.

- [ ] **Step 2: Add to `gradle/libs.versions.toml`**

Under `[versions]` (use verified numbers from Step 1):
```toml
googleServices = "4.4.3"
firebaseBom = "34.1.0"
```
Under `[libraries]`:
```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics" }
```
Under `[plugins]`:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 3: Declare the plugin in root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 4: In `app/build.gradle.kts`, add the guarded copy + conditional apply**

At the top of the file, after the `plugins { }` block, add:
```kotlin
// Firebase is optional: only wire google-services when the json (kept in the
// gitignored release/ folder, next to the keystores) is present. The plugin
// only scans app/, so bridge the file into place first.
val googleServicesJson = rootProject.file("release/google-services.json")
if (googleServicesJson.exists()) {
    googleServicesJson.copyTo(file("google-services.json"), overwrite = true)
    apply(plugin = "com.google.gms.google-services")
}
```

- [ ] **Step 5: Add the Firebase dependencies in `app/build.gradle.kts`**

In `dependencies { }`:
```kotlin
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.analytics)
```

- [ ] **Step 6: Update `.gitignore`**

Add:
```
release/google-services.json
app/google-services.json
```

- [ ] **Step 7: Verify the build works WITHOUT the json (guarded path)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (google-services plugin not applied; Firebase libs on classpath but unused).

- [ ] **Step 8: Verify unit tests still pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 154+ tests pass.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts .gitignore
git commit -m "build(analytics): add Firebase Analytics deps, guarded on google-services.json

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: FirebaseAnalyticsImpl + Hilt module

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/analytics/FirebaseAnalyticsImpl.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/di/AnalyticsModule.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent` (Task 1).
- Produces: Hilt binding for `Analytics` (Firebase impl when `FirebaseApp.getApps(ctx).isNotEmpty()`, else `NoOpAnalytics`). Injectable into VMs (Task 4) and `MainActivity` (Task 5).

- [ ] **Step 1: Create `FirebaseAnalyticsImpl.kt`**

```kotlin
package com.ryccoatika.contactmanager.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Logs to Firebase. Constructed only when Firebase is initialised (see AnalyticsModule). */
class FirebaseAnalyticsImpl(private val firebase: FirebaseAnalytics) : Analytics {

    override fun logEvent(event: AnalyticsEvent) {
        firebase.logEvent(event.name, event.params.toBundle())
    }

    override fun logScreenView(screenName: String) {
        firebase.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "ContactManager")
            },
        )
    }
}

private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
    forEach { (key, value) ->
        when (value) {
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            is Boolean -> putBoolean(key, value)
            else -> putString(key, value.toString())
        }
    }
}
```

- [ ] **Step 2: Create `AnalyticsModule.kt`**

```kotlin
package com.ryccoatika.contactmanager.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.FirebaseAnalyticsImpl
import com.ryccoatika.contactmanager.data.analytics.NoOpAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    /** Real Firebase sink only when google-services.json is present (FirebaseApp initialised); else no-op. */
    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseAnalyticsImpl(FirebaseAnalytics.getInstance(context))
        } else {
            NoOpAnalytics
        }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/data/analytics/FirebaseAnalyticsImpl.kt app/src/main/java/com/ryccoatika/contactmanager/di/AnalyticsModule.kt
git commit -m "feat(analytics): Firebase impl + guarded Hilt provider

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Log user actions from ViewModels

Inject `Analytics` into `HomeViewModel`, `DuplicatesViewModel`, `EditorViewModel`, `AccountsViewModel`; log the event on the **success** path (same spot as the existing snackbar). Add `FakeAnalytics()` to each VM test constructor and assert the event. This mirrors the existing `StringProvider` injection pattern exactly.

**Files:**
- Modify: `ui/home/HomeViewModel.kt`, `ui/duplicates/DuplicatesViewModel.kt`, `ui/editor/EditorViewModel.kt`, `ui/accounts/AccountsViewModel.kt`
- Modify: `ui/home/HomeViewModelTest.kt`, `ui/duplicates/DuplicatesViewModelTest.kt`, `ui/editor/EditorViewModelTest.kt`, `ui/accounts/AccountsViewModelTest.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent` (Task 1), `FakeAnalytics` (Task 1).
- Each VM constructor gains a trailing `private val analytics: Analytics` parameter (before any `@DefaultDispatcher` param, matching where `strings` was inserted).

### 4a — HomeViewModel

- [ ] **Step 1: Add failing test** in `HomeViewModelTest.kt`

```kotlin
@Test fun `moveSelectedTo logs contact_move with count and capability`() = runTest(dispatcher) {
    val analytics = FakeAnalytics()
    val vm = vm(analytics = analytics)
    val job = launch { vm.uiState.collect {} }
    dispatcher.scheduler.advanceUntilIdle()
    vm.toggleSelect(1)   // a FULL_CRUD google raw
    dispatcher.scheduler.advanceUntilIdle()
    vm.moveSelectedTo(googleTarget)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(analytics.events.any { it is AnalyticsEvent.ContactMove })
    job.cancel()
}
```
Also extend the `vm(...)` helper signature: add `analytics: FakeAnalytics = FakeAnalytics()` and pass `analytics = analytics` into the `HomeViewModel(...)` call. Import `AnalyticsEvent`, `FakeAnalytics`.

- [ ] **Step 2: Run — expect FAIL** (compile error: `HomeViewModel` has no `analytics` param).

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest"`

- [ ] **Step 3: Inject + log in `HomeViewModel.kt`**

Add import `com.ryccoatika.contactmanager.data.analytics.Analytics` and `...AnalyticsEvent`. Add constructor param `private val analytics: Analytics,` (right after `strings`). Log at each success/action point:

- In `moveSelectedTo`, right after the `batchManager.moveContacts(...)` call succeeds starting (i.e. when `movable` is non-empty and the batch starts), add:
```kotlin
analytics.logEvent(AnalyticsEvent.ContactMove(movable.size, target.capability.name))
```
(`target` is a `ContactAccount`; use its capability enum name. If `ContactAccount` exposes capability differently, derive via `AccountClassifier.classify(target.type).name`.)

- In `deleteSelected`, inside `is ContactOpResult.Success ->` add `analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))`.
- In `deleteContact`, inside its `is ContactOpResult.Success ->` add `analytics.logEvent(AnalyticsEvent.ContactDelete(ids.size))`.
- In `mergeSelected`, inside `is ContactOpResult.Success ->` add `analytics.logEvent(AnalyticsEvent.ContactsMerge(sources.size))`.
- In the debounced search pipeline (where the non-blank query settles), add `analytics.logEvent(AnalyticsEvent.Search(query.length))` for non-blank queries. If threading into the flow is awkward, log in `setQuery` guarded by `if (value.isNotBlank())`.

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeViewModel.kt app/src/test/java/com/ryccoatika/contactmanager/ui/home/HomeViewModelTest.kt
git commit -m "feat(analytics): log Home actions (move/delete/merge/search)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### 4b — DuplicatesViewModel

- [ ] **Step 1: Add failing tests** in `DuplicatesViewModelTest.kt` — assert `link` logs `ContactsLink(count)` and `merge` logs `ContactsMerge(count)`. Extend the `vm(...)` helper with `analytics: FakeAnalytics = FakeAnalytics()` passed into `DuplicatesViewModel(...)`.

```kotlin
@Test fun `link logs contacts_link`() = runTest(dispatcher) {
    val analytics = FakeAnalytics()
    val vm = vm(analytics = analytics)
    // ... trigger a link on a known group as existing link test does ...
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(analytics.events.any { it is AnalyticsEvent.ContactsLink })
}
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Inject + log** in `DuplicatesViewModel.kt`: add `private val analytics: Analytics,` (after `strings`); in `link` success add `analytics.logEvent(AnalyticsEvent.ContactsLink(group.contacts.size))`; in `merge` success add `analytics.logEvent(AnalyticsEvent.ContactsMerge(sources.size))`; in `dismiss` add `analytics.logEvent(AnalyticsEvent.DuplicateDismiss)`.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** (`feat(analytics): log Duplicates actions (link/merge/dismiss)`).

### 4c — EditorViewModel

- [ ] **Step 1: Add failing test** in `EditorViewModelTest.kt`: saving a new contact logs `ContactCreate`; editing logs `ContactUpdate`. Extend `newVm`/`editVm` helpers with `analytics: FakeAnalytics = FakeAnalytics()` passed to `EditorViewModel(...)`.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Inject + log** in `EditorViewModel.kt`: add `private val analytics: Analytics,`; in `save` success, when creating log `AnalyticsEvent.ContactCreate(selectedAccountCapability.name)` (derive capability from the selected account via `AccountClassifier.classify(type)`), when editing log `AnalyticsEvent.ContactUpdate`.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** (`feat(analytics): log Editor create/update`).

### 4d — AccountsViewModel

- [ ] **Step 1: Add failing tests** in `AccountsViewModelTest.kt`: `moveAllContacts` (non-empty) logs `AccountMoveAll(count)`; `setAccountHidden(true/false)` logs `AccountVisibility(hidden)`. Extend the `vm(...)` helper with `analytics: FakeAnalytics = FakeAnalytics()`.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Inject + log** in `AccountsViewModel.kt`: add `private val analytics: Analytics,`; in `moveAllContacts` after a successful start with non-empty `rawIds` log `AnalyticsEvent.AccountMoveAll(rawIds.size)`; in `setAccountHidden` log `AnalyticsEvent.AccountVisibility(hidden)`.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** (`feat(analytics): log Accounts move-all + hide/show`).

- [ ] **Final: full suite** — `./gradlew :app:testDebugUnitTest` (all green).

---

## Task 5: Screen / dialog / UI-action tracking

Provide `Analytics` to Compose via a CompositionLocal, add `TrackScreenView` to every destination + dialog, and log the UI-only action events (call/message/sim/onboarding).

**Files:**
- Create: `ui/analytics/LocalAnalytics.kt`
- Modify: `MainActivity.kt`
- Modify: `ui/home/HomeScreen.kt`, `ui/detail/DetailScreen.kt`, `ui/editor/EditorScreen.kt`, `ui/duplicates/DuplicatesScreen.kt`, `ui/accounts/AccountsScreen.kt`, `ui/onboarding/OnboardingScreen.kt`, `ui/common/PhonePermissionPrompt.kt`

**Interfaces:**
- Consumes: `Analytics`, `AnalyticsEvent`, `NoOpAnalytics` (Task 1); Hilt `Analytics` binding (Task 3).
- Produces: `val LocalAnalytics: ProvidableCompositionLocal<Analytics>` and `@Composable fun TrackScreenView(screenName: String)`.

- [ ] **Step 1: Create `LocalAnalytics.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.NoOpAnalytics

/** Analytics for composables; provided at the app root, defaults to no-op. */
val LocalAnalytics: ProvidableCompositionLocal<Analytics> = staticCompositionLocalOf { NoOpAnalytics }

/** Logs a single screen_view for [screenName] when this composable enters composition. */
@Composable
fun TrackScreenView(screenName: String) {
    val analytics = LocalAnalytics.current
    LaunchedEffect(screenName) { analytics.logScreenView(screenName) }
}
```

- [ ] **Step 2: Provide it in `MainActivity.kt`** — inject `Analytics`, wrap `AppNav()`:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var analytics: Analytics
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactManagerTheme {
                CompositionLocalProvider(LocalAnalytics provides analytics) {
                    AppNav()
                }
            }
        }
    }
}
```
Add imports: `androidx.compose.runtime.CompositionLocalProvider`, `javax.inject.Inject`, `com.ryccoatika.contactmanager.data.analytics.Analytics`, `com.ryccoatika.contactmanager.ui.analytics.LocalAnalytics`.

- [ ] **Step 3: Add `TrackScreenView` at the top of each screen composable body**

- `HomeScreen` → `TrackScreenView("home")`
- `DetailScreen` → `TrackScreenView("contact_detail")`
- `EditorScreen` → `TrackScreenView(if (isEdit) "editor_edit" else "editor_new")` (read `isEdit` from `uiState`)
- `DuplicatesScreen` → `TrackScreenView("duplicates")`
- `AccountsScreen` → `TrackScreenView("accounts")`
- `OnboardingScreen` → `TrackScreenView("onboarding")`

Import `com.ryccoatika.contactmanager.ui.analytics.TrackScreenView` in each.

- [ ] **Step 4: Add `TrackScreenView` inside each dialog's `if (show…) { }`**

Place immediately inside the block that renders the `AlertDialog`:
- Home: delete-confirm → `TrackScreenView("delete_contact_confirm")`; delete-selected → `"delete_selected_confirm"`; move-fields-lost → `"move_fields_lost"`.
- Detail: delete dialog → `"delete_contact_confirm"`.
- Editor: discard dialog → `"discard_edit"`.
- Duplicates: merge dialog → `"merge_confirm"`; link dialog → `"link_confirm"` (if link uses a dialog; else skip).
- Accounts: move-confirm dialog → `"account_move_confirm"`.

- [ ] **Step 5: Log UI-only action events via `LocalAnalytics.current`**

- `DetailScreen` Call quick action `onClick`: `LocalAnalytics.current` captured as `val analytics = LocalAnalytics.current` at composable top; in the call `onClick` add `analytics.logEvent(AnalyticsEvent.CallContact)` before firing the intent; Message action → `AnalyticsEvent.MessageContact`.
- `PhonePermissionPrompt`: in the `onGranted` path (launcher result when granted), add `analytics.logEvent(AnalyticsEvent.SimPermissionGrant)` using `LocalAnalytics.current`.
- `OnboardingScreen`: Skip button → `analytics.logEvent(AnalyticsEvent.Onboarding(skipped = true))`; Get-started button → `AnalyticsEvent.Onboarding(skipped = false)`; both via `val analytics = LocalAnalytics.current`.

Import `com.ryccoatika.contactmanager.data.analytics.AnalyticsEvent` and `com.ryccoatika.contactmanager.ui.analytics.LocalAnalytics` where used.

- [ ] **Step 6: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all green (screen/dialog logging isn't unit-tested; VM tests from Task 4 still pass).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/analytics app/src/main/java/com/ryccoatika/contactmanager/MainActivity.kt app/src/main/java/com/ryccoatika/contactmanager/ui
git commit -m "feat(analytics): screen/dialog views + call/message/sim/onboarding events

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Manual verification (with json)

Not unit-testable — do this once when a real `google-services.json` is available.

- [ ] **Step 1:** Place a real `google-services.json` (matching applicationId `com.ryccoatika.contactmanager`) in `release/`.
- [ ] **Step 2:** `./gradlew :app:assembleDebug` — confirm `:app:processDebugGoogleServices` runs (plugin applied).
- [ ] **Step 3:** Install, enable DebugView: `adb shell setprop debug.firebase.analytics.app com.ryccoatika.contactmanager`.
- [ ] **Step 4:** In Firebase console → DebugView, navigate screens/dialogs and perform actions; confirm `screen_view` + action events arrive with the expected non-PII params and **no** names/numbers.
- [ ] **Step 5:** Remove the json → `./gradlew :app:assembleDebug` still succeeds (guarded path), analytics no-op.

---

## Self-Review (done)

- **Spec coverage:** guarded Firebase (T2/T3), no-PII typed events (T1), VM action logging (T4), screen/dialog views (T5), no opt-out/Settings (correctly omitted), FakeAnalytics tests (T1/T4), manual DebugView check (T6). ✓
- **Type consistency:** `Analytics.logEvent/logScreenView`, `AnalyticsEvent.*`, `FakeAnalytics.events/screenViews`, `LocalAnalytics`, `TrackScreenView` used consistently across tasks. ✓
- **Open risk flagged:** google-services/firebase-bom versions vs AGP 9.2.1 — pinned in T2 Step 1 with a verify-first step. Capability derivation for `contact_move`/`contact_create` (T4) may need `AccountClassifier.classify(type)` if `ContactAccount`/editor state doesn't expose a capability enum directly — noted at the call sites.
