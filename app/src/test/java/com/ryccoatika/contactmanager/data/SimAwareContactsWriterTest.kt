package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.data.sim.FakeSimContactSource
import com.ryccoatika.contactmanager.data.sim.InMemorySimCapabilityCache
import com.ryccoatika.contactmanager.data.sim.ResolvedSimContact
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimAwareContactsWriterTest {

    private class FakeDelegate : ContactsWriter {
        var created: Triple<String?, String?, EditableContact>? = null
        var updated: Pair<Long, EditableContact>? = null
        val deleted = mutableListOf<List<Long>>()
        var copied: Triple<Long, String?, String?>? = null
        val moved = mutableListOf<Long>()
        var moveTarget: Pair<String?, String?>? = null
        var result: ContactOpResult = ContactOpResult.Success

        override suspend fun createContact(
            accountType: String?,
            accountName: String?,
            contact: EditableContact,
        ): ContactOpResult {
            created = Triple(accountType, accountName, contact)
            return result
        }

        override suspend fun updateRawContact(rawContactId: Long, contact: EditableContact): ContactOpResult {
            updated = rawContactId to contact
            return result
        }

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult {
            deleted += rawContactIds
            return result
        }

        override suspend fun copyRawContact(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult {
            copied = Triple(rawContactId, targetType, targetName)
            return result
        }

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult {
            moved += rawContactIds
            moveTarget = targetType to targetName
            rawContactIds.forEachIndexed { i, _ -> onProgress(i + 1, rawContactIds.size) }
            return result
        }
    }

    private class FakeIntegration(
        var resolved: Map<Long, ResolvedSimContact> = emptyMap(),
    ) : SimAccountsIntegration {
        override suspend fun simPseudoAccounts(existingAccounts: List<ContactAccount>) =
            emptyList<ContactAccount>()

        override suspend fun simContacts(existingContacts: List<Contact>) = emptyList<Contact>()

        override fun resolveSimContact(rawContactId: Long) = resolved[rawContactId]
    }

    private val simId = SimRouting.syntheticId(0)
    private val simEntry = SimContact(indexOnIcc = 1, name = "Budi", number = "0812", subscriptionId = null)

    private val providerContact = Contact(
        contactId = 1,
        displayName = "Citra Lestari",
        rawContacts = listOf(
            RawContact(
                rawContactId = 10,
                accountType = "com.google",
                accountName = "a@gmail.com",
                givenName = "Citra",
                familyName = "Lestari",
                phones = listOf(LabeledValue(1, "+62 812-9999", "Mobile")),
                emails = listOf(LabeledValue(2, "citra@x.com", null)),
            ),
        ),
    )

    private class Env(
        writable: Boolean = true,
        resolved: Map<Long, ResolvedSimContact> = emptyMap(),
        contacts: List<Contact> = emptyList(),
    ) {
        val delegate = FakeDelegate()
        val simSource = FakeSimContactSource(
            capsBySub = mapOf<Int?, SimCapabilities>(null to SimCapabilities(true, writable)),
        )
        val simRepository = SimRepository(simSource, InMemorySimCapabilityCache())
        val integration = FakeIntegration(resolved)
        val writer = SimAwareContactsWriter(
            delegate = delegate,
            simSource = simSource,
            simIntegration = integration,
            simRepository = simRepository,
            contactsSource = object : ContactsSource {
                override fun observeContacts(): Flow<List<Contact>> = flowOf(contacts)
            },
        )
    }

    private fun editable(name: String, phone: String?) = EditableContact(
        displayName = name,
        phones = phone?.let { listOf(it to null) } ?: emptyList(),
        emails = emptyList(),
        organization = null,
        note = null,
    )

    // --- createContact ---------------------------------------------------

    @Test fun `create with normal account delegates untouched`() = runTest {
        val env = Env()
        val result = env.writer.createContact("com.google", "a@gmail.com", editable("Budi", "0812"))
        assertEquals(ContactOpResult.Success, result)
        assertEquals("com.google", env.delegate.created!!.first)
        assertTrue(env.simSource.contactsBySub.isEmpty())
    }

    @Test fun `create with icc account inserts to sim with normalized number`() = runTest {
        val env = Env()
        val result = env.writer.createContact("icc/-1", "SIM", editable("Budi", "+62 812-345"))
        assertEquals(ContactOpResult.Success, result)
        assertNull(env.delegate.created)
        assertEquals(
            listOf(SimContact(null, "Budi", "+62812345", null)),
            env.simSource.contactsBySub[null],
        )
    }

    @Test fun `create with icc account fails validation for over-long name`() = runTest {
        val env = Env()
        val result = env.writer.createContact("icc/-1", "SIM", editable("A".repeat(15), "0812"))
        assertEquals(ContactOpResult.Failure("Name too long for SIM (max 14)."), result)
        assertTrue(env.simSource.contactsBySub.isEmpty())
    }

    @Test fun `create on read-only sim fails without touching provider`() = runTest {
        val env = Env(writable = false)
        val result = env.writer.createContact("icc/-1", "SIM", editable("Budi", "0812"))
        assertTrue(result is ContactOpResult.Failure)
        assertTrue(env.simSource.contactsBySub.isEmpty())
    }

    // --- update / delete ---------------------------------------------------

    @Test fun `update with negative id routes to sim update`() = runTest {
        val env = Env(resolved = mapOf(simId to ResolvedSimContact(null, simEntry)))
        env.simSource.contactsBySub[null] = mutableListOf(simEntry)
        val result = env.writer.updateRawContact(simId, editable("Budiman", "0899"))
        assertEquals(ContactOpResult.Success, result)
        assertNull(env.delegate.updated)
        assertEquals("Budiman", env.simSource.contactsBySub[null]!!.single().name)
        assertEquals("0899", env.simSource.contactsBySub[null]!!.single().number)
    }

    @Test fun `update with stale sim id fails gracefully`() = runTest {
        val env = Env()
        val result = env.writer.updateRawContact(simId, editable("Budi", "0812"))
        assertTrue(result is ContactOpResult.Failure)
    }

    @Test fun `update with positive id delegates`() = runTest {
        val env = Env()
        env.writer.updateRawContact(10, editable("Budi", "0812"))
        assertEquals(10L, env.delegate.updated!!.first)
    }

    @Test fun `delete splits sim and provider ids`() = runTest {
        val env = Env(resolved = mapOf(simId to ResolvedSimContact(null, simEntry)))
        env.simSource.contactsBySub[null] = mutableListOf(simEntry)
        val result = env.writer.deleteRawContacts(listOf(10L, simId, 20L))
        assertEquals(ContactOpResult.Success, result)
        assertEquals(listOf(listOf(10L, 20L)), env.delegate.deleted)
        assertTrue(env.simSource.contactsBySub[null]!!.isEmpty())
    }

    // --- copy ---------------------------------------------------------------

    @Test fun `copy provider contact to sim down-converts name and first phone`() = runTest {
        val env = Env(contacts = listOf(providerContact))
        val result = env.writer.copyRawContact(10, "icc/-1", "SIM")
        assertEquals(ContactOpResult.Success, result)
        assertEquals(
            SimContact(null, "Citra Lestari", "+628129999", null),
            env.simSource.contactsBySub[null]!!.single(),
        )
        assertNull(env.delegate.copied)
    }

    @Test fun `copy to sim fails when name exceeds sim limit`() = runTest {
        val longNamed = Contact(
            contactId = 2,
            displayName = "Bambang Pamungkas Setiawan",
            rawContacts = listOf(RawContact(11, "com.google", "a@gmail.com", phones = listOf(LabeledValue(1, "0812", null)))),
        )
        val env = Env(contacts = listOf(longNamed))
        val result = env.writer.copyRawContact(11, "icc/-1", "SIM")
        assertEquals(ContactOpResult.Failure("Name too long for SIM (max 14)."), result)
        assertTrue(env.simSource.contactsBySub.isEmpty())
    }

    @Test fun `copy sim contact to normal account creates via delegate`() = runTest {
        val env = Env(resolved = mapOf(simId to ResolvedSimContact(null, simEntry)))
        val result = env.writer.copyRawContact(simId, "com.google", "a@gmail.com")
        assertEquals(ContactOpResult.Success, result)
        val (type, name, contact) = env.delegate.created!!
        assertEquals("com.google", type)
        assertEquals("a@gmail.com", name)
        assertEquals("Budi", contact.displayName)
        assertEquals(listOf("0812" to null), contact.phones)
    }

    @Test fun `copy between normal accounts delegates`() = runTest {
        val env = Env()
        env.writer.copyRawContact(10, "com.google", "b@gmail.com")
        assertEquals(Triple(10L, "com.google", "b@gmail.com"), env.delegate.copied)
    }

    // --- move ----------------------------------------------------------------

    @Test fun `move sim contact to normal account creates then deletes sim entry`() = runTest {
        val env = Env(resolved = mapOf(simId to ResolvedSimContact(null, simEntry)))
        env.simSource.contactsBySub[null] = mutableListOf(simEntry)
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = env.writer.moveRawContacts(listOf(simId), "com.google", "a@gmail.com") { d, t ->
            progress += d to t
        }
        assertEquals(ContactOpResult.Success, result)
        assertEquals("Budi", env.delegate.created!!.third.displayName)
        assertTrue(env.simSource.contactsBySub[null]!!.isEmpty())
        assertEquals(listOf(1 to 1), progress)
    }

    @Test fun `move sim contact keeps sim entry when target copy fails`() = runTest {
        val env = Env(resolved = mapOf(simId to ResolvedSimContact(null, simEntry)))
        env.simSource.contactsBySub[null] = mutableListOf(simEntry)
        env.delegate.result = ContactOpResult.Failure("provider says no")
        val result = env.writer.moveRawContacts(listOf(simId), "com.google", "a@gmail.com") { _, _ -> }
        assertEquals(ContactOpResult.Failure("provider says no"), result)
        assertEquals(listOf(simEntry), env.simSource.contactsBySub[null])
    }

    @Test fun `move provider contact to sim inserts then deletes via delegate`() = runTest {
        val env = Env(contacts = listOf(providerContact))
        val result = env.writer.moveRawContacts(listOf(10L), "icc/-1", "SIM") { _, _ -> }
        assertEquals(ContactOpResult.Success, result)
        assertEquals("Citra Lestari", env.simSource.contactsBySub[null]!!.single().name)
        assertEquals(listOf(listOf(10L)), env.delegate.deleted)
    }

    @Test fun `mixed move to normal account splits sim and provider paths with total progress`() = runTest {
        val env = Env(
            resolved = mapOf(simId to ResolvedSimContact(null, simEntry)),
            contacts = listOf(providerContact),
        )
        env.simSource.contactsBySub[null] = mutableListOf(simEntry)
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = env.writer.moveRawContacts(listOf(simId, 10L), "com.google", "a@gmail.com") { d, t ->
            progress += d to t
        }
        assertEquals(ContactOpResult.Success, result)
        assertEquals(listOf(10L), env.delegate.moved)
        assertEquals("Budi", env.delegate.created!!.third.displayName)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test fun `pure provider move delegates directly`() = runTest {
        val env = Env()
        env.writer.moveRawContacts(listOf(10L, 20L), "com.google", "a@gmail.com") { _, _ -> }
        assertEquals(listOf(10L, 20L), env.delegate.moved)
        assertEquals("com.google" to "a@gmail.com", env.delegate.moveTarget)
    }

    // --- change notifications ---------------------------------------------

    @Test fun `successful sim write emits change signal`() = runTest {
        val env = Env()
        var ticks = 0
        val job = launch { env.simRepository.changes.collect { ticks++ } }
        testScheduler.advanceUntilIdle()
        env.writer.createContact("icc/-1", "SIM", editable("Budi", "0812"))
        testScheduler.advanceUntilIdle()
        assertTrue(ticks >= 1)
        job.cancel()
    }

    @Test fun `failed sim write does not emit change signal`() = runTest {
        val env = Env(writable = false)
        var ticks = 0
        val job = launch { env.simRepository.changes.collect { ticks++ } }
        testScheduler.advanceUntilIdle()
        env.writer.createContact("icc/-1", "SIM", editable("Budi", "0812"))
        testScheduler.advanceUntilIdle()
        assertEquals(0, ticks)
        job.cancel()
    }
}
