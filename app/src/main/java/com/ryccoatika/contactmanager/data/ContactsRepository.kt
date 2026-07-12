package com.ryccoatika.contactmanager.data

import android.content.Context
import android.database.ContentObserver
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import android.util.Log
import com.ryccoatika.contactmanager.data.sim.SimAccountsIntegration
import com.ryccoatika.contactmanager.data.sim.SimRepository
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

interface ContactsSource {
    fun observeContacts(): Flow<List<Contact>>
}

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val simIntegration: SimAccountsIntegration,
    private val simRepository: SimRepository,
) : ContactsSource {

    /**
     * Emits the full contact list on subscription and again on every provider
     * change (debounced) or SIM write. SIM storage has no ContentObserver, so
     * icc entries are re-read on each tick and merged below the aggregator.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun observeContacts(): Flow<List<Contact>> =
        merge(contentChanges().debounce(300), simRepository.changes)
            .mapLatest {
                val contacts = queryAllContacts()
                val simContacts = try {
                    simIntegration.simContacts(contacts)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "SIM contact read failed; skipping SIM entries", e)
                    emptyList()
                }
                if (simContacts.isEmpty()) {
                    contacts
                } else {
                    (contacts + simContacts).sortedBy { it.displayName.lowercase() }
                }
            }
            .flowOn(ioDispatcher)

    private fun contentChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true, observer,
        )
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    /**
     * Never throws: this feeds the long-lived observer flow, so a revoked
     * permission or a provider hiccup must degrade to an empty list instead
     * of crashing every collector.
     */
    private suspend fun queryAllContacts(): List<Contact> = withContext(ioDispatcher) {
        try {
            queryAllContactsOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Contact query failed; emitting empty list", e)
            emptyList()
        }
    }

    private fun queryAllContactsOrThrow(): List<Contact> {
        val projection = arrayOf(
            Data._ID,
            Data.RAW_CONTACT_ID,
            Data.CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            Data.DATA2,
            Data.DATA3,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED,
        )
        val rows = ArrayList<DataRow>(1024)
        context.contentResolver.query(
            Data.CONTENT_URI, projection, null, null, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Data._ID)
            val iRaw = c.getColumnIndexOrThrow(Data.RAW_CONTACT_ID)
            val iContact = c.getColumnIndexOrThrow(Data.CONTACT_ID)
            val iMime = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val iData1 = c.getColumnIndexOrThrow(Data.DATA1)
            val iData2 = c.getColumnIndexOrThrow(Data.DATA2)
            val iData3 = c.getColumnIndexOrThrow(Data.DATA3)
            val iAccType = c.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val iAccName = c.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
            val iName = c.getColumnIndexOrThrow(Data.DISPLAY_NAME_PRIMARY)
            val iPhoto = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val iStar = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            while (c.moveToNext()) {
                val mimeType = c.getString(iMime)
                val data2 = c.getString(iData2)
                val data3 = c.getString(iData3)
                rows += DataRow(
                    dataId = c.getLong(iId),
                    rawContactId = c.getLong(iRaw),
                    contactId = c.getLong(iContact),
                    mimeType = mimeType,
                    data1 = c.getString(iData1),
                    data2 = data2,
                    data3 = data3,
                    typeLabel = PhoneEmailTypeLabel.from(mimeType, data2?.toIntOrNull(), data3),
                    accountType = c.getString(iAccType),
                    accountName = c.getString(iAccName),
                    displayName = c.getString(iName),
                    photoThumbUri = c.getString(iPhoto),
                    starred = c.getInt(iStar) == 1,
                )
            }
        }
        return ContactAggregator.aggregate(rows)
    }

    private companion object {
        const val TAG = "ContactsRepo"
    }
}
