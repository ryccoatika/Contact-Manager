# Import / Export Contacts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** vCard export (per account / multi account / Home selection) and vCard import into one or several accounts, from Accounts settings and the Home selection bar.

**Architecture:** Pure `domain/vcard` writer+parser; `data/transfer` bridge (snapshot→vcf, vcf→`createContact`, batched import through a generalized `BatchRunner.run`); UI = menu items + sheets/dialogs on Accounts, an Export action on Home's selection bar, SAF launchers in the screens.

**Tech Stack:** Kotlin, Compose M3, Hilt, ContactsContract, SAF (`ActivityResultContracts`), JUnit4 JVM tests with hand-written fakes.

**Spec:** `docs/superpowers/specs/2026-09-17-import-export-design.md`

## Global Constraints

- No new dependencies (hand-rolled vCard; SAF tree files via `DocumentsContract`, not `DocumentFile`).
- Kotlin warnings are errors; ktlint via Spotless; file budget target ≤500 lines (hard cap 750).
- All provider/stream I/O on injected `Dispatchers.IO`; every provider call wrapped, errors as typed results → snackbars.
- All user-visible text in `res/values/strings_<screen>.xml`; plurals for counts; ViewModels use `StringProvider`.
- Analytics: counts/booleans only, never contact data. `TrackScreenView` on every new sheet/dialog.
- Gate after every task: `./gradlew compileDebugKotlin`; tests where the task has them. Before final commit: full `:app:testDebugUnitTest` + `spotlessCheck`.
- Conventional Commits with trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: VCardContact model + VCardWriter

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/vcard/VCard.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/vcard/VCardWriter.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/domain/vcard/VCardWriterTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin, no Android imports).
- Produces: `VCardContact` (fields exactly as below), `VCardParseResult(contacts, skippedCards)`, `object VCardWriter { fun write(contacts: List<VCardContact>): String }`.

- [ ] **Step 1: Write the model** (no test needed — data only)

```kotlin
// domain/vcard/VCard.kt
package com.ryccoatika.contactmanager.domain.vcard

/** One vCard's worth of contact data — mirrors what the app can store. */
data class VCardContact(
    val displayName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val phones: List<Pair<String, String?>> = emptyList(), // value to typeLabel
    val emails: List<Pair<String, String?>> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val nickname: String? = null,
    val websites: List<String> = emptyList(),
    val addresses: List<String> = emptyList(), // one formatted string each
    val birthday: String? = null,
    val anniversary: String? = null,
    val note: String? = null,
    val photo: ByteArray? = null,
) {
    // ByteArray needs manual equals for test assertions.
    override fun equals(other: Any?): Boolean = other is VCardContact &&
        displayName == other.displayName && givenName == other.givenName &&
        familyName == other.familyName && phones == other.phones &&
        emails == other.emails && organization == other.organization &&
        jobTitle == other.jobTitle && nickname == other.nickname &&
        websites == other.websites && addresses == other.addresses &&
        birthday == other.birthday && anniversary == other.anniversary &&
        note == other.note && photo.contentEquals(other.photo)

    override fun hashCode(): Int = displayName.hashCode()
}

/** Parser output: parsed cards plus how many malformed ones were skipped. */
data class VCardParseResult(
    val contacts: List<VCardContact>,
    val skippedCards: Int,
)
```

- [ ] **Step 2: Write failing writer tests**

```kotlin
// VCardWriterTest.kt
package com.ryccoatika.contactmanager.domain.vcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardWriterTest {
    @Test fun `writes minimal card with CRLF and version 3`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "Andi Wijaya")))
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Andi Wijaya\r\nN:;Andi Wijaya;;;\r\nEND:VCARD\r\n",
            out,
        )
    }

    @Test fun `writes N from family and given when present`() {
        val out = VCardWriter.write(
            listOf(VCardContact(displayName = "Andi Wijaya", givenName = "Andi", familyName = "Wijaya")),
        )
        assertTrue(out.contains("\r\nN:Wijaya;Andi;;;\r\n"))
    }

    @Test fun `writes all simple properties in stable order`() {
        val out = VCardWriter.write(
            listOf(
                VCardContact(
                    displayName = "Budi",
                    phones = listOf("+62812111" to "Mobile", "021555" to null),
                    emails = listOf("b@x.id" to "Home"),
                    organization = "PT Maju",
                    jobTitle = "CTO",
                    nickname = "Bud",
                    websites = listOf("https://x.id"),
                    addresses = listOf("Jl. Sudirman 1, Jakarta"),
                    birthday = "1990-08-12",
                    anniversary = "2015-01-02",
                    note = "VIP",
                ),
            ),
        )
        val lines = out.split("\r\n")
        val idx = { p: String -> lines.indexOfFirst { it.startsWith(p) } }
        assertTrue(idx("TEL;TYPE=Mobile:+62812111") in 0 until idx("TEL:021555"))
        assertTrue(idx("EMAIL;TYPE=Home:b@x.id") > 0)
        assertTrue(lines.contains("ORG:PT Maju"))
        assertTrue(lines.contains("TITLE:CTO"))
        assertTrue(lines.contains("NICKNAME:Bud"))
        assertTrue(lines.contains("URL:https://x.id"))
        assertTrue(lines.contains("ADR:;;Jl. Sudirman 1\\, Jakarta;;;;"))
        assertTrue(lines.contains("BDAY:1990-08-12"))
        assertTrue(lines.contains("X-ANNIVERSARY:2015-01-02"))
        assertTrue(lines.contains("NOTE:VIP"))
    }

    @Test fun `escapes backslash comma semicolon and newline in values`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A;B,C\\D", note = "l1\nl2")))
        assertTrue(out.contains("FN:A\\;B\\,C\\\\D"))
        assertTrue(out.contains("NOTE:l1\\nl2"))
    }

    @Test fun `folds lines longer than 75 octets with space continuation`() {
        val long = "x".repeat(200)
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A", note = long)))
        val noteBlock = out.substringAfter("NOTE:").substringBefore("END:VCARD")
        assertTrue(noteBlock.contains("\r\n "))
        out.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
    }

    @Test fun `embeds photo as base64 with b encoding`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A", photo = byteArrayOf(1, 2, 3))))
        assertTrue(out.contains("PHOTO;ENCODING=b;TYPE=JPEG:AQID"))
    }

    @Test fun `omits empty and null fields entirely`() {
        val out = VCardWriter.write(listOf(VCardContact(displayName = "A")))
        listOf("TEL", "EMAIL", "ORG", "TITLE", "NICKNAME", "URL", "ADR", "BDAY", "X-ANNIVERSARY", "NOTE", "PHOTO")
            .forEach { assertTrue("$it leaked", !out.contains("\r\n$it")) }
    }

    @Test fun `writes multiple cards back to back`() {
        val out = VCardWriter.write(listOf(VCardContact("A"), VCardContact("B")))
        assertEquals(2, Regex("BEGIN:VCARD").findAll(out).count())
    }
}
```

- [ ] **Step 3: Run to verify failure**

`./gradlew :app:testDebugUnitTest --tests '*VCardWriterTest*'` → compile error, `VCardWriter` unresolved.

- [ ] **Step 4: Implement VCardWriter**

```kotlin
// domain/vcard/VCardWriter.kt
package com.ryccoatika.contactmanager.domain.vcard

import java.util.Base64

/** Serializes contacts as vCard 3.0 (CRLF, 75-octet folding, deterministic order). */
object VCardWriter {
    fun write(contacts: List<VCardContact>): String = buildString {
        contacts.forEach { c ->
            line("BEGIN:VCARD"); line("VERSION:3.0")
            line("FN:${esc(c.displayName)}")
            line("N:${esc(c.familyName ?: "")};${esc(c.givenName ?: c.displayName.takeIf { c.familyName == null } ?: "")};;;")
            c.phones.forEach { (v, t) -> line(if (t.isNullOrBlank()) "TEL:${esc(v)}" else "TEL;TYPE=${esc(t)}:${esc(v)}") }
            c.emails.forEach { (v, t) -> line(if (t.isNullOrBlank()) "EMAIL:${esc(v)}" else "EMAIL;TYPE=${esc(t)}:${esc(v)}") }
            c.organization?.ifBlank { null }?.let { line("ORG:${esc(it)}") }
            c.jobTitle?.ifBlank { null }?.let { line("TITLE:${esc(it)}") }
            c.nickname?.ifBlank { null }?.let { line("NICKNAME:${esc(it)}") }
            c.websites.forEach { line("URL:${esc(it)}") }
            c.addresses.forEach { line("ADR:;;${esc(it)};;;;") }
            c.birthday?.ifBlank { null }?.let { line("BDAY:${esc(it)}") }
            c.anniversary?.ifBlank { null }?.let { line("X-ANNIVERSARY:${esc(it)}") }
            c.note?.ifBlank { null }?.let { line("NOTE:${esc(it)}") }
            c.photo?.takeIf { it.isNotEmpty() }
                ?.let { line("PHOTO;ENCODING=b;TYPE=JPEG:${Base64.getEncoder().encodeToString(it)}") }
            line("END:VCARD")
        }
    }

    private fun esc(v: String): String = v
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n")

    /** Appends [raw] folded to ≤75 octets per physical line (RFC 2426 §2.6). */
    private fun StringBuilder.line(raw: String) {
        var rest = raw
        var first = true
        while (true) {
            val budget = if (first) 75 else 74
            val bytes = rest.toByteArray(Charsets.UTF_8)
            if (bytes.size <= budget) break
            var cut = budget
            while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut-- // don't split UTF-8
            val head = String(bytes, 0, cut, Charsets.UTF_8)
            append(if (first) head else " $head"); append("\r\n")
            rest = String(bytes, cut, bytes.size - cut, Charsets.UTF_8)
            first = false
        }
        append(if (first) rest else " $rest"); append("\r\n")
    }
}
```

Note: the `N:` fallback puts the display name in the given slot only when no family name exists — matches test 1 (`N:;Andi Wijaya;;;`) and test 2 (`N:Wijaya;Andi;;;`). Adjust implementation until both pass; do NOT weaken the tests.

- [ ] **Step 5: Run tests until green**, then `./gradlew compileDebugKotlin`
- [ ] **Step 6: Commit** `feat(domain): vCard 3.0 model and writer`

---

### Task 2: VCardParser

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/domain/vcard/VCardParser.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/domain/vcard/VCardParserTest.kt`

**Interfaces:**
- Consumes: `VCardContact`, `VCardParseResult`, `VCardWriter` (round-trip test).
- Produces: `object VCardParser { fun parse(text: String): VCardParseResult }`.

- [ ] **Step 1: Write failing parser tests**

```kotlin
package com.ryccoatika.contactmanager.domain.vcard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VCardParserTest {
    @Test fun `round trips every field through the writer`() {
        val original = listOf(
            VCardContact(
                displayName = "Budi Santoso", givenName = "Budi", familyName = "Santoso",
                phones = listOf("+62812111" to "Mobile", "021555" to null),
                emails = listOf("b@x.id" to "Home"),
                organization = "PT Maju", jobTitle = "CTO", nickname = "Bud",
                websites = listOf("https://x.id"),
                addresses = listOf("Jl. Sudirman 1, Jakarta"),
                birthday = "1990-08-12", anniversary = "2015-01-02",
                note = "line1\nline2; with, punctuation\\",
                photo = ByteArray(64) { it.toByte() },
            ),
            VCardContact(displayName = "Siti"),
        )
        val parsed = VCardParser.parse(VCardWriter.write(original))
        assertEquals(0, parsed.skippedCards)
        assertEquals(original, parsed.contacts)
    }

    @Test fun `parses vcard 21 quoted printable with soft breaks`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\n" +
            "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:;=41=6E=64=\r\n=69\r\n" +
            "TEL;CELL:+62812\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("Andi", c.givenName)
        assertEquals("Andi", c.displayName) // FN absent: joined N
        assertEquals(listOf("+62812" to "CELL"), c.phones)
    }

    @Test fun `parses vcard 21 base64 photo with continuation lines`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:A\r\n" +
            "PHOTO;ENCODING=BASE64;JPEG:AQID\r\n BAU=\r\n\r\nEND:VCARD\r\n"
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), VCardParser.parse(vcf).contacts.single().photo)
    }

    @Test fun `parses 40 basics ANNIVERSARY and item groups`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:A\r\n" +
            "ANNIVERSARY:20150102\r\nitem1.TEL:+62899\r\nitem1.X-ABLabel:Work\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("20150102", c.anniversary)
        assertEquals("+62899", c.phones.single().first)
    }

    @Test fun `unknown properties are skipped`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\r\nX-WEIRD:zzz\r\nIMPP:sip:a@b\r\nEND:VCARD\r\n"
        assertEquals("A", VCardParser.parse(vcf).contacts.single().displayName)
    }

    @Test fun `malformed card is skipped and counted, good ones survive`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Good\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:NoEnd\r\n" // truncated
        val r = VCardParser.parse(vcf)
        assertEquals(listOf("Good"), r.contacts.map { it.displayName })
        assertEquals(1, r.skippedCards)
    }

    @Test fun `card without FN and without N is skipped as malformed`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nTEL:1\r\nEND:VCARD\r\n"
        val r = VCardParser.parse(vcf)
        assertEquals(0, r.contacts.size)
        assertEquals(1, r.skippedCards)
    }

    @Test fun `tolerates LF-only line endings and lowercase property names`() {
        val vcf = "begin:vcard\nversion:3.0\nfn:Lima\ntel;type=cell:+1\nend:vcard\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("Lima", c.displayName)
        assertEquals(listOf("+1" to "cell"), c.phones)
    }

    @Test fun `empty input parses to empty result`() {
        val r = VCardParser.parse("")
        assertEquals(0, r.contacts.size)
        assertEquals(0, r.skippedCards)
    }

    @Test fun `ADR maps middle component and TYPE params carry to labels`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\r\nADR;TYPE=HOME:;;Street 1;Town;;12345;ID\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals(listOf("Street 1, Town, 12345, ID"), c.addresses)
    }

    @Test fun `unescapes values`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:A\\;B\\,C\\\\D\r\nNOTE:l1\\nl2\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(vcf).contacts.single()
        assertEquals("A;B,C\\D", c.displayName)
        assertEquals("l1\nl2", c.note)
    }
}
```

- [ ] **Step 2: Run to verify failure** (unresolved `VCardParser`).

- [ ] **Step 3: Implement**

Implementation outline (single object, ~200 lines — keep private helpers small):

```kotlin
// domain/vcard/VCardParser.kt
package com.ryccoatika.contactmanager.domain.vcard

import java.util.Base64

/** Tolerant vCard reader: 2.1 (QP/BASE64/CHARSET), 3.0, 4.0 basics. */
object VCardParser {
    fun parse(text: String): VCardParseResult {
        val logical = unfold(text)
        // Split into card line-lists on BEGIN:VCARD/END:VCARD (case-insensitive).
        // A BEGIN without matching END → that trailing card is malformed.
        val cards = mutableListOf<List<String>>(); var skipped = 0
        var current: MutableList<String>? = null
        logical.forEach { l ->
            when {
                l.equals("BEGIN:VCARD", true) -> { if (current != null) skipped++; current = mutableListOf() }
                l.equals("END:VCARD", true) -> { current?.let(cards::add); current = null }
                else -> current?.add(l)
            }
        }
        if (current != null) skipped++
        val contacts = cards.mapNotNull { lines -> card(lines) ?: run { skipped++; null } }
        return VCardParseResult(contacts, skipped)
    }

    /** Unfolds continuations: 3.0 "CRLF + space/tab"; 2.1 QP soft break "=<CRLF>"
     *  and 2.1 BASE64 blank-line-terminated continuation are handled here too. */
    private fun unfold(text: String): List<String> { /* normalize \r\n|\n, join folded lines */ }

    /** One property line -> (group?, name, params[List<Pair>], rawValue). */
    private fun splitProperty(line: String): Prop? { /* name up to first ':' outside params; strip "item1." group */ }

    /** Decode value: QUOTED-PRINTABLE per param, CHARSET per param, then unescape \n \, \; \\ . */
    private fun decode(prop: Prop): String { ... }

    private fun card(lines: List<String>): VCardContact? {
        // Fold properties into a builder: FN, N (split on unescaped ';' → family,given),
        // TEL/EMAIL (+ TYPE= param or bare 2.1 param like CELL as label),
        // ORG (first component), TITLE, NICKNAME, URL, ADR (components joined
        // ", " skipping blanks), BDAY, ANNIVERSARY|X-ANNIVERSARY, NOTE,
        // PHOTO (ENCODING=b|BASE64 → Base64.getMimeDecoder()).
        // displayName = FN, else "given family" trimmed; both absent -> null (malformed).
    }
}
```

The executor writes the full bodies; every behavior above already has a test from Step 1 — implement until the whole class passes. Do not add features without a test (YAGNI: no GEO, no TZ, no KIND).

- [ ] **Step 4: Run tests until green; run the writer suite too** (`--tests '*VCard*'`).
- [ ] **Step 5: Commit** `feat(domain): tolerant vCard parser (2.1/3.0/4.0 basics)`

---

### Task 3: BatchRunner.run generalization

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/data/BatchOperationManager.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/data/BatchOperationManagerTest.kt`

**Interfaces:**
- Produces: `BatchRunner.run(total: Int, label: String, finishedMessage: String, operation: suspend (onProgress: (Int, Int) -> Unit) -> ContactOpResult): Boolean` — Task 5/6's import uses exactly this.
- `moveContacts` / `copyContacts` keep their signatures (existing callers untouched).

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `run drives progress and finishes with the given message`() = runTest(dispatcher) {
    val manager = BatchOperationManager(FakeWriter(), CoroutineScope(SupervisorJob() + dispatcher))
    val started = manager.run(total = 2, label = "L", finishedMessage = "done!") { onProgress ->
        onProgress(1, 2); onProgress(2, 2); ContactOpResult.Success
    }
    assertTrue(started)
    dispatcher.scheduler.advanceUntilIdle()
    val p = manager.progress.value!!
    assertTrue(p.finished); assertEquals("done!", p.finishedMessage); assertNull(p.error)
}

@Test fun `run refuses while another batch is active`() = runTest(dispatcher) {
    val manager = BatchOperationManager(FakeWriter(), CoroutineScope(SupervisorJob() + dispatcher))
    manager.run(1, "L", "m") { kotlinx.coroutines.awaitCancellation() }
    assertFalse(manager.run(1, "L2", "m2") { ContactOpResult.Success })
    manager.cancel()
}
```

(Match the file's existing test setup — dispatcher, FakeWriter — copy the established pattern in that file.)

- [ ] **Step 2: Verify failure** (`run` unresolved on interface).
- [ ] **Step 3: Implement** — add `run` to the `BatchRunner` interface with the exact KDoc `/** Runs an arbitrary counted batch; false when another batch is running. */`; in `BatchOperationManager` rename the existing private `start(...)` to the public `override fun run(...)` (same body), and make `moveContacts`/`copyContacts` call `run(...)`.
- [ ] **Step 4: Full file's tests green** (`--tests '*BatchOperationManagerTest*'`).
- [ ] **Step 5: Commit** `ref(data): expose BatchRunner.run for arbitrary counted batches`

---

### Task 4: EditableContact photo + createContact photo row

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/data/ContactsWriteRepository.kt`

**Interfaces:**
- Produces: `EditableContact(..., val photo: ByteArray? = null)`. `createContact` writes a `CommonDataKinds.Photo` row when `photo != null`.

- [ ] **Step 1: Add the field** with default `null` (no call sites change).
- [ ] **Step 2: In `createContact`'s op-building block**, alongside the existing data rows, add:

```kotlin
contact.photo?.takeIf { it.isNotEmpty() }?.let { bytes ->
    ops += ContentProviderOperation
        .newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
        .withValue(Photo.PHOTO, bytes)
        .build()
}
```

(Adapt to the exact builder style already used for phones/emails in that method — read it first; the back-reference index must match the method's existing pattern.)

- [ ] **Step 3: `./gradlew compileDebugKotlin :app:testDebugUnitTest`** — provider write itself is not JVM-testable; the compile + existing suites are the gate here.
- [ ] **Step 4: Commit** `feat(data): photo support on contact create`

---

### Task 5: ContactTransfer — export

**Files:**
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/transfer/ContactTransfer.kt` (interface + result types)
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/transfer/DefaultContactTransfer.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/transfer/TransferFiles.kt` (SAF seam + Android impl)
- Create: `app/src/main/java/com/ryccoatika/contactmanager/data/transfer/ContactPhotoSource.kt` (photo seam + provider impl)
- Test: `app/src/test/java/com/ryccoatika/contactmanager/data/transfer/DefaultContactTransferTest.kt`
- Test helper: `app/src/test/java/com/ryccoatika/contactmanager/data/transfer/FakeTransferFiles.kt`

**Interfaces:**

```kotlin
// ContactTransfer.kt  (package com.ryccoatika.contactmanager.data.transfer)
sealed interface TransferResult {
    data class Success(val contactCount: Int, val fileCount: Int) : TransferResult
    data class Failure(val message: String) : TransferResult
}

sealed interface ParseOutcome {
    data class Parsed(val contacts: List<VCardContact>, val skippedCards: Int) : ParseOutcome
    data class Failure(val message: String) : ParseOutcome
}

interface ContactTransfer {
    suspend fun exportAccounts(accounts: List<ContactAccount>, uri: Uri): TransferResult
    suspend fun exportAccountsToFolder(accounts: List<ContactAccount>, treeUri: Uri): TransferResult
    suspend fun exportRawContacts(rawContactIds: List<Long>, uri: Uri): TransferResult
    suspend fun parseFile(uri: Uri): ParseOutcome
    fun startImport(contacts: List<VCardContact>, targets: List<ContactAccount>): Boolean
}

// TransferFiles.kt — the only place that touches ContentResolver/DocumentsContract.
interface TransferFiles {
    fun openWrite(uri: Uri): OutputStream?
    fun openRead(uri: Uri): InputStream?
    /** Creates "<displayName>.vcf" inside the SAF tree; null on failure. */
    fun createInTree(treeUri: Uri, displayName: String): Uri?
    fun delete(uri: Uri)
}

// ContactPhotoSource.kt
interface ContactPhotoSource {
    suspend fun photoOf(rawContactId: Long): ByteArray?
}
```

- Consumes: `ContactsSource.snapshot()`, `VCardWriter`, `StringProvider`, `@IoDispatcher`-style injected dispatcher (find the exact qualifier used by `ContactsRepository` and reuse it), `AccountClassifier` not needed here.
- Produces for Task 6/8/10: everything above plus `DefaultContactTransfer` constructor `(contactsSource, writer: ContactsWriter, batchRunner: BatchRunner, files: TransferFiles, photos: ContactPhotoSource, strings: StringProvider, ioDispatcher)`.

- [ ] **Step 1: Failing export tests** (fakes: reuse the `ContactsSource` fake pattern from `HomeViewModelTest`; `FakeTransferFiles` records writes into `ByteArrayOutputStream`s keyed by uri string; fake `ContactPhotoSource` returns a byte array for chosen ids). Cover:

```
`exportAccounts writes every raw contact of the accounts to one file`
`exportAccounts attaches photos from the photo source`
`exportRawContacts writes exactly the given raw contacts`
`export uses contact displayName when raw has no given or family name`
`exportAccountsToFolder writes one file per account named after its label, sanitized`
`exportAccountsToFolder appends numeric suffix on display-name collision`
`export returns Failure with message when the stream cannot be opened`
`export deletes the partial single file when writing throws`
```

Name sanitization rule (test it): replace `/\\:*?"<>|` and control chars with `_`, trim, fall back to `"contacts"` when blank; collision → `name (2)`, `name (3)`.
Mapping rule (test via written vcf content, parsed back with `VCardParser` for assertions): `RawContact` → `VCardContact` copies phones/emails as `value to typeLabel`, `givenName`/`familyName` verbatim, displayName = `listOfNotNull(given, family).joinToString(" ")` else parent `Contact.displayName`.

- [ ] **Step 2: Verify failure.**
- [ ] **Step 3: Implement `DefaultContactTransfer` export half + `AndroidTransferFiles` + provider `ContactPhotoSource`** (`@Singleton`, `@Inject` constructors). Android impls:

```kotlin
class AndroidTransferFiles @Inject constructor(@ApplicationContext private val context: Context) : TransferFiles {
    override fun openWrite(uri: Uri) = context.contentResolver.openOutputStream(uri, "wt")
    override fun openRead(uri: Uri) = context.contentResolver.openInputStream(uri)
    override fun createInTree(treeUri: Uri, displayName: String): Uri? = runCatching {
        DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
            "text/x-vcard",
            displayName,
        )
    }.getOrNull()
    override fun delete(uri: Uri) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
}

class ProviderContactPhotoSource @Inject constructor(
    @ApplicationContext private val context: Context,
    /* same IO dispatcher qualifier as the repositories */
) : ContactPhotoSource {
    override suspend fun photoOf(rawContactId: Long): ByteArray? = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.query(
                Data.CONTENT_URI, arrayOf(Photo.PHOTO),
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE), null,
            )?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
        }.getOrNull()
    }
}
```

New user-facing failure strings go in `strings_data.xml` (pattern: existing `cwr_*` ids) — e.g. `transfer_error_open_file`, `transfer_error_write`.

- [ ] **Step 4: Tests green; compile.**
- [ ] **Step 5: Commit** `feat(data): vCard export via ContactTransfer`

---

### Task 6: ContactTransfer — parse + import

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/data/transfer/DefaultContactTransfer.kt`
- Test: extend `DefaultContactTransferTest.kt`

**Interfaces:**
- Consumes: `VCardParser`, `BatchRunner.run` (Task 3), `ContactsWriter.createContact` (SIM routing already inside `SimAwareContactsWriter`), `EditableContact.photo` (Task 4).
- Produces: working `parseFile` / `startImport` per the interface in Task 5.

- [ ] **Step 1: Failing tests**

```
`parseFile returns contacts and skipped count`
`parseFile returns Failure when stream is null`
`parseFile returns Failure when zero cards parse`   // whole-file garbage
`startImport creates every contact in every target account`  // 2 cards × 2 targets = 4 createContact calls with right type/name
`startImport maps vcard fields into EditableContact including photo`
`startImport counts failures and finishes with imported-x-of-y message`
`startImport returns false while another batch runs`
```

Fake writer records `createContact(accountType, accountName, contact)` calls; make selected calls return `ContactOpResult.Failure("x")` for the partial test. Import runs through a real `BatchOperationManager` on the test dispatcher (established pattern in `HomeViewModelTest`).

Mapping `VCardContact` → `EditableContact`:

```kotlin
EditableContact(
    displayName = vc.displayName,
    phones = vc.phones, emails = vc.emails,
    organization = vc.organization, note = vc.note, jobTitle = vc.jobTitle,
    nickname = vc.nickname, websites = vc.websites, addresses = vc.addresses,
    birthday = vc.birthday, anniversary = vc.anniversary, photo = vc.photo,
)
```

Finished message: success → `getQuantity(R.plurals.transfer_imported, total, total)`; partial → the operation returns `ContactOpResult.Failure(strings.get(R.string.transfer_imported_partial, ok, total))` so the batch bar's error slot carries it (same envelope move/copy use).

- [ ] **Step 2: Verify failure. Step 3: Implement. Step 4: Green + compile.**
- [ ] **Step 5: Commit** `feat(data): vCard parse and batched import`

---

### Task 7: DI + analytics events

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/di/DataModule.kt`
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/domain/analytics/AnalyticsEvent.kt`

**Interfaces:**
- Produces bindings: `ContactTransfer→DefaultContactTransfer`, `TransferFiles→AndroidTransferFiles`, `ContactPhotoSource→ProviderContactPhotoSource` (all `@Binds`, style of the file). Analytics:

```kotlin
data class ContactsExport(
    val count: Int, val accountCount: Int, val perAccountFiles: Boolean,
) : AnalyticsEvent("contacts_export", mapOf("count" to count, "account_count" to accountCount, "per_account_files" to perAccountFiles))

data class ContactsImport(
    val count: Int, val accountCount: Int, val simTarget: Boolean,
) : AnalyticsEvent("contacts_import", mapOf("count" to count, "account_count" to accountCount, "sim_target" to simTarget))
```

- [ ] **Step 1: Add; compile; commit** `feat(di): wire ContactTransfer` (no tests — declarative).

---

### Task 8: AccountsViewModel flows

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/accounts/AccountsViewModel.kt`
- Test: `app/src/test/java/com/ryccoatika/contactmanager/ui/accounts/AccountsViewModelTest.kt`

**Interfaces (produced for Task 9's screen):**

```kotlin
/** Parsed import waiting for the user's confirm. */
data class PendingImport(
    val targets: List<ContactAccount>,
    val contacts: List<VCardContact>,
    val skippedCards: Int,
)

val pendingImport: StateFlow<PendingImport?>
fun exportToFile(accounts: List<ContactAccount>, uri: Uri)        // combined file
fun exportToFolder(accounts: List<ContactAccount>, treeUri: Uri)  // one file per account
fun requestImport(targets: List<ContactAccount>, uri: Uri)        // parse -> pendingImport or error snackbar
fun dismissPendingImport()
fun confirmPendingImport()                                        // startImport, snackbar started/busy
```

- Consumes: `ContactTransfer` (new constructor dep — update the test's `vm(...)` builder and every other VM test that constructs `AccountsViewModel`), `Analytics`, `StringProvider`, existing `UiEvent.ShowSnackbar` flow.

- [ ] **Step 1: Failing tests**

```
`exportToFile emits count snackbar on success and logs contacts_export`
`exportToFile emits failure message on Failure`
`exportToFolder logs perAccountFiles true`
`requestImport parks PendingImport on successful parse`
`requestImport emits error snackbar and parks nothing on parse failure`
`confirmPendingImport starts import, clears pending, logs contacts_import with simTarget flag`
`confirmPendingImport emits busy snackbar when batch already running`
```

Fake `ContactTransfer` records calls, returns scripted results. simTarget flag = `targets.any { it.capability == AccountCapability.SIM }`.

- [ ] **Step 2: Verify failure. Step 3: Implement** (every method `viewModelScope.launch`; snackbars: `accounts_msg_exported` plural / failure message verbatim / `accounts_msg_import_started` / `accounts_msg_busy` reuse). **Step 4: Green.**
- [ ] **Step 5: Commit** `feat(ui): accounts export/import view-model flows`

---

### Task 9: Accounts screen UI

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/accounts/AccountsScreen.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/accounts/TransferSheets.kt` (keeps AccountsScreen under budget)
- Modify: `app/src/main/res/values/strings_accounts.xml`, `strings_accounts_messages.xml`

**Interfaces:**
- Consumes: Task 8's VM API; `SelectionAccountEntry`-style checkbox-sheet pattern (copy the visual idiom from `DeleteAccountsSheet` in `ui/home/HomeDialogs.kt` — `ListItem` + `AccountDot` + `Checkbox`, whole-row `toggleable`); `TrackScreenView`.
- Produces: composables in `TransferSheets.kt`:

```kotlin
internal fun TransferAccountsSheet(          // global menu step 1 (export & import share it)
    mode: TransferMode,                      // enum class TransferMode { EXPORT, IMPORT } (declare here)
    accounts: List<ContactAccount>,          // all visible accounts, precheck all
    onConfirm: (List<ContactAccount>) -> Unit,
    onDismiss: () -> Unit,
)                                            // TrackScreenView("export_account_picker"/"import_account_picker")
internal fun ExportModeDialog(               // combined vs per-account; only when accounts.size > 1
    onOneFile: () -> Unit, onPerAccount: () -> Unit, onDismiss: () -> Unit,
)                                            // TrackScreenView("export_mode_dialog")
internal fun ImportConfirmDialog(
    pending: PendingImport,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
)                                            // TrackScreenView("import_confirm"); body: N contacts into target(s);
                                             // skippedCards>0 line; SIM warning line when a target is SIM
```

- [ ] **Step 1: Build the sheets/dialog file** (previews optional per file convention — Accounts screens carry previews; add light+dark for the sheet).
- [ ] **Step 2: Wire AccountsScreen:**
  - Row menu: two new `DropdownMenuItem`s under Copy — *Export contacts…* / *Import contacts…* → set `exportRequest = listOf(account)` / `importTargets = listOf(account)`.
  - Top bar: `IconButton(Icons.Default.MoreVert)` + `DropdownMenu` with the same two items → open `TransferAccountsSheet(EXPORT/IMPORT)`; sheet confirm sets the same two states.
  - SAF launchers (`rememberLauncherForActivityResult`):
    - `CreateDocument("text/x-vcard")` — suggested name `"contacts-" + LocalDate.now() + ".vcf"` for multi, `"<sanitized label>-" + LocalDate.now() + ".vcf"` for single (use `AccountVisuals.label`).
    - `OpenDocumentTree()` for per-account mode.
    - `OpenDocument()` launched with `arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")`.
  - Flow state (composable `remember`, mirrors `pendingDeleteBreakdown` pattern): `exportRequest: List<ContactAccount>?` (>1 → `ExportModeDialog` first), `importTargets: List<ContactAccount>?` (launch OpenDocument immediately). Null-out state when a launcher returns null uri (user cancelled).
  - `pendingImport` collected → `ImportConfirmDialog`.
- [ ] **Step 3: Strings** — draft ids: `accounts_export_contacts`, `accounts_import_contacts`, `accounts_transfer_menu` (top-bar contentDescription), `accounts_export_pick_title` / `accounts_import_pick_title`, `accounts_export_mode_title/_one_file/_per_account`, `accounts_import_confirm_title` (+plural), `accounts_import_skipped` plural, `accounts_import_sim_warning`, `accounts_msg_exported` plural, `accounts_msg_import_started`, `accounts_msg_import_none`.
- [ ] **Step 4: `compileDebugKotlin` + `spotlessApply`.**
- [ ] **Step 5: Commit** `feat(ui): accounts export/import menus, sheets and SAF flow`

---

### Task 10: Home selection export

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeChrome.kt` (`SelectionBottomBar` + `onExport`)
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings_home.xml` (+`_messages`)
- Test: `app/src/test/java/com/ryccoatika/contactmanager/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `HomeViewModel.exportSelected(uri: Uri)` — exports **all** selected raw contacts (read-only included), clears selection after starting, snackbar with count or failure, logs `ContactsExport(count, accountCount = breakdown size, perAccountFiles = false)`.
- Consumes: `ContactTransfer` (new constructor dep — update `vm(...)` builder in the test file), Task 5's `exportRawContacts`.

- [ ] **Step 1: Failing tests**

```
`exportSelected exports every selected raw id including read-only and clears selection`
`exportSelected emits failure snackbar on Failure`
```

- [ ] **Step 2: Verify failure. Step 3: Implement VM.**
- [ ] **Step 4: UI wiring** — `SelectionBottomBar` gains `onExport` `TextButton` (string `home_export`) between Copy and Delete; `HomeScreen` adds a `CreateDocument("text/x-vcard")` launcher (suggested name `"contacts-" + LocalDate.now() + ".vcf"`); wrap the bar's Row in `horizontalScroll(rememberScrollState())` so five actions never clip.
- [ ] **Step 5: Green + compile. Step 6: Commit** `feat(ui): export selected contacts from Home`

---

### Task 11: Copy pass, changelog, gates, PR

**Files:**
- Modify: all new strings files, `CHANGELOG.md`

- [ ] **Step 1: ux-writer subagent pass** over every string added in Tasks 5/9/10 + the changelog bullet (voice: sentence case, "entries"/"contacts"/"accounts" terminology, no jargon).
- [ ] **Step 2: `update-changelog` skill** — `[Unreleased]` bullet: export/import contacts as vCard files, per account, across accounts, or for a selection; imports into any account including SIM.
- [ ] **Step 3: Full gates** — `./gradlew compileDebugKotlin :app:testDebugUnitTest spotlessCheck :app:lintDebug`.
- [ ] **Step 4: `/architecture-audit`** (new package `data/transfer` — verify layering fences hold: ui→data→domain only).
- [ ] **Step 5: Secrets guard, commit remaining files** `feat(ui): finish import/export copy + changelog`, push `feat/import-export`, PR to `develop` with the generated-with trailer.

---

## Self-review notes

- Spec coverage: engine (T1/T2), BatchRunner (T3), photo write (T4), export incl. per-account files + sanitize + partial-file cleanup (T5), parse/import incl. SIM via existing writer routing + partial counts (T6), DI/analytics (T7), Accounts flows account-first (T8/T9), export-mode ask (T9), Home selection export (T10), strings/changelog/gates (T11). SIM export = plain snapshot rows — covered by T5 filtering, no special task needed.
- Type consistency: `TransferResult`/`ParseOutcome`/`PendingImport`/`TransferMode` defined once (T5/T8/T9) and consumed by name elsewhere.
- No placeholder steps: T2 Step 3 outlines the parser but every required behavior is pinned by the Step 1 tests, which are complete.
