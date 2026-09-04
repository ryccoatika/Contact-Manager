package com.ryccoatika.contactmanager.data.sim

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.ContactOpResult
import com.ryccoatika.contactmanager.data.StringProvider
import com.ryccoatika.contactmanager.di.IoDispatcher
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import com.ryccoatika.contactmanager.domain.model.SimContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface SimContactSource {
    suspend fun probe(subscriptionId: Int?): SimCapabilities

    suspend fun readAll(subscriptionId: Int?): List<SimContact>

    suspend fun insert(subscriptionId: Int?, name: String, number: String): ContactOpResult

    suspend fun update(subscriptionId: Int?, original: SimContact, name: String, number: String): ContactOpResult

    suspend fun delete(subscriptionId: Int?, contact: SimContact): ContactOpResult
}

/**
 * SIM access through the undocumented-but-stable icc/adn content provider.
 * Every call is fully wrapped: OEM providers variously throw SecurityException,
 * IllegalArgumentException, UnsupportedOperationException and even NPE — none
 * of them may ever reach the UI, so failures degrade to Failure/empty results.
 */
@Singleton
class IccSimSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val strings: StringProvider,
    ) : SimContactSource {
        override suspend fun probe(subscriptionId: Int?): SimCapabilities = withContext(ioDispatcher) {
            val canRead = guard(false) {
                context.contentResolver
                    .query(uri(subscriptionId), null, null, null, null)
                    ?.use { true } ?: false
            }
            if (!canRead) return@withContext SimCapabilities(canRead = false, canWrite = false)
            // Reversible test-write: insert a marker entry, then remove it again.
            val canWrite = guard(false) {
                val inserted = context.contentResolver.insert(
                    uri(subscriptionId),
                    ContentValues().apply {
                        put(COLUMN_TAG, PROBE_NAME)
                        put(COLUMN_NUMBER, PROBE_NUMBER)
                    },
                ) != null
                if (!inserted) {
                    false
                } else {
                    context.contentResolver.delete(
                        uri(subscriptionId),
                        deleteWhere(PROBE_NAME, PROBE_NUMBER),
                        null,
                    ) > 0
                }
            }
            SimCapabilities(canRead = true, canWrite = canWrite)
        }

        override suspend fun readAll(subscriptionId: Int?): List<SimContact> = withContext(ioDispatcher) {
            guard(emptyList()) {
                val result = ArrayList<SimContact>()
                context.contentResolver.query(uri(subscriptionId), null, null, null, null)?.use { c ->
                    val iName = c.getColumnIndex(COLUMN_NAME)
                    val iNumber = c.getColumnIndex(COLUMN_NUMBER)
                    // Some providers expose "_id", some "index", some neither.
                    val iIndex = c.getColumnIndex("_id").takeIf { it >= 0 } ?: c.getColumnIndex("index")
                    while (c.moveToNext()) {
                        val name = if (iName >= 0) c.getString(iName).orEmpty() else ""
                        val number = if (iNumber >= 0) c.getString(iNumber).orEmpty() else ""
                        if (name.isBlank() && number.isBlank()) continue
                        // Hide our own write-probe marker if a prior cleanup delete failed.
                        if (name == PROBE_NAME && number == PROBE_NUMBER) continue
                        result += SimContact(
                            indexOnIcc = if (iIndex >= 0) c.getString(iIndex)?.toIntOrNull() else null,
                            name = name,
                            number = number,
                            subscriptionId = subscriptionId,
                        )
                    }
                }
                result
            }
        }

        override suspend fun insert(
            subscriptionId: Int?,
            name: String,
            number: String,
        ): ContactOpResult = withContext(ioDispatcher) {
            writeOp(R.string.icc_action_add) {
                context.contentResolver.insert(
                    uri(subscriptionId),
                    ContentValues().apply {
                        put(COLUMN_TAG, name)
                        put(COLUMN_NUMBER, number)
                    },
                ) != null
            }
        }

        override suspend fun update(
            subscriptionId: Int?,
            original: SimContact,
            name: String,
            number: String,
        ): ContactOpResult = withContext(ioDispatcher) {
            writeOp(R.string.icc_action_update) {
                context.contentResolver.update(
                    uri(subscriptionId),
                    ContentValues().apply {
                        put(COLUMN_TAG, original.name)
                        put(COLUMN_NUMBER, original.number)
                        put(COLUMN_NEW_TAG, name)
                        put(COLUMN_NEW_NUMBER, number)
                    },
                    null,
                    null,
                ) > 0
            }
        }

        override suspend fun delete(
            subscriptionId: Int?,
            contact: SimContact,
        ): ContactOpResult = withContext(ioDispatcher) {
            writeOp(R.string.icc_action_delete) {
                context.contentResolver.delete(
                    uri(subscriptionId),
                    deleteWhere(contact.name, contact.number),
                    null,
                ) > 0
            }
        }

        private fun uri(subscriptionId: Int?): Uri =
            if (subscriptionId == null) {
                Uri.parse("content://icc/adn")
            } else {
                Uri.parse("content://icc/adn/subId/$subscriptionId")
            }

        /**
         * The icc provider only accepts a literal where string of this shape
         * (no selectionArgs support); single quotes in the name are escaped.
         */
        private fun deleteWhere(name: String, number: String): String =
            "tag='${name.replace("'", "''")}' AND number='${number.replace("'", "''")}'"

        private inline fun writeOp(
            @StringRes action: Int,
            block: () -> Boolean,
        ): ContactOpResult = try {
            if (block()) {
                ContactOpResult.Success
            } else {
                ContactOpResult.Failure(strings.get(R.string.icc_error_rejected, strings.get(action)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // SecurityException, IllegalArgumentException, UnsupportedOperationException,
            // NullPointerException (some OEM providers), SQLite errors, ...
            ContactOpResult.Failure(strings.get(R.string.icc_error_no_access, strings.get(action)), e)
        }

        private inline fun <T> guard(fallback: T, block: () -> T): T = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }

        private companion object {
            const val COLUMN_NAME = "name"
            const val COLUMN_TAG = "tag"
            const val COLUMN_NUMBER = "number"
            const val COLUMN_NEW_TAG = "newTag"
            const val COLUMN_NEW_NUMBER = "newNumber"

            /** Marker entry for the reversible write probe. */
            const val PROBE_NAME = "zzcmprobe"
            const val PROBE_NUMBER = "000"
        }
    }
