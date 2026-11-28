package com.ryccoatika.contactmanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryccoatika.contactmanager.domain.DuplicateAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Not a duplicate" dismissals, persisted app-side: [com.ryccoatika.contactmanager.domain.DuplicateFinder]
 * matches on our own group keys, independent of the provider's aggregation
 * state, so a KEEP_SEPARATE alone would not stop a group from reappearing.
 */
interface DuplicatePrefs {
    fun observeDismissedKeys(): Flow<Set<String>>

    suspend fun dismissedKeys(): Set<String>

    suspend fun dismiss(key: String)

    /** Undoes [dismiss]: the group may show up again. */
    suspend fun restore(key: String)

    /** Whether the first-use explanation for [action] was already confirmed. */
    suspend fun isExplained(action: DuplicateAction): Boolean

    suspend fun markExplained(action: DuplicateAction)
}

private val Context.duplicatePrefsDataStore by preferencesDataStore(name = "duplicate_prefs")

@Singleton
class DataStoreDuplicatePrefs
    @Inject
    constructor(
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

        override suspend fun restore(key: String) {
            try {
                context.duplicatePrefsDataStore.edit { prefs ->
                    prefs[DISMISSED_KEYS] = (prefs[DISMISSED_KEYS] ?: emptySet()) - key
                }
            } catch (_: IOException) {
                // Best-effort: a lost restore leaves the group hidden.
            }
        }

        override suspend fun isExplained(action: DuplicateAction): Boolean =
            context.duplicatePrefsDataStore.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { it[explainedKey(action)] ?: false }
                .first()

        override suspend fun markExplained(action: DuplicateAction) {
            try {
                context.duplicatePrefsDataStore.edit { prefs -> prefs[explainedKey(action)] = true }
            } catch (_: IOException) {
                // Best-effort: a lost flag only shows the explanation once more.
            }
        }

        private fun explainedKey(action: DuplicateAction) = when (action) {
            DuplicateAction.LINK -> EXPLAINED_LINK
            DuplicateAction.DISMISS -> EXPLAINED_DISMISS
        }

        private companion object {
            val EXPLAINED_LINK = booleanPreferencesKey("explained_link")
            val EXPLAINED_DISMISS = booleanPreferencesKey("explained_dismiss")
            val DISMISSED_KEYS = stringSetPreferencesKey("dismissed_group_keys")
        }
    }
