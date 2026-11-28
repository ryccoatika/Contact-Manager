package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    override suspend fun set(subId: Int?, caps: SimCapabilities) {
        map[subId] = caps
    }
}

class FakeSimSubscriptionsSource(
    private val subscriptions: List<SimSubscription> = listOf(SimSubscription(null, "SIM")),
    private val presentSimCount: Int = subscriptions.size,
) : SimSubscriptionsSource {
    override suspend fun activeSubscriptions(): List<SimSubscription> = subscriptions

    override suspend fun presentSimCount(): Int = presentSimCount
}

class SimAccountsIntegrationTest {
    private val dualSim = listOf(
        SimSubscription(1, "SIM 1 · Telkomsel"),
        SimSubscription(2, "SIM 2 · XL"),
    )

    private fun integration(
        source: FakeSimContactSource,
        subs: List<SimSubscription> = dualSim,
        presentSimCount: Int = subs.size,
    ) = IccSimAccountsIntegration(
        subscriptionsSource = FakeSimSubscriptionsSource(subs, presentSimCount),
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

    @Test fun `partial native coverage surfaces the uncovered SIM via icc pseudo account`() = runTest {
        // Physical SIM in slot 0 (icc-only) + eSIM in slot 1 that the provider
        // surfaces natively as vnd.sec.contact.sim2. Only slot 0 should get icc.
        val subs = listOf(
            SimSubscription(1, "SIM 1 · Telkomsel", slotIndex = 0),
            SimSubscription(3, "SIM 2 · XL", slotIndex = 1),
        )
        val source = FakeSimContactSource(
            capsBySub = mapOf(
                1 to SimCapabilities(canRead = true, canWrite = true),
                3 to SimCapabilities(canRead = true, canWrite = true),
            ),
            contactsBySub = mutableMapOf(1 to mutableListOf(SimContact(1, "Budi", "0812", 1))),
        )
        val nativeSim2 = ContactAccount("SIM", "vnd.sec.contact.sim2", AccountCapability.SIM, 2)
        val accounts = integration(source, subs).simPseudoAccounts(listOf(nativeSim2))
        assertEquals(1, accounts.size)
        assertEquals("icc/1", accounts.single().type)
    }

    @Test fun `partial native coverage still reads the uncovered SIM contacts`() = runTest {
        val subs = listOf(
            SimSubscription(1, "SIM 1", slotIndex = 0),
            SimSubscription(3, "SIM 2", slotIndex = 1),
        )
        val source = FakeSimContactSource(
            capsBySub = mapOf(
                1 to SimCapabilities(true, true),
                3 to SimCapabilities(true, true),
            ),
            contactsBySub = mutableMapOf(
                1 to mutableListOf(SimContact(1, "Budi", "0812", 1)),
                3 to mutableListOf(SimContact(1, "OnEsim", "0899", 3)),
            ),
        )
        val nativeEsim = Contact(
            contactId = 9,
            displayName = "OnEsim",
            rawContacts = listOf(RawContact(90, "vnd.sec.contact.sim2", "sim2")),
        )
        val contacts = integration(source, subs).simContacts(listOf(nativeEsim))
        assertEquals(listOf("Budi"), contacts.map { it.displayName })
        assertEquals(
            "icc/1",
            contacts
                .single()
                .rawContacts
                .single()
                .accountType,
        )
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

    @Test fun `no subscriptions yields no SIM accounts or contacts`() = runTest {
        val integration = integration(FakeSimContactSource(), subs = emptyList())
        assertTrue(integration.simPseudoAccounts(emptyList()).isEmpty())
        assertTrue(integration.simContacts(emptyList()).isEmpty())
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

    // --- card reconcile -------------------------------------------------------

    private val legacySim = listOf(SimSubscription(null, "SIM"))

    private fun mirrorContact(
        type: String,
        accountName: String,
        name: String,
        number: String,
        id: Long = 5,
    ) = Contact(
        contactId = id,
        displayName = name,
        rawContacts = listOf(
            RawContact(
                rawContactId = id * 10,
                accountType = type,
                accountName = accountName,
                phones = listOf(LabeledValue(1, number, null)),
            ),
        ),
    )

    /** Legacy-path card from flat name, number, name, number… arguments. */
    private fun legacyCard(vararg nameAndNumber: String) = FakeSimContactSource(
        contactsBySub = mutableMapOf(
            null to nameAndNumber
                .toList()
                .chunked(2)
                .mapIndexed { i, (n, num) -> SimContact(i + 1, n, num, null) }
                .toMutableList(),
        ),
    )

    @Test fun `unmappable native type on a single SIM surfaces card-only entries`() = runTest {
        val source = legacyCard("Old", "0812345678", "New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 1)
        val mirror = listOf(mirrorContact("USIM Account", "USIM", "Old", "+62 812-345-678"))

        val contacts = integration.simContacts(mirror)

        assertEquals(listOf("New"), contacts.map { it.displayName })
        val raw = contacts.single().rawContacts.single()
        assertEquals("USIM Account", raw.accountType)
        assertEquals("USIM", raw.accountName)
        assertTrue(SimRouting.isSimRawContactId(raw.rawContactId))
        assertEquals(contacts.single().contactId, raw.rawContactId)
        val resolved = integration.resolveSimContact(raw.rawContactId)
        assertNotNull(resolved)
        assertNull(resolved!!.subscriptionId)
        assertEquals("New", resolved.contact.name)
        assertEquals(1, integration.cardOnlyCount("USIM Account", "USIM"))
        assertEquals(0, integration.cardOnlyCount("USIM Account", "other"))
        val account = ContactAccount("USIM", "USIM Account", AccountCapability.SIM, 1)
        assertTrue(integration.simPseudoAccounts(listOf(account)).isEmpty())
        assertEquals(0, source.probes)
    }

    @Test fun `two SIMs present disables the single-SIM pairing`() = runTest {
        val source = legacyCard("New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 2)
        val contacts = integration.simContacts(listOf(mirrorContact("USIM Account", "USIM", "Old", "0812")))
        assertTrue(contacts.isEmpty())
        assertEquals(0, integration.cardOnlyCount("USIM Account", "USIM"))
        assertEquals(0, source.probes)
    }

    @Test fun `unknown present SIM count disables the single-SIM pairing`() = runTest {
        val source = legacyCard("New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 0)
        assertTrue(integration.simContacts(listOf(mirrorContact("USIM Account", "USIM", "Old", "0812"))).isEmpty())
    }

    @Test fun `two native SIM accounts are never paired by the single-SIM rule`() = runTest {
        val source = legacyCard("New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 1)
        val contacts = integration.simContacts(
            listOf(
                mirrorContact("USIM Account", "A", "Old", "0812", id = 5),
                mirrorContact("USIM Account", "B", "Older", "0813", id = 6),
            ),
        )
        assertTrue(contacts.isEmpty())
    }

    @Test fun `a Samsung account counts toward the single-SIM rule`() = runTest {
        val source = legacyCard("New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 1)
        val contacts = integration.simContacts(
            listOf(
                mirrorContact("USIM Account", "A", "Old", "0812", id = 5),
                mirrorContact("vnd.sec.contact.sim", "SIM", "Older", "0813", id = 6),
            ),
        )
        assertTrue(contacts.isEmpty())
    }

    @Test fun `mappable native type reconciles against the subscription in its slot`() = runTest {
        val subs = listOf(SimSubscription(7, "SIM 1", slotIndex = 0), SimSubscription(8, "SIM 2", slotIndex = 1))
        val source = FakeSimContactSource(
            contactsBySub = mutableMapOf(
                7 to mutableListOf(SimContact(1, "Old", "0812345678", 7), SimContact(2, "New", "0899887766", 7)),
                8 to mutableListOf(SimContact(1, "Other", "0855555555", 8)),
            ),
        )
        val integration = integration(source, subs, presentSimCount = 2)
        val mirror = listOf(mirrorContact("com.android.contacts.sim", "SIM", "Old", "0812345678"))

        val contacts = integration.simContacts(mirror)
        // Slot 1 is uncovered here: it is an icc pseudo-account candidate, never reconciled.
        assertEquals(listOf("New"), contacts.map { it.displayName })
        assertEquals(7, integration.resolveSimContact(contacts.single().contactId)!!.subscriptionId)
        assertEquals(1, integration.cardOnlyCount("com.android.contacts.sim", "SIM"))
    }

    @Test fun `partial native coverage keeps the uncovered SIM as icc and never reconciles it into the native account`() =
        runTest {
            val subs = listOf(SimSubscription(1, "SIM 1", slotIndex = 0), SimSubscription(3, "SIM 2", slotIndex = 1))
            val source = FakeSimContactSource(
                capsBySub = mapOf(1 to SimCapabilities(true, true), 3 to SimCapabilities(true, true)),
                contactsBySub = mutableMapOf(
                    1 to mutableListOf(SimContact(1, "Budi", "0812", 1)),
                    3 to mutableListOf(SimContact(1, "EsimOnly", "0899", 3), SimContact(2, "Mirrored", "0877", 3)),
                ),
            )
            val integration = integration(source, subs, presentSimCount = 2)
            val mirror = listOf(mirrorContact("com.android.contacts.sim2", "SIM2", "Mirrored", "0877"))

            val contacts = integration.simContacts(mirror)

            // Slot 0 stays an icc pseudo-account; only slot 1's own card feeds the native account.
            val typeByName = contacts.associate { it.displayName to it.rawContacts.single().accountType }
            assertEquals(mapOf("Budi" to "icc/1", "EsimOnly" to "com.android.contacts.sim2"), typeByName)
            assertEquals(1, integration.cardOnlyCount("com.android.contacts.sim2", "SIM2"))
        }

    @Test fun `two native accounts mapping to one slot are not reconciled`() = runTest {
        val subs = listOf(SimSubscription(7, "SIM 1", slotIndex = 0))
        val source = FakeSimContactSource(
            contactsBySub = mutableMapOf(7 to mutableListOf(SimContact(1, "New", "0899887766", 7))),
        )
        val integration = integration(source, subs, presentSimCount = 1)
        val contacts = integration.simContacts(
            listOf(
                mirrorContact("com.android.contacts.sim", "SIM", "Old", "0812", id = 5),
                mirrorContact("com.android.sim", "SIM", "Older", "0813", id = 6),
            ),
        )
        assertTrue(contacts.isEmpty())
    }

    @Test fun `Samsung native account is not reconciled and keeps its count at zero`() = runTest {
        val source = legacyCard("New", "0899887766")
        val integration = integration(source, legacySim, presentSimCount = 1)
        val contacts = integration.simContacts(listOf(nativeSimContact()))
        assertTrue(contacts.isEmpty())
        assertEquals(0, integration.cardOnlyCount("vnd.sec.contact.sim", "SIM"))
        assertNull(integration.resolveSimContact(SimRouting.syntheticId(0)))
        assertEquals(0, source.probes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cardOnlyChanges emits when a count changes but not on an identical re-read`() = runTest {
        val source = legacyCard("Old", "0812345678")
        val integration = integration(source, legacySim, presentSimCount = 1)
        val mirror = listOf(mirrorContact("USIM Account", "USIM", "Old", "0812345678"))
        var emissions = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            integration.cardOnlyChanges.collect { emissions++ }
        }

        integration.simContacts(mirror)
        assertEquals(0, emissions)

        source.contactsBySub.getValue(null) += SimContact(2, "New", "0899887766", null)
        integration.simContacts(mirror)
        assertEquals(1, emissions)

        integration.simContacts(mirror)
        assertEquals(1, emissions)
        job.cancel()
    }
}
