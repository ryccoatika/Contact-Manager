# Phase 1: Foundation + Read Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permissions gate + full read path (accounts + contacts as Flow) + unified home list with account filter chips, search, badges.

**Architecture:** MVVM + Repository directly over ContactsContract (no cache DB). Bulk Data-table query grouped in memory into `Contact` aggregates. Hilt DI, Compose UI, Navigation-Compose.

**Tech Stack:** Kotlin 2.2.10, AGP 9.2.1 (built-in Kotlin), Compose BOM 2026.02.01, Material3, Hilt (KSP), Navigation-Compose, Coil.

## Global Constraints

- minSdk 24, targetSdk 36, compileSdk 36.1 (already configured — do not change).
- Package root: `com.ryccoatika.contactmanager`.
- ALL ContentResolver access inside repository methods wrapped `withContext(ioDispatcher)` — never trust callers.
- Bulk queries only — never per-contact cursor loops.
- No Room, no cache DB. ContactsContract is source of truth.
- Spec: `docs/superpowers/specs/2026-07-12-contact-manager-design.md`.

---

### Task 1: Dependencies + Hilt scaffold

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ContactManagerApp.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/di/DispatchersModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: Hilt application, `@IoDispatcher` / `@DefaultDispatcher` qualifiers used by every repository task.

- [ ] **Step 1: Add versions/libraries/plugins to `gradle/libs.versions.toml`**

Append to `[versions]`:
```toml
hilt = "2.57.2"
ksp = "2.2.10-2.0.2"
navigationCompose = "2.9.4"
hiltNavigationCompose = "1.3.0"
coil = "2.7.0"
lifecycleViewmodelCompose = "2.9.4"
coroutinesTest = "1.10.2"
materialIconsExtended = "1.7.8"
```

Append to `[libraries]`:
```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "materialIconsExtended" }
```

Append to `[plugins]`:
```toml
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Wire plugins + dependencies in `app/build.gradle.kts`**

Add to `plugins {}`:
```kotlin
alias(libs.plugins.hilt)
alias(libs.plugins.ksp)
```

Add to `dependencies {}`:
```kotlin
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)
implementation(libs.androidx.navigation.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.coil.compose)
implementation(libs.androidx.compose.material.icons.extended)
testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Application class**

`app/src/main/java/com/ryccoatika/contactmanager/ContactManagerApp.kt`:
```kotlin
package com.ryccoatika.contactmanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContactManagerApp : Application()
```

- [ ] **Step 4: Dispatchers module**

`app/src/main/java/com/ryccoatika/contactmanager/di/DispatchersModule.kt`:
```kotlin
package com.ryccoatika.contactmanager.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

- [ ] **Step 5: Manifest — permissions + app class + annotate MainActivity**

In `AndroidManifest.xml` add above `<application>`:
```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.WRITE_CONTACTS" />
```
Add `android:name=".ContactManagerApp"` to `<application>`.

In `MainActivity.kt` add `@dagger.hilt.android.AndroidEntryPoint` annotation on the class.

- [ ] **Step 6: Build check**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. (If KSP version `2.2.10-2.0.2` unresolvable, check latest `2.2.10-*` on Maven Central and use it.)

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: add Hilt, navigation, coil deps + app scaffold"
```

---

### Task 2: Domain models + AccountClassifier (TDD)

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/model/Models.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/AccountClassifier.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/domain/AccountClassifierTest.kt`

**Interfaces:**
- Produces:
  - `data class Contact(contactId: Long, displayName: String, photoThumbnailUri: String?, starred: Boolean, rawContacts: List<RawContact>)`
  - `data class RawContact(rawContactId: Long, accountType: String?, accountName: String?, phones: List<LabeledValue>, emails: List<LabeledValue>, organization: String?, note: String?)`
  - `data class LabeledValue(dataId: Long, value: String, typeLabel: String?)`
  - `data class ContactAccount(name: String?, type: String?, capability: AccountCapability, contactCount: Int)` with `val key: String get() = "$type/$name"`
  - `enum class AccountCapability { FULL_CRUD, READ_ONLY, SIM }`
  - `AccountClassifier.classify(accountType: String?): AccountCapability`

- [ ] **Step 1: Write models**

`domain/model/Models.kt`:
```kotlin
package com.ryccoatika.contactmanager.domain.model

data class LabeledValue(
    val dataId: Long,
    val value: String,
    val typeLabel: String?,
)

data class RawContact(
    val rawContactId: Long,
    val accountType: String?,
    val accountName: String?,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val organization: String? = null,
    val note: String? = null,
)

data class Contact(
    val contactId: Long,
    val displayName: String,
    val photoThumbnailUri: String? = null,
    val starred: Boolean = false,
    val rawContacts: List<RawContact> = emptyList(),
)

enum class AccountCapability { FULL_CRUD, READ_ONLY, SIM }

data class ContactAccount(
    val name: String?,
    val type: String?,
    val capability: AccountCapability,
    val contactCount: Int = 0,
) {
    val key: String get() = "$type/$name"
}
```

- [ ] **Step 2: Write failing classifier test**

`app/src/test/java/com/ryccoatika/contactmanager/domain/AccountClassifierTest.kt`:
```kotlin
package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.AccountCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountClassifierTest {
    @Test fun `google is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify("com.google"))

    @Test fun `device local null type is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify(null))

    @Test fun `samsung account is full crud`() =
        assertEquals(AccountCapability.FULL_CRUD, AccountClassifier.classify("com.osp.app.signin"))

    @Test fun `whatsapp is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.whatsapp"))

    @Test fun `telegram is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("org.telegram.messenger"))

    @Test fun `viber is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.viber.voip"))

    @Test fun `signal is read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("org.thoughtcrime.securesms"))

    @Test fun `samsung sim account is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("vnd.sec.contact.sim"))

    @Test fun `generic sim account is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("com.android.contacts.sim"))

    @Test fun `unknown app account defaults to read only`() =
        assertEquals(AccountCapability.READ_ONLY, AccountClassifier.classify("com.some.random.app"))

    @Test fun `unknown but contains sim keyword is sim`() =
        assertEquals(AccountCapability.SIM, AccountClassifier.classify("vnd.xiaomi.contact.usim"))
}
```

- [ ] **Step 3: Run test — verify fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.domain.AccountClassifierTest" -q`
Expected: FAIL — unresolved reference `AccountClassifier`.

- [ ] **Step 4: Implement classifier**

`domain/AccountClassifier.kt`:
```kotlin
package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.AccountCapability

/**
 * Classifies a RawContacts account_type into what operations we allow.
 * Unknown third-party types default to READ_ONLY: writing into an app-managed
 * account is at best ignored and at worst clobbered by that app's sync.
 */
object AccountClassifier {

    private val FULL_CRUD_TYPES = setOf(
        "com.google",
        "com.osp.app.signin",          // Samsung account
        "vnd.sec.contact.phone",       // Samsung device-local
        "com.android.huawei.phone",
        "com.oppo.contacts.device",
        "vnd.oneplus.contact.phone",
        "com.xiaomi",
        "com.android.localphone",
        "com.android.contacts.default",
    )

    private val SIM_TYPES = setOf(
        "vnd.sec.contact.sim",         // Samsung SIM
        "vnd.sec.contact.sim2",
        "com.android.contacts.sim",
        "com.android.sim",
        "USIM Account",
    )

    fun classify(accountType: String?): AccountCapability = when {
        accountType == null -> AccountCapability.FULL_CRUD
        accountType in SIM_TYPES -> AccountCapability.SIM
        accountType.contains("sim", ignoreCase = true) -> AccountCapability.SIM
        accountType in FULL_CRUD_TYPES -> AccountCapability.FULL_CRUD
        else -> AccountCapability.READ_ONLY
    }
}
```

- [ ] **Step 5: Run test — verify passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.domain.AccountClassifierTest" -q`
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: domain models + account capability classifier"
```

---

### Task 3: ContactAggregator — bulk rows to Contact aggregates (TDD)

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/ContactAggregator.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/data/ContactAggregatorTest.kt`

**Interfaces:**
- Consumes: models from Task 2.
- Produces:
  - `data class DataRow(dataId: Long, rawContactId: Long, contactId: Long, mimeType: String?, data1: String?, typeLabel: String?, accountType: String?, accountName: String?, displayName: String?, photoThumbUri: String?, starred: Boolean)`
  - `ContactAggregator.aggregate(rows: List<DataRow>): List<Contact>` — sorted by displayName (case-insensitive), phones/emails deduped by value, rawContacts grouped per account.

- [ ] **Step 1: Write failing test**

`app/src/test/java/com/ryccoatika/contactmanager/data/ContactAggregatorTest.kt`:
```kotlin
package com.ryccoatika.contactmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactAggregatorTest {

    private fun row(
        dataId: Long, rawId: Long, contactId: Long, mime: String?,
        data1: String?, name: String = "Contact $contactId",
        accType: String? = "com.google", accName: String? = "a@gmail.com",
    ) = DataRow(
        dataId = dataId, rawContactId = rawId, contactId = contactId,
        mimeType = mime, data1 = data1, typeLabel = null,
        accountType = accType, accountName = accName,
        displayName = name, photoThumbUri = null, starred = false,
    )

    private val PHONE = "vnd.android.cursor.item/phone_v2"
    private val EMAIL = "vnd.android.cursor.item/email_v2"

    @Test
    fun `groups rows into one contact with one raw contact`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111"),
            row(2, 10, 100, EMAIL, "x@y.com"),
        ))
        assertEquals(1, contacts.size)
        assertEquals(1, contacts[0].rawContacts.size)
        assertEquals("+62812111", contacts[0].rawContacts[0].phones[0].value)
        assertEquals("x@y.com", contacts[0].rawContacts[0].emails[0].value)
    }

    @Test
    fun `same contact across two accounts yields two raw contacts`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111", accType = "com.google"),
            row(2, 11, 100, PHONE, "+62812111", accType = "com.whatsapp", accName = "WhatsApp"),
        ))
        assertEquals(1, contacts.size)
        assertEquals(2, contacts[0].rawContacts.size)
    }

    @Test
    fun `contact with no data rows still appears (name-only row)`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(0, 10, 100, null, null),
        ))
        assertEquals(1, contacts.size)
        assertEquals(0, contacts[0].rawContacts[0].phones.size)
    }

    @Test
    fun `duplicate phone values within raw contact deduped`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62812111"),
            row(2, 10, 100, PHONE, "+62812111"),
        ))
        assertEquals(1, contacts[0].rawContacts[0].phones.size)
    }

    @Test
    fun `sorted by display name case-insensitive`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "1", name = "zack"),
            row(2, 11, 101, PHONE, "2", name = "Anna"),
        ))
        assertEquals(listOf("Anna", "zack"), contacts.map { it.displayName })
    }

    @Test
    fun `blank display name falls back to phone then unnamed`() {
        val contacts = ContactAggregator.aggregate(listOf(
            row(1, 10, 100, PHONE, "+62899", name = ""),
        ))
        assertEquals("+62899", contacts[0].displayName)
    }
}
```

- [ ] **Step 2: Run test — verify fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.data.ContactAggregatorTest" -q`
Expected: FAIL — unresolved `DataRow` / `ContactAggregator`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/ryccoatika/contactmanager/data/ContactAggregator.kt`:
```kotlin
package com.ryccoatika.contactmanager.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact

/** One row of the bulk Data-table query. Pure value type so aggregation is JVM-testable. */
data class DataRow(
    val dataId: Long,
    val rawContactId: Long,
    val contactId: Long,
    val mimeType: String?,
    val data1: String?,
    val typeLabel: String?,
    val accountType: String?,
    val accountName: String?,
    val displayName: String?,
    val photoThumbUri: String?,
    val starred: Boolean,
)

object ContactAggregator {

    fun aggregate(rows: List<DataRow>): List<Contact> =
        rows.groupBy { it.contactId }
            .map { (contactId, contactRows) -> buildContact(contactId, contactRows) }
            .sortedBy { it.displayName.lowercase() }

    private fun buildContact(contactId: Long, rows: List<DataRow>): Contact {
        val rawContacts = rows.groupBy { it.rawContactId }.map { (rawId, rawRows) ->
            val first = rawRows.first()
            RawContact(
                rawContactId = rawId,
                accountType = first.accountType,
                accountName = first.accountName,
                phones = rawRows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                emails = rawRows.filter { it.mimeType == Email.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
                    .distinctBy { it.data1 }
                    .map { LabeledValue(it.dataId, it.data1!!, it.typeLabel) },
                organization = rawRows.firstOrNull { it.mimeType == Organization.CONTENT_ITEM_TYPE }?.data1,
                note = rawRows.firstOrNull { it.mimeType == Note.CONTENT_ITEM_TYPE }?.data1,
            )
        }
        val first = rows.first()
        val fallbackName = rawContacts.firstNotNullOfOrNull { it.phones.firstOrNull()?.value }
            ?: rawContacts.firstNotNullOfOrNull { it.emails.firstOrNull()?.value }
            ?: "(unnamed)"
        return Contact(
            contactId = contactId,
            displayName = first.displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            photoThumbnailUri = rows.firstNotNullOfOrNull { it.photoThumbUri },
            starred = first.starred,
            rawContacts = rawContacts,
        )
    }
}
```

Note: `Phone.CONTENT_ITEM_TYPE` etc. are plain String constants — resolvable in JVM unit tests without Robolectric because unit tests use `android.jar` stub constants? They are static final Strings but android.jar stubs throw on method calls, not field reads — constants are inlined at compile time. If test still fails on class-load, add to `app/build.gradle.kts` `android {}`: `testOptions { unitTests.isReturnDefaultValues = true }` — constants stay compile-time inlined either way.

- [ ] **Step 4: Run test — verify passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.data.ContactAggregatorTest" -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: contact aggregator - bulk data rows to contact aggregates"
```

---

### Task 4: ContactsRepository + AccountRepository (read path)

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/ContactsRepository.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/AccountRepository.kt`

**Interfaces:**
- Consumes: `ContactAggregator`, `AccountClassifier`, `@IoDispatcher`, models.
- Produces:
  - `ContactsRepository.observeContacts(): Flow<List<Contact>>` — hot requery on provider change, debounced 300 ms, runs on IO.
  - `AccountRepository.observeAccounts(): Flow<List<ContactAccount>>` — distinct accounts + counts, classified.

No unit tests here (thin ContentResolver glue; logic lives in Task 2/3 pure classes). Verified by build + Phase 1 manual run; instrumented CRUD tests come with write path in Phase 2.

- [ ] **Step 1: Implement ContactsRepository**

`app/src/main/java/com/ryccoatika/contactmanager/data/ContactsRepository.kt`:
```kotlin
package com.ryccoatika.contactmanager.data

import android.content.Context
import android.database.ContentObserver
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Emits full contact list on subscription and again on every provider change (debounced). */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observeContacts(): Flow<List<Contact>> = contentChanges()
        .debounce(300)
        .mapLatest { queryAllContacts() }
        .flowOn(ioDispatcher)

    private fun contentChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true, observer,
        )
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private suspend fun queryAllContacts(): List<Contact> = withContext(ioDispatcher) {
        val projection = arrayOf(
            Data._ID,
            Data.RAW_CONTACT_ID,
            Data.CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED,
        )
        val rows = ArrayList<DataRow>(1024)
        context.contentResolver.query(
            Data.CONTENT_URI, projection, null, null, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Data._ID)
            val iRaw = c.getColumnIndexOrThrow(Data.RAW_CONTACT_ID)
            val iContact = c.getColumnIndexOrThrow(Data.CONTACT_ID)
            val iMime = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val iData1 = c.getColumnIndexOrThrow(Data.DATA1)
            val iAccType = c.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val iAccName = c.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
            val iName = c.getColumnIndexOrThrow(Data.DISPLAY_NAME_PRIMARY)
            val iPhoto = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val iStar = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            while (c.moveToNext()) {
                rows += DataRow(
                    dataId = c.getLong(iId),
                    rawContactId = c.getLong(iRaw),
                    contactId = c.getLong(iContact),
                    mimeType = c.getString(iMime),
                    data1 = c.getString(iData1),
                    typeLabel = null,
                    accountType = c.getString(iAccType),
                    accountName = c.getString(iAccName),
                    displayName = c.getString(iName),
                    photoThumbUri = c.getString(iPhoto),
                    starred = c.getInt(iStar) == 1,
                )
            }
        }
        ContactAggregator.aggregate(rows)
    }
}
```

- [ ] **Step 2: Implement AccountRepository**

`app/src/main/java/com/ryccoatika/contactmanager/data/AccountRepository.kt`:
```kotlin
package com.ryccoatika.contactmanager.data

import android.content.Context
import android.provider.ContactsContract.RawContacts
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Accounts derived from RawContacts rows (catches WhatsApp-style accounts that
     * AccountManager restricts), with per-account contact counts.
     */
    suspend fun getAccounts(): List<ContactAccount> = withContext(ioDispatcher) {
        val counts = HashMap<Pair<String?, String?>, Int>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) to c.getString(1)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts.map { (key, count) ->
            ContactAccount(
                name = key.second,
                type = key.first,
                capability = AccountClassifier.classify(key.first),
                contactCount = count,
            )
        }.sortedByDescending { it.contactCount }
    }
}
```

- [ ] **Step 3: Build check**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: contacts + accounts read repositories over ContactsContract"
```

---

### Task 5: HomeViewModel — filter + search (TDD)

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `ContactsRepository.observeContacts()`, `AccountRepository.getAccounts()`, `@DefaultDispatcher`.
- Produces:
  - `data class HomeUiState(contacts: List<Contact>, accounts: List<ContactAccount>, selectedAccountKey: String?, query: String, loading: Boolean)`
  - `HomeViewModel.uiState: StateFlow<HomeUiState>`, `fun setQuery(q: String)`, `fun selectAccount(key: String?)`

To keep the ViewModel JVM-testable, repositories are consumed through interfaces:

- [ ] **Step 1: Extract interfaces**

Modify `ContactsRepository.kt` and `AccountRepository.kt`: add interfaces in the same files and have Hilt bind implementations.

```kotlin
// in ContactsRepository.kt
interface ContactsSource {
    fun observeContacts(): Flow<List<Contact>>
}
// class ContactsRepository ... : ContactsSource (add override modifier to observeContacts)
```

```kotlin
// in AccountRepository.kt
interface AccountsSource {
    suspend fun getAccounts(): List<ContactAccount>
}
// class AccountRepository ... : AccountsSource (add override modifier to getAccounts)
```

Create `app/src/main/java/com/ryccoatika/contactmanager/di/DataModule.kt`:
```kotlin
package com.ryccoatika.contactmanager.di

import com.ryccoatika.contactmanager.data.AccountRepository
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsRepository
import com.ryccoatika.contactmanager.data.ContactsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun bindContactsSource(impl: ContactsRepository): ContactsSource
    @Binds abstract fun bindAccountsSource(impl: AccountRepository): AccountsSource
}
```

- [ ] **Step 2: Write failing ViewModel test**

`app/src/test/java/com/ryccoatika/contactmanager/ui/home/HomeViewModelTest.kt`:
```kotlin
package com.ryccoatika.contactmanager.ui.home

import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun contact(id: Long, name: String, accType: String?, phone: String? = null) = Contact(
        contactId = id, displayName = name,
        rawContacts = listOf(RawContact(
            rawContactId = id * 10, accountType = accType, accountName = "acc",
            phones = phone?.let { listOf(LabeledValue(1, it, null)) } ?: emptyList(),
        )),
    )

    private val contactsFlow = MutableStateFlow(listOf(
        contact(1, "Andi Wijaya", "com.google", "+62812111"),
        contact(2, "Budi Santoso", "com.whatsapp"),
    ))

    private val fakeContacts = object : ContactsSource {
        override fun observeContacts(): Flow<List<Contact>> = contactsFlow
    }
    private val fakeAccounts = object : AccountsSource {
        override suspend fun getAccounts() = listOf(
            ContactAccount("acc", "com.google", AccountCapability.FULL_CRUD, 1),
            ContactAccount("acc", "com.whatsapp", AccountCapability.READ_ONLY, 1),
        )
    }

    private fun vm() = HomeViewModel(fakeContacts, fakeAccounts, dispatcher)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `emits all contacts and accounts`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.contacts.size)
        assertEquals(2, vm.uiState.value.accounts.size)
        job.cancel()
    }

    @Test fun `account filter keeps only contacts having raw contact in account`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        vm.selectAccount("com.whatsapp/acc")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Budi Santoso"), vm.uiState.value.contacts.map { it.displayName })
        job.cancel()
    }

    @Test fun `query matches name case-insensitive and phone substring`() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.uiState.collect {} }
        vm.setQuery("andi")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.contacts.size)
        vm.setQuery("812111")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Andi Wijaya"), vm.uiState.value.contacts.map { it.displayName })
        job.cancel()
    }
}
```

- [ ] **Step 3: Run test — verify fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.ui.home.HomeViewModelTest" -q`
Expected: FAIL — unresolved `HomeViewModel`.

- [ ] **Step 4: Implement HomeViewModel**

`app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeViewModel.kt`:
```kotlin
package com.ryccoatika.contactmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AccountsSource
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.di.DefaultDispatcher
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val contacts: List<Contact> = emptyList(),
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccountKey: String? = null,
    val query: String = "",
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contactsSource: ContactsSource,
    private val accountsSource: AccountsSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedAccountKey = MutableStateFlow<String?>(null)
    private val accounts = MutableStateFlow<List<ContactAccount>>(emptyList())

    init {
        viewModelScope.launch { accounts.value = accountsSource.getAccounts() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        contactsSource.observeContacts(), accounts, query, selectedAccountKey,
    ) { contacts, accounts, query, accountKey ->
        HomeUiState(
            contacts = contacts.filtered(query, accountKey),
            accounts = accounts,
            selectedAccountKey = accountKey,
            query = query,
            loading = false,
        )
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) { query.value = q }

    fun selectAccount(key: String?) { selectedAccountKey.value = key }

    private fun List<Contact>.filtered(query: String, accountKey: String?): List<Contact> {
        var result = this
        if (accountKey != null) {
            result = result.filter { contact ->
                contact.rawContacts.any { "${it.accountType}/${it.accountName}" == accountKey }
            }
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            val qDigits = q.filter { it.isDigit() }
            result = result.filter { contact ->
                contact.displayName.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && contact.rawContacts.any { raw ->
                        raw.phones.any { it.value.filter(Char::isDigit).contains(qDigits) }
                    }) ||
                    contact.rawContacts.any { raw -> raw.emails.any { it.value.contains(q, true) } }
            }
        }
        return result
    }
}
```

- [ ] **Step 5: Run test — verify passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ryccoatika.contactmanager.ui.home.HomeViewModelTest" -q`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: home viewmodel with account filter and search"
```

---

### Task 6: Permission gate + navigation + home screen UI

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/AppNav.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/permission/PermissionGate.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/common/AccountVisuals.kt`
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/MainActivity.kt`

**Interfaces:**
- Consumes: `HomeViewModel` (Task 5).
- Produces: `AppNav()` root composable; route constants `object Routes { const val HOME = "home" }` (later phases add routes here).

No unit tests (Compose UI); verified by build + manual run checklist in Step 6.

- [ ] **Step 1: Account visuals helper**

`ui/common/AccountVisuals.kt` — deterministic badge color + human label per account:
```kotlin
package com.ryccoatika.contactmanager.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

object AccountVisuals {
    private val palette = listOf(
        Color(0xFF4285F4), Color(0xFF0F9D58), Color(0xFFF4B400), Color(0xFFDB4437),
        Color(0xFF7B1FA2), Color(0xFF00838F), Color(0xFFEF6C00), Color(0xFF5D4037),
    )

    fun color(accountType: String?, accountName: String?): Color =
        palette[("$accountType/$accountName".hashCode().absoluteValue) % palette.size]

    fun label(accountType: String?, accountName: String?): String = when {
        accountType == null -> "Device"
        accountType == "com.google" -> accountName ?: "Google"
        accountType == "com.osp.app.signin" -> "Samsung"
        accountType == "com.whatsapp" -> "WhatsApp"
        accountType.startsWith("org.telegram") -> "Telegram"
        accountType.contains("sim", ignoreCase = true) -> "SIM"
        else -> accountName ?: accountType.substringAfterLast('.')
    }
}
```

- [ ] **Step 2: Permission gate**

`ui/permission/PermissionGate.kt`:
```kotlin
package com.ryccoatika.contactmanager.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val CONTACT_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
)

/** Shows [content] only when contact permissions are granted; otherwise rationale screen. */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(CONTACT_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.all { it }
        denied = !granted
    }

    if (granted) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Contacts, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Contact access needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This app organizes the contacts already on your device — across Google, Samsung, SIM and app accounts. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { launcher.launch(CONTACT_PERMISSIONS) }) {
            Text("Allow access")
        }
        if (denied) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open settings") }
        }
    }
}
```

- [ ] **Step 3: Home screen**

`ui/home/HomeScreen.kt`:
```kotlin
package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search ${state.contacts.size} contacts") },
                singleLine = true,
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountKey == null,
                        onClick = { viewModel.selectAccount(null) },
                        label = { Text("All") },
                    )
                }
                items(state.accounts, key = { it.key }) { account ->
                    FilterChip(
                        selected = state.selectedAccountKey == account.key,
                        onClick = { viewModel.selectAccount(account.key) },
                        label = { Text("${AccountVisuals.label(account.type, account.name)} · ${account.contactCount}") },
                        leadingIcon = {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(account.type, account.name)),
                            )
                        },
                    )
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.contacts, key = { it.contactId }) { contact ->
                    ContactRow(contact)
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        leadingContent = { ContactAvatar(contact) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                contact.rawContacts
                    .distinctBy { it.accountType to it.accountName }
                    .forEach { raw ->
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(AccountVisuals.color(raw.accountType, raw.accountName)),
                        )
                    }
            }
        },
    )
}

@Composable
private fun ContactAvatar(contact: Contact) {
    if (contact.photoThumbnailUri != null) {
        AsyncImage(
            model = contact.photoThumbnailUri,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.displayName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
```

Also add to `libs.versions.toml` `[libraries]` if missing:
```toml
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleViewmodelCompose" }
```
and `implementation(libs.androidx.lifecycle.runtime.compose)` in `app/build.gradle.kts` (provides `collectAsStateWithLifecycle`).

- [ ] **Step 4: Navigation root + MainActivity**

`ui/AppNav.kt`:
```kotlin
package com.ryccoatika.contactmanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryccoatika.contactmanager.ui.home.HomeScreen
import com.ryccoatika.contactmanager.ui.permission.PermissionGate

object Routes {
    const val HOME = "home"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    PermissionGate {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen() }
        }
    }
}
```

Replace `MainActivity.kt` setContent body:
```kotlin
package com.ryccoatika.contactmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ryccoatika.contactmanager.ui.AppNav
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactManagerTheme {
                AppNav()
            }
        }
    }
}
```
(Keep the existing theme composable name if it differs — check `ui/theme/Theme.kt` for the actual `*Theme` function and use it.)

- [ ] **Step 5: Build + full test suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 6: Manual run checklist (emulator/device)**

1. Fresh install: permission rationale screen appears, no crash.
2. Grant: contact list loads, alphabetical, badges shown.
3. Filter chip: list narrows to that account.
4. Search by name fragment and by digits: matches.
5. Add contact in system Contacts app while ours is open: list updates within ~1 s.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: permission gate, navigation, unified home list with filters"
```

---

## Self-review notes

- Spec coverage (Phase 1 scope only): permissions gate ✓ (Task 6), accounts enumeration + classification ✓ (Tasks 2, 4), contacts flow + ContentObserver + debounce ✓ (Task 4), bulk query + in-memory group-by ✓ (Tasks 3, 4), unified list UI with chips/search/badges ✓ (Tasks 5, 6). Alphabet fast-scroll rail and sticky headers deferred to Phase 6 polish (spec lists them under home screen — noted as debt in Phase 6 plan).
- Type consistency: `ContactsSource`/`AccountsSource` names used in Tasks 4 (as impl), 5 (as consumer). `DataRow.typeLabel` reserved for Phase 2 (labels in detail view); read as null in Phase 1.
- Later phases (separate plans): 2 = detail + editor + CRUD, 3 = move engine + multi-select + accounts screen, 4 = SIM, 5 = duplicates, 6 = polish.
