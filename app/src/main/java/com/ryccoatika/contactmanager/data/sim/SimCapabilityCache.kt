package com.ryccoatika.contactmanager.data.sim

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Cached SIM probe results, keyed per subscription id (-1 = single-SIM/legacy path). */
interface SimCapabilityCache {
    suspend fun get(subId: Int?): SimCapabilities?

    suspend fun set(subId: Int?, caps: SimCapabilities)
}

private val Context.simCapsDataStore by preferencesDataStore(name = "sim_caps")

@Singleton
class DataStoreSimCapabilityCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SimCapabilityCache {
        override suspend fun get(subId: Int?): SimCapabilities? {
            val prefs = context.simCapsDataStore.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()
            val canRead = prefs[canReadKey(subId)] ?: return null
            val canWrite = prefs[canWriteKey(subId)] ?: return null
            return SimCapabilities(
                canRead = canRead,
                canWrite = canWrite,
                maxNameLength = prefs[maxNameKey(subId)] ?: SimCapabilities(canRead, canWrite).maxNameLength,
            )
        }

        override suspend fun set(subId: Int?, caps: SimCapabilities) {
            try {
                context.simCapsDataStore.edit { prefs ->
                    prefs[canReadKey(subId)] = caps.canRead
                    prefs[canWriteKey(subId)] = caps.canWrite
                    prefs[maxNameKey(subId)] = caps.maxNameLength
                }
            } catch (_: IOException) {
                // Losing the cache only costs a re-probe on the next lookup.
            }
        }

        private fun keySuffix(subId: Int?): Int = subId ?: -1

        private fun canReadKey(subId: Int?) = booleanPreferencesKey("sim_canread_${keySuffix(subId)}")

        private fun canWriteKey(subId: Int?) = booleanPreferencesKey("sim_canwrite_${keySuffix(subId)}")

        private fun maxNameKey(subId: Int?) = intPreferencesKey("sim_maxname_${keySuffix(subId)}")
    }
