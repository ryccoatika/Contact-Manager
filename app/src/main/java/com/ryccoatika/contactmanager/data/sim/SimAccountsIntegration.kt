package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.LabeledValue
import com.ryccoatika.contactmanager.domain.model.RawContact
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import com.ryccoatika.contactmanager.domain.sim.SimCardReconciler
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * no icc pseudo-accounts are added — those SIM contacts keep flowing through the
 * normal ContactsContract pipeline and adding icc pseudo-accounts on top
 * would show every SIM contact twice. Non-Samsung native mirrors are additionally
 * reconciled against the card so entries written behind the provider's back show
 * up as synthetic contacts under the native account (see [simContacts]).
 */
interface SimAccountsIntegration {
    suspend fun simPseudoAccounts(existingAccounts: List<ContactAccount>): List<ContactAccount>

    suspend fun simContacts(existingContacts: List<Contact>): List<Contact>

    /** Maps a synthetic negative raw-contact id back to the SIM entry from the last read. */
    fun resolveSimContact(rawContactId: Long): ResolvedSimContact?

    /** Active subscriptions keyed by physical slot, for labeling native SIM accounts. */
    suspend fun subscriptionsBySlot(): Map<Int, SimSubscription>

    /**
     * Card-only entries (from the last [simContacts] read) surfaced under the native
     * SIM account ([accountType], [accountName]); see SimCardReconciler.
     */
    fun cardOnlyCount(accountType: String?, accountName: String?): Int

    /** Emits when any [cardOnlyCount] changes, so account counts can reload. */
    val cardOnlyChanges: Flow<Unit>
}

/** One SIM card entry on its way to becoming a synthetic contact, with its owning account. */
private data class SimEntry(
    val subscriptionId: Int?,
    val entry: SimContact,
    val accountType: String,
    val accountName: String?,
)

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

        /** Card-only entry counts per native (type, name) from the last simContacts() read. */
        @Volatile
        private var cardOnlyCounts: Map<Pair<String?, String?>, Int> = emptyMap()

        private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override val cardOnlyChanges: Flow<Unit> = changes.asSharedFlow()

        override suspend fun simPseudoAccounts(
            existingAccounts: List<ContactAccount>,
        ): List<ContactAccount> {
            val nativeSimTypes = existingAccounts
                .filter { it.capability == AccountCapability.SIM }
                .map { it.type }
            val uncoveredSubs = uncovered(subscriptionsSource.activeSubscriptions(), nativeSimTypes)
            return readableSubscriptions(uncoveredSubs).map { (sub, caps) ->
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
            // Plain loop: this runs on every contact load, over every raw contact.
            val nativeRaws = ArrayList<Pair<Contact, RawContact>>()
            for (contact in existingContacts) {
                for (raw in contact.rawContacts) {
                    if (AccountClassifier.classify(raw.accountType) == AccountCapability.SIM) nativeRaws += contact to raw
                }
            }
            val active = subscriptionsSource.activeSubscriptions()
            val uncoveredSubs = uncovered(active, nativeRaws.map { (_, raw) -> raw.accountType })
            val entries = readableSubscriptions(uncoveredSubs)
                .flatMap { (sub, _) ->
                    simSource.readAll(sub.subscriptionId).map {
                        SimEntry(sub.subscriptionId, it, SimRouting.simAccountType(sub.subscriptionId), sub.label)
                    }
                } + reconcileWithCards(nativeRaws, active, uncoveredSubs)
            val sorted = entries.sortedWith(
                compareBy(
                    { it.subscriptionId ?: -1 },
                    { it.entry.indexOnIcc ?: Int.MAX_VALUE },
                    { it.entry.name },
                ),
            )
            val newSnapshot = HashMap<Long, ResolvedSimContact>(sorted.size)
            val contacts = sorted.mapIndexed { index, item ->
                val syntheticId = SimRouting.syntheticId(index)
                newSnapshot[syntheticId] = ResolvedSimContact(item.subscriptionId, item.entry)
                Contact(
                    contactId = syntheticId,
                    displayName = item.entry.name.ifBlank { item.entry.number },
                    rawContacts = listOf(
                        RawContact(
                            rawContactId = syntheticId,
                            accountType = item.accountType,
                            accountName = item.accountName,
                            phones = listOf(LabeledValue(dataId = -1, value = item.entry.number, typeLabel = "SIM"))
                                .filter { it.value.isNotBlank() },
                        ),
                    ),
                )
            }
            snapshot = newSnapshot
            publishCardOnlyCounts(
                sorted
                    .filter { !SimRouting.isSimAccount(it.accountType) }
                    .groupingBy { it.accountType to it.accountName }
                    .eachCount(),
            )
            return contacts
        }

        override fun cardOnlyCount(accountType: String?, accountName: String?): Int =
            cardOnlyCounts[accountType to accountName] ?: 0

        /** Stores the per-account counts and signals [cardOnlyChanges] only when they differ. */
        private fun publishCardOnlyCounts(counts: Map<Pair<String?, String?>, Int>) {
            if (counts == cardOnlyCounts) return
            cardOnlyCounts = counts
            changes.tryEmit(Unit)
        }

        override fun resolveSimContact(rawContactId: Long): ResolvedSimContact? = snapshot[rawContactId]

        /**
         * Readable subscriptions among [uncoveredSubs], the ones no native SIM account
         * surfaces (see [uncovered]), so the same SIM isn't listed twice.
         */
        private suspend fun readableSubscriptions(
            uncoveredSubs: List<SimSubscription>,
        ): List<Pair<SimSubscription, SimCapabilities>> =
            uncoveredSubs.mapNotNull { sub ->
                val caps = simRepository.capabilities(sub.subscriptionId)
                if (caps.canRead) sub to caps else null
            }

        /**
         * Card entries missing from the mirror of each reconcilable native SIM account
         * (see SimRouting.reconcilesWithCard), attributed to that account. Reads go
         * through [SimContactSource.readAll] only — never a capability probe, which
         * writes a test marker to the SIM.
         */
        private suspend fun reconcileWithCards(
            nativeRaws: List<Pair<Contact, RawContact>>,
            active: List<SimSubscription>,
            uncoveredSubs: List<SimSubscription>,
        ): List<SimEntry> {
            val accounts = nativeRaws
                .map { (_, raw) -> raw.accountType to raw.accountName }
                .filter { (type, _) -> !SimRouting.isSimAccount(type) }
                .distinct()
            val reconcilable = accounts.filter { (type, _) -> SimRouting.reconcilesWithCard(type) }
            if (reconcilable.isEmpty()) return emptyList()
            // Unambiguous single-SIM device: pairs even types that map to no slot.
            val lone = if (accounts.size == 1 && active.size == 1 && subscriptionsSource.presentSimCount() == 1) {
                active.single()
            } else {
                null
            }
            val paired = reconcilable.mapNotNull { account ->
                val slot = SimRouting.nativeSimSlot(account.first)
                (slot?.let { s -> active.firstOrNull { it.slotIndex == s } } ?: lone)
                    ?.takeIf { it !in uncoveredSubs }
                    ?.let { account to it }
            }
            // Two accounts claiming one card can't be told apart: skip both rather
            // than list a card-only entry under each.
            val claims = paired.groupingBy { (_, sub) -> sub.subscriptionId }.eachCount()
            return paired.filter { (_, sub) -> claims[sub.subscriptionId] == 1 }.flatMap { (account, sub) ->
                val (type, name) = account
                val card = simSource.readAll(sub.subscriptionId)
                val mirror = nativeRaws.filter { (_, raw) -> raw.accountType == type && raw.accountName == name }
                val numbers = mirror.flatMap { (_, raw) -> raw.phones.map { it.value } }
                val names = mirror.flatMap { (contact, raw) ->
                    listOf(contact.displayName, listOfNotNull(raw.givenName, raw.familyName).joinToString(" "))
                }
                SimCardReconciler.cardOnly(card, numbers, names).map { SimEntry(sub.subscriptionId, it, type!!, name) }
            }
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
