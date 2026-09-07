package com.ryccoatika.contactmanager.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Emits once on subscription, then on every provider change under [uri].
 * Registering throws SecurityException if READ_CONTACTS was revoked (user
 * toggle, Android auto-reset) while the app runs; degrade to the one-shot
 * emission instead of crashing — downstream queries already return empty.
 */
internal fun contentChangesFlow(context: Context, uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    val registered = runCatching {
        context.contentResolver.registerContentObserver(uri, true, observer)
    }.isSuccess
    trySend(Unit)
    awaitClose {
        if (registered) context.contentResolver.unregisterContentObserver(observer)
    }
}.conflate()
