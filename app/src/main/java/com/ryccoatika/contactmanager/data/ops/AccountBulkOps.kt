package com.ryccoatika.contactmanager.data.ops

import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.BatchRunner
import com.ryccoatika.contactmanager.data.ContactsSource
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.domain.MovePlanner
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.AnalyticsEvent
import com.ryccoatika.contactmanager.domain.model.AccountOpMode
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.model.PendingAccountOp
import com.ryccoatika.contactmanager.domain.model.RawContact
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Move/copy every contact of account A into account B" — shared by the
 * Accounts screen and Home's account-chip long-press so each flow exists once.
 */
interface AccountBulkOps {
    /** Plans the operation (with the SIM field-loss report) for user confirmation. */
    suspend fun plan(mode: AccountOpMode, source: ContactAccount, target: ContactAccount): PendingAccountOp

    /** Starts the background batch; returns the user-facing status message. */
    suspend fun execute(mode: AccountOpMode, source: ContactAccount, target: ContactAccount): String
}

@Singleton
class DefaultAccountBulkOps
    @Inject
    constructor(
        private val contactsSource: ContactsSource,
        private val batchRunner: BatchRunner,
        private val analytics: Analytics,
        private val strings: StringProvider,
    ) : AccountBulkOps {
        override suspend fun plan(
            mode: AccountOpMode,
            source: ContactAccount,
            target: ContactAccount,
        ): PendingAccountOp {
            val plan = MovePlanner.plan(rawContactsOf(source), target.type, target.name)
            return PendingAccountOp(mode, source, target, plan.losses)
        }

        override suspend fun execute(
            mode: AccountOpMode,
            source: ContactAccount,
            target: ContactAccount,
        ): String {
            val rawIds = rawContactsOf(source).map { it.rawContactId }
            if (rawIds.isEmpty()) {
                return strings.get(
                    when (mode) {
                        AccountOpMode.MOVE -> R.string.accounts_msg_no_contacts
                        AccountOpMode.COPY -> R.string.accounts_msg_no_contacts_copy
                    },
                )
            }
            val targetName = target.name ?: strings.get(R.string.accounts_msg_this_device)
            val started = when (mode) {
                AccountOpMode.MOVE -> batchRunner.moveContacts(
                    rawContactIds = rawIds,
                    targetType = target.type,
                    targetName = target.name,
                    label = strings.get(R.string.accounts_msg_moving_label, rawIds.size, targetName),
                    finishedMessage = strings.getQuantity(R.plurals.home_moved_contacts, rawIds.size, rawIds.size),
                )

                AccountOpMode.COPY -> batchRunner.copyContacts(
                    rawContactIds = rawIds,
                    targetType = target.type,
                    targetName = target.name,
                    label = strings.get(R.string.accounts_msg_copying_label, rawIds.size, targetName),
                    finishedMessage = strings.getQuantity(R.plurals.home_copied_contacts, rawIds.size, rawIds.size),
                )
            }
            if (started) {
                analytics.logEvent(
                    when (mode) {
                        AccountOpMode.MOVE -> AnalyticsEvent.AccountMoveAll(rawIds.size)
                        AccountOpMode.COPY -> AnalyticsEvent.AccountCopyAll(rawIds.size)
                    },
                )
            }
            return strings.get(
                when {
                    !started -> R.string.accounts_msg_busy
                    mode == AccountOpMode.MOVE -> R.string.accounts_msg_move_started
                    else -> R.string.accounts_msg_copy_started
                },
            )
        }

        private suspend fun rawContactsOf(account: ContactAccount): List<RawContact> =
            contactsSource
                .snapshot()
                .flatMap { it.rawContacts }
                .filter { it.accountType == account.type && it.accountName == account.name }
    }
