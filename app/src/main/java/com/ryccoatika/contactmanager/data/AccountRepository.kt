package com.ryccoatika.contactmanager.data

import android.content.Context
import android.provider.ContactsContract.RawContacts
import android.util.Log
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.SimRouting
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface AccountsSource {
    suspend fun getAccounts(): List<ContactAccount>
}

@Singleton
class AccountRepository @Inject constructor(
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

    private suspend fun getAccountsOrThrow(): List<ContactAccount> {
        val counts = HashMap<Pair<String?, String?>, Int>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) to c.getString(1)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        // Slot -> subscription, so native SIM accounts (e.g. Samsung's opaque
        // "primary.sim2.account_name") can be labeled "SIM 2 · carrier" + number.
        val slotSubs = simIntegration.subscriptionsBySlot()
        val providerAccounts = counts.map { (key, count) ->
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

    private companion object {
        const val TAG = "AccountsRepo"
    }
}
