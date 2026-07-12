package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSimContactSource(
    private val capsBySub: Map<Int?, SimCapabilities> = emptyMap(),
    val contactsBySub: MutableMap<Int?, MutableList<SimContact>> = mutableMapOf(),
) : SimContactSource {
    var probes = 0

    override suspend fun probe(subscriptionId: Int?): SimCapabilities {
        probes++
        return capsBySub[subscriptionId] ?: SimCapabilities(canRead = false, canWrite = false)
    }

    override suspend fun readAll(subscriptionId: Int?): List<SimContact> =
        contactsBySub[subscriptionId].orEmpty()

    override suspend fun insert(subscriptionId: Int?, name: String, number: String): ContactOpResult {
        contactsBySub.getOrPut(subscriptionId) { mutableListOf() } +=
            SimContact(null, name, number, subscriptionId)
        return ContactOpResult.Success
    }

    override suspend fun update(
        subscriptionId: Int?,
        original: SimContact,
        name: String,
        number: String,
    ): ContactOpResult {
        val list = contactsBySub[subscriptionId] ?: return ContactOpResult.Failure("no sim")
        val index = list.indexOfFirst { it.name == original.name && it.number == original.number }
        if (index < 0) return ContactOpResult.Failure("not found")
        list[index] = list[index].copy(name = name, number = number)
        return ContactOpResult.Success
    }

    override suspend fun delete(subscriptionId: Int?, contact: SimContact): ContactOpResult {
        val removed = contactsBySub[subscriptionId]
            ?.removeIf { it.name == contact.name && it.number == contact.number } == true
        return if (removed) ContactOpResult.Success else ContactOpResult.Failure("not found")
    }
}

class InMemorySimCapabilityCache : SimCapabilityCache {
    private val map = mutableMapOf<Int?, SimCapabilities>()
    override suspend fun get(subId: Int?): SimCapabilities? = map[subId]
    override suspend fun set(subId: Int?, caps: SimCapabilities) { map[subId] = caps }
}

class FakeSimSubscriptionsSource(
    private val subscriptions: List<SimSubscription> = listOf(SimSubscription(null, "SIM")),
) : SimSubscriptionsSource {
    override suspend fun activeSubscriptions(): List<SimSubscription> = subscriptions
}

class SimAccountsIntegrationTest {

    private val dualSim = listOf(
        SimSubscription(1, "SIM 1 · Telkomsel"),
        SimSubscription(2, "SIM 2 · XL"),
    )

    private fun integration(
        source: FakeSimContactSource,
        subs: List<SimSubscription> = dualSim,
    ) = IccSimAccountsIntegration(
        subscriptionsSource = FakeSimSubscriptionsSource(subs),
        simRepository = SimRepository(source, InMemorySimCapabilityCache()),
        simSource = source,
    )

    private fun nativeSimAccount() =
        ContactAccount("SIM", "vnd.sec.contact.sim", AccountCapability.SIM, 3)

    private fun nativeSimContact() = Contact(
        contactId = 5,
        displayName = "On Samsung SIM",
        rawContacts = listOf(RawContact(50, "vnd.sec.contact.sim", "SIM")),
    )

    @Test fun `no pseudo accounts when a native SIM account already exists`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(1 to SimCapabilities(true, true)),
            contactsBySub = mutableMapOf(1 to mutableListOf(SimContact(1, "Budi", "0812", 1))),
        )
        val result = integration(source).simPseudoAccounts(listOf(nativeSimAccount()))
        assertTrue(result.isEmpty())
        assertEquals(0, source.probes)
    }

    @Test fun `pseudo account per readable subscription with count and writability`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(
                1 to SimCapabilities(canRead = true, canWrite = true),
                2 to SimCapabilities(canRead = true, canWrite = false),
            ),
            contactsBySub = mutableMapOf(
                1 to mutableListOf(
                    SimContact(1, "Budi", "0812", 1),
                    SimContact(2, "Citra", "0813", 1),
                ),
                2 to mutableListOf(SimContact(1, "Dewi", "0814", 2)),
            ),
        )
        val accounts = integration(source).simPseudoAccounts(emptyList())
        assertEquals(2, accounts.size)
        val (sim1, sim2) = accounts
        assertEquals("icc/1", sim1.type)
        assertEquals("SIM 1 · Telkomsel", sim1.name)
        assertEquals(AccountCapability.SIM, sim1.capability)
        assertEquals(2, sim1.contactCount)
        assertTrue(sim1.writable)
        assertEquals("icc/2", sim2.type)
        assertEquals(1, sim2.contactCount)
        assertTrue(!sim2.writable)
    }

    @Test fun `unreadable subscription produces no pseudo account`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(1 to SimCapabilities(canRead = false, canWrite = false)),
        )
        val accounts = integration(source, listOf(SimSubscription(1, "SIM 1"))).simPseudoAccounts(emptyList())
        assertTrue(accounts.isEmpty())
    }

    @Test fun `no sim contacts when native SIM raw contacts already flow through provider`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(1 to SimCapabilities(true, true)),
            contactsBySub = mutableMapOf(1 to mutableListOf(SimContact(1, "Budi", "0812", 1))),
        )
        val integration = integration(source)
        val contacts = integration.simContacts(listOf(nativeSimContact()))
        assertTrue(contacts.isEmpty())
        assertNull(integration.resolveSimContact(SimRouting.syntheticId(0)))
    }

    @Test fun `sim contacts get stable synthetic negative ids and resolve back`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(
                1 to SimCapabilities(true, true),
                2 to SimCapabilities(true, true),
            ),
            contactsBySub = mutableMapOf(
                1 to mutableListOf(
                    SimContact(2, "Citra", "0813", 1),
                    SimContact(1, "Budi", "0812", 1),
                ),
                2 to mutableListOf(SimContact(1, "Dewi", "0814", 2)),
            ),
        )
        val integration = integration(source)
        val contacts = integration.simContacts(emptyList())

        // Ordered by (subscription, icc index): Budi, Citra, Dewi.
        assertEquals(listOf("Budi", "Citra", "Dewi"), contacts.map { it.displayName })
        assertEquals(
            listOf(SimRouting.syntheticId(0), SimRouting.syntheticId(1), SimRouting.syntheticId(2)),
            contacts.map { it.contactId },
        )
        contacts.forEach { contact ->
            assertEquals(1, contact.rawContacts.size)
            val raw = contact.rawContacts.single()
            assertEquals(contact.contactId, raw.rawContactId)
            assertTrue(SimRouting.isSimRawContactId(raw.rawContactId))
            assertEquals("SIM", raw.phones.single().typeLabel)
        }
        assertEquals("icc/1", contacts[0].rawContacts.single().accountType)
        assertEquals("SIM 1 · Telkomsel", contacts[0].rawContacts.single().accountName)
        assertEquals("icc/2", contacts[2].rawContacts.single().accountType)

        val resolved = integration.resolveSimContact(contacts[1].rawContacts.single().rawContactId)
        assertNotNull(resolved)
        assertEquals(1, resolved!!.subscriptionId)
        assertEquals("Citra", resolved.contact.name)
        assertNull(integration.resolveSimContact(-999L))
        assertNull(integration.resolveSimContact(10L))
    }

    @Test fun `capabilities are probed once then served from cache`() = runTest {
        val source = FakeSimContactSource(
            capsBySub = mapOf(1 to SimCapabilities(true, true)),
            contactsBySub = mutableMapOf(1 to mutableListOf(SimContact(1, "Budi", "0812", 1))),
        )
        val integration = integration(source, listOf(SimSubscription(1, "SIM 1")))
        integration.simContacts(emptyList())
        integration.simContacts(emptyList())
        integration.simPseudoAccounts(emptyList())
        assertEquals(1, source.probes)
    }
}
