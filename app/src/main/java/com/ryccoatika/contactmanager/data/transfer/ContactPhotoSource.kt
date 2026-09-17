package com.ryccoatika.contactmanager.data.transfer

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.Data
import com.ryccoatika.contactmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface ContactPhotoSource {
    suspend fun photoOf(rawContactId: Long): ByteArray?
}

@Singleton
class ProviderContactPhotoSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ContactPhotoSource {
        override suspend fun photoOf(rawContactId: Long): ByteArray? = withContext(ioDispatcher) {
            runCatching {
                context.contentResolver
                    .query(
                        Data.CONTENT_URI,
                        arrayOf(Photo.PHOTO),
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                        arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE),
                        null,
                    )?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
            }.getOrNull()
        }
    }
