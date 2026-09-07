package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import javax.inject.Inject
import javax.inject.Singleton

/** A synthetic SIM raw-contact id mapped back to the icc entry behind it. */
data class ResolvedSimContact(
    val subscriptionId: Int?,
    val contact: SimContact,
)

/**
 * Merges icc/adn SIM storage into the app's account and contact lists.
 *
 * Vendor guard: on devices (Samsung et al.) whose contacts provider already
 * surfaces SIM entries as a ContactsContract account (vnd.sec.contact.sim, …),
 * both methods return empty — those SIM contacts keep flowing through the
 * normal ContactsContract pipeline and adding icc pseudo-accounts on top
 * would show every SIM contact twice.
 */
interface SimAccountsIntegration {
    suspend fun simPseudoAccounts(existingAccounts: List<ContactAccount>): List<ContactAccount>

    suspend fun simContacts(existingContacts: List<Contact>): List<Contact>

    /** Maps a synthetic negative raw-contact id back to the SIM entry from the last read. */
    fun resolveSimContact(rawContactId: Long): ResolvedSimContact?

    /** Active subscriptions keyed by physical slot, for labeling native SIM accounts. */
    suspend fun subscriptionsBySlot(): Map<Int, SimSubscription>
}

@Singleton
class IccSimAccountsIntegration
    @Inject
    constructor(
        private val subscriptionsSource: SimSubscriptionsSource,
        private val simRepository: SimRepository,
        private val simSource: SimContactSource,
    ) : SimAccountsIntegration {
        /** Snapshot of the last simContacts() read, for synthetic-id resolution. */
        @Volatile
        private var snapshot: Map<Long, ResolvedSimContact> = emptyMap()

        override suspend fun simPseudoAccounts(
            existingAccounts: List<ContactAccount>,
        ): List<ContactAccount> {
            val nativeSimTypes = existingAccounts
                .filter { it.capability == AccountCapability.SIM }
                .map { it.type }
            return readableSubscriptions(nativeSimTypes).map { (sub, caps) ->
                ContactAccount(
                    name = sub.label,
                    type = SimRouting.simAccountType(sub.subscriptionId),
                    capability = AccountCapability.SIM,
                    contactCount = simSource.readAll(sub.subscriptionId).size,
                    writable = caps.canWrite,
                    displayLabel = sub.label,
                    phoneNumber = sub.number,
                )
            }
        }

        override suspend fun subscriptionsBySlot(): Map<Int, SimSubscription> =
            subscriptionsSource
                .activeSubscriptions()
                .mapNotNull { sub -> sub.slotIndex?.let { it to sub } }
                .toMap()

        override suspend fun simContacts(existingContacts: List<Contact>): List<Contact> {
            val nativeSimTypes = existingContacts
                .flatMap { it.rawContacts }
                .filter { AccountClassifier.classify(it.accountType) == AccountCapability.SIM }
                .map { it.accountType }
            val readable = readableSubscriptions(nativeSimTypes)
            if (readable.isEmpty()) {
                snapshot = emptyMap()
                return emptyList()
            }
            val entries = readable
                .flatMap { (sub, _) -> simSource.readAll(sub.subscriptionId).map { sub to it } }
                .sortedWith(
                    compareBy(
                        { (sub, _) -> sub.subscriptionId ?: -1 },
                        { (_, entry) -> entry.indexOnIcc ?: Int.MAX_VALUE },
                        { (_, entry) -> entry.name },
                    ),
                )
            val newSnapshot = HashMap<Long, ResolvedSimContact>(entries.size)
            val contacts = entries.mapIndexed { index, (sub, entry) ->
                val syntheticId = SimRouting.syntheticId(index)
                newSnapshot[syntheticId] = ResolvedSimContact(sub.subscriptionId, entry)
                Contact(
                    contactId = syntheticId,
                    displayName = entry.name.ifBlank { entry.number },
                    rawContacts = listOf(
                        RawContact(
                            rawContactId = syntheticId,
                            accountType = SimRouting.simAccountType(sub.subscriptionId),
                            accountName = sub.label,
                            phones = listOf(LabeledValue(dataId = -1, value = entry.number, typeLabel = "SIM"))
                                .filter { it.value.isNotBlank() },
                        ),
                    ),
                )
            }
            snapshot = newSnapshot
            return contacts
        }

        override fun resolveSimContact(rawContactId: Long): ResolvedSimContact? = snapshot[rawContactId]

        /**
         * Readable subscriptions not already surfaced by a native SIM account.
         * [nativeSimTypes] are the account types of SIM accounts the provider exposes
         * itself; a subscription whose physical slot one of them covers is dropped so
         * the same SIM isn't listed twice. See [uncovered] for the slot matching.
         */
        private suspend fun readableSubscriptions(
            nativeSimTypes: List<String?>,
        ): List<Pair<SimSubscription, SimCapabilities>> =
            uncovered(subscriptionsSource.activeSubscriptions(), nativeSimTypes).mapNotNull { sub ->
                val caps = simRepository.capabilities(sub.subscriptionId)
                if (caps.canRead) sub to caps else null
            }

        /**
         * Filters [subs] down to those NOT covered by a native SIM account. With no
         * native SIM accounts every subscription flows through icc. Otherwise each
         * native account is mapped to a physical slot and matching subscriptions are
         * dropped. If any native SIM type can't be mapped to a slot, all icc pseudo-
         * accounts are skipped (old behavior) rather than risk double-listing a SIM.
         */
        private fun uncovered(
            subs: List<SimSubscription>,
            nativeSimTypes: List<String?>,
        ): List<SimSubscription> {
            if (nativeSimTypes.isEmpty()) return subs
            val slots = nativeSimTypes.map { SimRouting.nativeSimSlot(it) }
            if (slots.any { it == null }) return emptyList()
            val covered = slots.filterNotNull().toSet()
            return subs.filter { it.slotIndex != null && it.slotIndex !in covered }
        }
    }
