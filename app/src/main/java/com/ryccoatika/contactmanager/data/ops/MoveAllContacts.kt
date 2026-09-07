package com.ryccoatika.contactmanager.data.ops

import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.domain.MovePlanner
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingMoveAll
import com.ryccoatika.contactmanager.domain.model.RawContact
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Move every contact of account A into account B" — shared by the Accounts
 * screen and Home's account-chip long-press so the flow exists exactly once.
 */
interface MoveAllContacts {
    /** Plans the move (with the SIM field-loss report) for user confirmation. */
    suspend fun plan(source: ContactAccount, target: ContactAccount): PendingMoveAll

    /** Starts the background batch; returns the user-facing status message. */
    suspend fun execute(source: ContactAccount, target: ContactAccount): String
}

@Singleton
class DefaultMoveAllContacts
    @Inject
    constructor(
        private val contactsSource: ContactsSource,
        private val batchRunner: BatchRunner,
        private val analytics: Analytics,
        private val strings: StringProvider,
    ) : MoveAllContacts {
        override suspend fun plan(source: ContactAccount, target: ContactAccount): PendingMoveAll {
            val plan = MovePlanner.plan(rawContactsOf(source), target.type, target.name)
            return PendingMoveAll(source, target, plan.losses)
        }

        override suspend fun execute(source: ContactAccount, target: ContactAccount): String {
            val rawIds = rawContactsOf(source).map { it.rawContactId }
            if (rawIds.isEmpty()) return strings.get(R.string.accounts_msg_no_contacts)
            val targetName = target.name ?: strings.get(R.string.accounts_msg_this_device)
            val started = batchRunner.moveContacts(
                rawContactIds = rawIds,
                targetType = target.type,
                targetName = target.name,
                label = strings.get(R.string.accounts_msg_moving_label, rawIds.size, targetName),
            )
            if (started) analytics.logEvent(AnalyticsEvent.AccountMoveAll(rawIds.size))
            return strings.get(
                if (started) R.string.accounts_msg_move_started else R.string.accounts_msg_busy,
            )
        }

        private suspend fun rawContactsOf(account: ContactAccount): List<RawContact> =
            contactsSource
                .observeContacts()
                .first()
                .flatMap { it.rawContacts }
                .filter { it.accountType == account.type && it.accountName == account.name }
    }
