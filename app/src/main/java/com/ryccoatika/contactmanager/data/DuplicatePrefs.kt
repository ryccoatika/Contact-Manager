package com.ryccoatika.contactmanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * "Not a duplicate" dismissals, persisted app-side: [com.ryccoatika.contactmanager.domain.DuplicateFinder]
 * matches on our own group keys, independent of the provider's aggregation
 * state, so a KEEP_SEPARATE alone would not stop a group from reappearing.
 */
interface DuplicatePrefs {
    fun observeDismissedKeys(): Flow<Set<String>>
    suspend fun dismissedKeys(): Set<String>
    suspend fun dismiss(key: String)
}

private val Context.duplicatePrefsDataStore by preferencesDataStore(name = "duplicate_prefs")

@Singleton
class DataStoreDuplicatePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : DuplicatePrefs {

    override fun observeDismissedKeys(): Flow<Set<String>> =
        context.duplicatePrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[DISMISSED_KEYS] ?: emptySet() }

    override suspend fun dismissedKeys(): Set<String> = observeDismissedKeys().first()

    override suspend fun dismiss(key: String) {
        try {
            context.duplicatePrefsDataStore.edit { prefs ->
                prefs[DISMISSED_KEYS] = (prefs[DISMISSED_KEYS] ?: emptySet()) + key
            }
        } catch (_: IOException) {
            // Best-effort: a lost dismissal only makes the group reappear.
        }
    }

    private companion object {
        val DISMISSED_KEYS = stringSetPreferencesKey("dismissed_group_keys")
    }
}
