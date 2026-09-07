package com.ryccoatika.contactmanager.data

import android.content.Context
import android.os.Build
import android.provider.ContactsContract.RawContacts
import android.util.Log
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface AccountsSource {
    suspend fun getAccounts(): List<ContactAccount>

    /** Emits on subscription and again on every contact-provider change, so
     *  per-account counts stay live as contacts are added/deleted/moved/merged. */
    fun observeAccounts(): Flow<List<ContactAccount>>
}

@Singleton
class AccountRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val simIntegration: SimAccountsIntegration,
    ) : AccountsSource {
        /**
         * Accounts derived from RawContacts rows (catches WhatsApp-style accounts that
         * AccountManager restricts), with per-account contact counts, plus icc/adn SIM
         * pseudo-accounts on devices whose provider does not surface SIM contacts itself.
         */
        override suspend fun getAccounts(): List<ContactAccount> = withContext(ioDispatcher) {
            try {
                getAccountsOrThrow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never throws: a revoked permission or provider hiccup degrades to
                // an empty account list instead of crashing the caller.
                Log.w(TAG, "Account query failed; returning empty list", e)
                emptyList()
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        override fun observeAccounts(): Flow<List<ContactAccount>> =
            contentChanges()
                .debounce(300)
                .mapLatest { getAccounts() }
                .flowOn(ioDispatcher)

        private fun contentChanges(): Flow<Unit> = contentChangesFlow(context, RawContacts.CONTENT_URI)

        private suspend fun getAccountsOrThrow(): List<ContactAccount> {
            val counts = HashMap<Pair<String?, String?>, Int>()
            context.contentResolver
                .query(
                    RawContacts.CONTENT_URI,
                    arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                    "${RawContacts.DELETED}=0",
                    null,
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val key = c.getString(0) to c.getString(1)
                        counts[key] = (counts[key] ?: 0) + 1
                    }
                }
            // Keep the on-device local account (e.g. Samsung's "Phone") always
            // selectable so a contact can be created or moved into local storage even
            // when it currently holds none. If a local account already exists (any
            // name), leave it untouched; otherwise seed it with a zero count.
            val localType = deviceLocalAccountType()
            if (counts.keys.none { it.first == localType }) {
                counts[localType to localType] = 0
            }
            // Slot -> subscription, so native SIM accounts (e.g. Samsung's opaque
            // "primary.sim2.account_name") can be labeled "SIM 2 · carrier" + number.
            val slotSubs = simIntegration.subscriptionsBySlot()
            val providerAccounts = counts
                .map { (key, count) ->
                    val capability = AccountClassifier.classify(key.first)
                    val sub = if (capability == AccountCapability.SIM) {
                        SimRouting.nativeSimSlot(key.first)?.let { slotSubs[it] }
                    } else {
                        null
                    }
                    ContactAccount(
                        name = key.second,
                        type = key.first,
                        capability = capability,
                        contactCount = count,
                        writable = capability != AccountCapability.READ_ONLY,
                        displayLabel = sub?.label,
                        phoneNumber = sub?.number,
                    )
                }.sortedByDescending { it.contactCount }
            return providerAccounts + simIntegration.simPseudoAccounts(providerAccounts)
        }

        /**
         * The account type the local contacts provider uses for on-device storage.
         * OEMs override the AOSP null-local with their own writable local type
         * (Samsung "vnd.sec.contact.phone", etc.); everything else falls back to the
         * AOSP null local, which the provider remaps to its device store on write.
         */
        private fun deviceLocalAccountType(): String? = when (Build.MANUFACTURER.lowercase()) {
            "samsung" -> "vnd.sec.contact.phone"
            "huawei" -> "com.android.huawei.phone"
            "oppo", "realme" -> "com.oppo.contacts.device"
            "oneplus" -> "vnd.oneplus.contact.phone"
            else -> null
        }

        private companion object {
            const val TAG = "AccountsRepo"
        }
    }
