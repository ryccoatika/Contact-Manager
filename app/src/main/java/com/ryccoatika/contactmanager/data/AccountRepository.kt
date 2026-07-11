package com.ryccoatika.contactmanager.data

import android.content.Context
import android.provider.ContactsContract.RawContacts
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface AccountsSource {
    suspend fun getAccounts(): List<ContactAccount>
}

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountsSource {

    /**
     * Accounts derived from RawContacts rows (catches WhatsApp-style accounts that
     * AccountManager restricts), with per-account contact counts.
     */
    override suspend fun getAccounts(): List<ContactAccount> = withContext(ioDispatcher) {
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
        counts.map { (key, count) ->
            ContactAccount(
                name = key.second,
                type = key.first,
                capability = AccountClassifier.classify(key.first),
                contactCount = count,
            )
        }.sortedByDescending { it.contactCount }
    }
}
