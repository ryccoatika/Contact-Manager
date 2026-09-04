package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.sim.ResolvedSimContact
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.SimContactSource
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.di.ContactsContractWriter
import com.ryccoatika.contactmanager.domain.SimContactValidator
import com.ryccoatika.contactmanager.domain.SimValidation
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Decorator over [ContactsWriteRepository] that routes SIM work to the icc
 * provider: "icc/…" account types and synthetic negative raw-contact ids are
 * SIM entries, everything else is delegated untouched. Moves and copies down-
 * convert to name + first phone; over-long names fail, they are never truncated.
 */
@Singleton
class SimAwareContactsWriter
    @Inject
    constructor(
        @ContactsContractWriter private val delegate: ContactsWriter,
        private val simSource: SimContactSource,
        private val simIntegration: SimAccountsIntegration,
        private val simRepository: SimRepository,
        private val contactsSource: ContactsSource,
        private val strings: StringProvider,
    ) : ContactsWriter {
        override suspend fun createContact(
            accountType: String?,
            accountName: String?,
            contact: EditableContact,
        ): ContactOpResult {
            if (!SimRouting.isSimAccount(accountType)) {
                return delegate.createContact(accountType, accountName, contact)
            }
            val subId = SimRouting.subscriptionIdOf(accountType)
            return insertToSim(
                subId = subId,
                name = contact.displayName.trim(),
                number = SimRouting.normalizeNumber(
                    contact.phones
                        .firstOrNull { it.first.isNotBlank() }
                        ?.first
                        .orEmpty(),
                ),
            )
        }

        override suspend fun updateRawContact(
            rawContactId: Long,
            contact: EditableContact,
        ): ContactOpResult {
            if (!SimRouting.isSimRawContactId(rawContactId)) {
                return delegate.updateRawContact(rawContactId, contact)
            }
            val resolved = resolve(rawContactId) ?: return staleSimEntry()
            val subId = resolved.subscriptionId
            val name = contact.displayName.trim()
            val number = SimRouting.normalizeNumber(
                contact.phones
                    .firstOrNull { it.first.isNotBlank() }
                    ?.first
                    .orEmpty(),
            )
            validate(subId, name, number)?.let { return it }
            return simSource.update(subId, resolved.contact, name, number).notifyOnSuccess()
        }

        override suspend fun deleteRawContacts(rawContactIds: List<Long>): ContactOpResult {
            val (simIds, providerIds) = SimRouting.splitSimIds(rawContactIds)
            var firstFailure: ContactOpResult.Failure? = null
            if (providerIds.isNotEmpty()) {
                val result = delegate.deleteRawContacts(providerIds)
                if (result is ContactOpResult.Failure) firstFailure = result
            }
            var simDeleted = false
            for (id in simIds) {
                val result = deleteSimEntry(id)
                if (result is ContactOpResult.Success) {
                    simDeleted = true
                } else if (firstFailure == null) {
                    firstFailure = result as ContactOpResult.Failure
                }
            }
            if (simDeleted) simRepository.notifySimChanged()
            return firstFailure ?: ContactOpResult.Success
        }

        override suspend fun copyRawContact(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult = when {
            SimRouting.isSimRawContactId(rawContactId) -> {
                val resolved = resolve(rawContactId)
                when {
                    resolved == null -> staleSimEntry()

                    SimRouting.isSimAccount(targetType) -> insertToSim(
                        subId = SimRouting.subscriptionIdOf(targetType),
                        name = resolved.contact.name,
                        number = SimRouting.normalizeNumber(resolved.contact.number),
                    )

                    else -> delegate.createContact(targetType, targetName, resolved.contact.asEditable())
                }
            }

            SimRouting.isSimAccount(targetType) -> {
                val raw = findRawContact(rawContactId)
                if (raw == null) {
                    ContactOpResult.Failure(strings.get(R.string.saw_error_copy_not_found))
                } else {
                    insertToSim(
                        subId = SimRouting.subscriptionIdOf(targetType),
                        name = raw.simName(),
                        number = SimRouting.normalizeNumber(
                            raw.rawContact.phones
                                .firstOrNull()
                                ?.value
                                .orEmpty(),
                        ),
                    )
                }
            }

            else -> {
                delegate.copyRawContact(rawContactId, targetType, targetName)
            }
        }

        override suspend fun moveRawContacts(
            rawContactIds: List<Long>,
            targetType: String?,
            targetName: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ContactOpResult {
            val (simIds, providerIds) = SimRouting.splitSimIds(rawContactIds)
            if (simIds.isEmpty() && !SimRouting.isSimAccount(targetType)) {
                return delegate.moveRawContacts(providerIds, targetType, targetName, onProgress)
            }

            val total = rawContactIds.size
            var done = 0
            var failed = 0
            var firstMessage: String? = null
            var simTouched = false

            fun track(result: ContactOpResult) {
                if (result is ContactOpResult.Failure) {
                    failed++
                    if (firstMessage == null) firstMessage = result.message
                } else {
                    simTouched = true
                }
            }

            // SIM-sourced entries: copy into the target first, delete from the SIM
            // only after the copy landed (a failure leaves a duplicate, never loses data).
            for (id in simIds) {
                coroutineContext.ensureActive()
                track(moveSimSourced(id, targetType, targetName))
                done++
                onProgress(done, total)
            }

            if (providerIds.isNotEmpty()) {
                if (SimRouting.isSimAccount(targetType)) {
                    val subId = SimRouting.subscriptionIdOf(targetType)
                    val snapshot = contactsSource.observeContacts().first()
                    for (id in providerIds) {
                        coroutineContext.ensureActive()
                        track(moveProviderContactToSim(id, subId, snapshot))
                        done++
                        onProgress(done, total)
                    }
                } else {
                    val doneBefore = done
                    val result = delegate.moveRawContacts(providerIds, targetType, targetName) { d, _ ->
                        onProgress(doneBefore + d, total)
                    }
                    done = doneBefore + providerIds.size
                    if (result is ContactOpResult.Failure) {
                        failed++
                        if (firstMessage == null) firstMessage = result.message
                    }
                }
            }

            if (simTouched) simRepository.notifySimChanged()
            return if (failed == 0) {
                ContactOpResult.Success
            } else {
                ContactOpResult.Failure(firstMessage ?: strings.get(R.string.saw_error_move_some))
            }
        }

        // --- aggregation & merge ---------------------------------------------

        /** SIM entries live outside ContactsContract, so aggregation can't touch them. */
        override suspend fun linkContacts(rawContactIds: List<Long>): ContactOpResult =
            if (rawContactIds.any(SimRouting::isSimRawContactId)) {
                simAggregationFailure()
            } else {
                delegate.linkContacts(rawContactIds)
            }

        override suspend fun keepSeparate(rawContactIds: List<Long>): ContactOpResult =
            if (rawContactIds.any(SimRouting::isSimRawContactId)) {
                simAggregationFailure()
            } else {
                delegate.keepSeparate(rawContactIds)
            }

        override suspend fun mergeContacts(
            target: RawContact,
            sources: List<RawContact>,
        ): ContactOpResult =
            if (SimRouting.isSimRawContactId(target.rawContactId) ||
                sources.any { SimRouting.isSimRawContactId(it.rawContactId) }
            ) {
                simAggregationFailure()
            } else {
                delegate.mergeContacts(target, sources)
            }

        private fun simAggregationFailure() =
            ContactOpResult.Failure(strings.get(R.string.saw_error_sim_no_link))

        // --- SIM primitives -------------------------------------------------

        private suspend fun insertToSim(subId: Int?, name: String, number: String): ContactOpResult {
            validate(subId, name, number)?.let { return it }
            return simSource.insert(subId, name, number).notifyOnSuccess()
        }

        /** Returns a Failure when invalid or the SIM is not writable, null when ok. */
        private suspend fun validate(subId: Int?, name: String, number: String): ContactOpResult.Failure? {
            val caps = simRepository.capabilities(subId)
            if (!caps.canWrite) {
                return ContactOpResult.Failure(strings.get(R.string.saw_error_sim_readonly))
            }
            return when (val validation = SimContactValidator.validate(name, number, caps)) {
                is SimValidation.Error -> ContactOpResult.Failure(validation.error.toMessage(strings))
                SimValidation.Ok -> null
            }
        }

        private fun resolve(rawContactId: Long): ResolvedSimContact? =
            simIntegration.resolveSimContact(rawContactId)

        private fun staleSimEntry() =
            ContactOpResult.Failure(strings.get(R.string.saw_error_sim_stale))

        private suspend fun deleteSimEntry(rawContactId: Long): ContactOpResult {
            val resolved = resolve(rawContactId) ?: return staleSimEntry()
            return simSource.delete(resolved.subscriptionId, resolved.contact)
        }

        private suspend fun moveSimSourced(
            rawContactId: Long,
            targetType: String?,
            targetName: String?,
        ): ContactOpResult {
            val resolved = resolve(rawContactId) ?: return staleSimEntry()
            val copied = if (SimRouting.isSimAccount(targetType)) {
                insertToSim(
                    subId = SimRouting.subscriptionIdOf(targetType),
                    name = resolved.contact.name,
                    number = SimRouting.normalizeNumber(resolved.contact.number),
                )
            } else {
                delegate.createContact(targetType, targetName, resolved.contact.asEditable())
            }
            if (copied is ContactOpResult.Failure) return copied
            return simSource.delete(resolved.subscriptionId, resolved.contact)
        }

        private suspend fun moveProviderContactToSim(
            rawContactId: Long,
            subId: Int?,
            snapshot: List<Contact>,
        ): ContactOpResult {
            val raw = findRawContact(rawContactId, snapshot)
                ?: return ContactOpResult.Failure(strings.get(R.string.saw_error_move_not_found))
            val copied = insertToSim(
                subId = subId,
                name = raw.simName(),
                number = SimRouting.normalizeNumber(
                    raw.rawContact.phones
                        .firstOrNull()
                        ?.value
                        .orEmpty(),
                ),
            )
            if (copied is ContactOpResult.Failure) return copied
            return delegate.deleteRawContacts(listOf(rawContactId))
        }

        // --- lookup helpers -------------------------------------------------

        private data class RawWithContact(
            val contact: Contact,
            val rawContact: RawContact,
        )

        private suspend fun findRawContact(
            rawContactId: Long,
            snapshot: List<Contact>? = null,
        ): RawWithContact? {
            val contacts = snapshot ?: contactsSource.observeContacts().first()
            contacts.forEach { contact ->
                contact.rawContacts.forEach { raw ->
                    if (raw.rawContactId == rawContactId) return RawWithContact(contact, raw)
                }
            }
            return null
        }

        /** Down-converted SIM name: structured name if present, else the display name. */
        private fun RawWithContact.simName(): String =
            listOfNotNull(rawContact.givenName, rawContact.familyName)
                .joinToString(" ")
                .ifBlank { contact.displayName }
                .trim()

        private fun com.ryccoatika.contactmanager.domain.model.SimContact.asEditable() = EditableContact(
            displayName = name,
            phones = listOf(number to null).filter { it.first.isNotBlank() },
            emails = emptyList(),
            organization = null,
            note = null,
        )

        private fun ContactOpResult.notifyOnSuccess(): ContactOpResult {
            if (this is ContactOpResult.Success) simRepository.notifySimChanged()
            return this
        }
    }
