package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimContact
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
}

@Singleton
class IccSimAccountsIntegration @Inject constructor(
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
        if (existingAccounts.any { it.capability == AccountCapability.SIM }) return emptyList()
        return readableSubscriptions().map { (sub, caps) ->
            ContactAccount(
                name = sub.label,
                type = SimRouting.simAccountType(sub.subscriptionId),
                capability = AccountCapability.SIM,
                contactCount = simSource.readAll(sub.subscriptionId).size,
                writable = caps.canWrite,
            )
        }
    }

    override suspend fun simContacts(existingContacts: List<Contact>): List<Contact> {
        val nativeSimPresent = existingContacts.any { contact ->
            contact.rawContacts.any {
                AccountClassifier.classify(it.accountType) == AccountCapability.SIM
            }
        }
        if (nativeSimPresent) {
            snapshot = emptyMap()
            return emptyList()
        }
        val entries = readableSubscriptions()
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

    private suspend fun readableSubscriptions() =
        subscriptionsSource.activeSubscriptions().mapNotNull { sub ->
            val caps = simRepository.capabilities(sub.subscriptionId)
            if (caps.canRead) sub to caps else null
        }
}
